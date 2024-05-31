// Tencent is pleased to support the open source community by making ncnn available.
//
// Copyright (C) 2021 THL A29 Limited, a Tencent company. All rights reserved.
//
// Licensed under the BSD 3-Clause License (the "License"); you may not use this file except
// in compliance with the License. You may obtain a copy of the License at
//
// https://opensource.org/licenses/BSD-3-Clause
//
// Unless required by applicable law or agreed to in writing, software distributed
// under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
// CONDITIONS OF ANY KIND, either express or implied. See the License for the
// specific language governing permissions and limitations under the License.

#include "yolo.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#include "cpu.h"

static float fast_exp(float x) {
    union {
        uint32_t i;
        float f;
    } v{};
    v.i = (1 << 23) * (1.4426950409 * x + 126.93490512f);
    return v.f;
}

static float sigmoid(float x) {
    return 1.0f / (1.0f + fast_exp(-x));
}

static float intersection_area(const Object &a, const Object &b) {
    cv::Rect_<float> inter = a.rect & b.rect;
    return inter.area();
}

static void qsort_descent_inplace(std::vector<Object> &face_objects, int left, int right) {
    int i = left;
    int j = right;
    float p = face_objects[(left + right) / 2].prob;

    while (i <= j) {
        while (face_objects[i].prob > p)
            i++;

        while (face_objects[j].prob < p)
            j--;

        if (i <= j) {
            // swap
            std::swap(face_objects[i], face_objects[j]);

            i++;
            j--;
        }
    }

    //     #pragma omp parallel sections
    {
        //         #pragma omp section
        {
            if (left < j) qsort_descent_inplace(face_objects, left, j);
        }
        //         #pragma omp section
        {
            if (i < right) qsort_descent_inplace(face_objects, i, right);
        }
    }
}

static void qsort_descent_inplace(std::vector<Object> &face_objects) {
    if (face_objects.empty())
        return;

    qsort_descent_inplace(face_objects, 0, face_objects.size() - 1);
}

static void nms_sorted_bboxes(const std::vector<Object> &face_objects, std::vector<int> &picked,
                              float nms_threshold) {
    picked.clear();

    const int n = face_objects.size();

    std::vector<float> areas(n);
    for (int i = 0; i < n; i++) {
        areas[i] = face_objects[i].rect.width * face_objects[i].rect.height;
    }

    for (int i = 0; i < n; i++) {
        const Object &a = face_objects[i];

        int keep = 1;
        for (int j = 0; j < (int) picked.size(); j++) {
            const Object &b = face_objects[picked[j]];

            // intersection over union
            float inter_area = intersection_area(a, b);
            float union_area = areas[i] + areas[picked[j]] - inter_area;
            // float IoU = inter_area / union_area
            if (inter_area / union_area > nms_threshold)
                keep = 0;
        }

        if (keep)
            picked.push_back(i);
    }
}

static void
generate_grids_and_stride(const int target_w, const int target_h, std::vector<int> &strides,
                          std::vector<GridAndStride> &grid_strides) {
    for (int stride: strides) {
        int num_grid_w = target_w / stride;
        int num_grid_h = target_h / stride;
        for (int g1 = 0; g1 < num_grid_h; g1++) {
            for (int g0 = 0; g0 < num_grid_w; g0++) {
                GridAndStride gs;
                gs.grid0 = g0;
                gs.grid1 = g1;
                gs.stride = stride;
                grid_strides.push_back(gs);
            }
        }
    }
}

static void generate_proposals(std::vector<GridAndStride> grid_strides, const ncnn::Mat &pred,
                               float prob_threshold, std::vector<Object> &objects) {
    const int num_points = grid_strides.size();
    //识别种类数
    const int num_class = 43;
    const int reg_max_1 = 16;

    for (int i = 0; i < num_points; i++) {
        const float *scores = pred.row(i) + 4 * reg_max_1;

        // find label with max score
        int label = -1;
        float score = -FLT_MAX;
        for (int k = 0; k < num_class; k++) {
            float confidence = scores[k];
            if (confidence > score) {
                label = k;
                score = confidence;
            }
        }
        float box_prob = sigmoid(score);
        if (box_prob >= prob_threshold) {
            ncnn::Mat bbox_pred(reg_max_1, 4, (void *) pred.row(i));
            {
                ncnn::Layer *softmax = ncnn::create_layer("Softmax");

                ncnn::ParamDict pd;
                pd.set(0, 1); // axis
                pd.set(1, 1);
                softmax->load_param(pd);

                ncnn::Option opt;
                opt.num_threads = 1;
                opt.use_packing_layout = false;

                softmax->create_pipeline(opt);

                softmax->forward_inplace(bbox_pred, opt);

                softmax->destroy_pipeline(opt);

                delete softmax;
            }

            float pred_ltrb[4];
            for (int k = 0; k < 4; k++) {
                float dis = 0.f;
                const float *dis_after_sm = bbox_pred.row(k);
                for (int l = 0; l < reg_max_1; l++) {
                    dis += l * dis_after_sm[l];
                }

                pred_ltrb[k] = dis * grid_strides[i].stride;
            }

            float pb_cx = (grid_strides[i].grid0 + 0.5f) * grid_strides[i].stride;
            float pb_cy = (grid_strides[i].grid1 + 0.5f) * grid_strides[i].stride;

            float x0 = pb_cx - pred_ltrb[0];
            float y0 = pb_cy - pred_ltrb[1];
            float x1 = pb_cx + pred_ltrb[2];
            float y1 = pb_cy + pred_ltrb[3];

            Object obj;
            obj.rect.x = x0;
            obj.rect.y = y0;
            obj.rect.width = x1 - x0;
            obj.rect.height = y1 - y0;
            obj.label = label;
            obj.prob = box_prob;

            objects.push_back(obj);
        }
    }
}

Yolo::Yolo() {
    blob_pool_allocator.set_size_compare_ratio(0.f);
    workspace_pool_allocator.set_size_compare_ratio(0.f);
}

int
Yolo::load(const char *model_type, int target_size, const float *mean_values,
           const float *norm_values, bool use_gpu) {

}

int
Yolo::load(AAssetManager *mgr, const char *model_type, int _target_size, const float *_mean_values,
           const float *_norm_values, bool use_gpu) {
    yolo.clear();
    blob_pool_allocator.clear();
    workspace_pool_allocator.clear();

    ncnn::set_cpu_powersave(2);
    ncnn::set_omp_num_threads(ncnn::get_big_cpu_count());

    yolo.opt = ncnn::Option();

#if NCNN_VULKAN
    yolo.opt.use_vulkan_compute = use_gpu;
#endif

    yolo.opt.num_threads = ncnn::get_big_cpu_count();
    yolo.opt.blob_allocator = &blob_pool_allocator;
    yolo.opt.workspace_allocator = &workspace_pool_allocator;

    char param_path[256];
    char model_path[256];
    //拼接模型名（路径）
    sprintf(param_path, "yolov8%s.param", model_type);
    sprintf(model_path, "yolov8%s.bin", model_type);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "param_path %s", param_path);
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "model_path %s", model_path);

    yolo.load_param(mgr, param_path);
    yolo.load_model(mgr, model_path);

    target_size = _target_size;
    mean_values[0] = _mean_values[0];
    mean_values[1] = _mean_values[1];
    mean_values[2] = _mean_values[2];
    norm_values[0] = _norm_values[0];
    norm_values[1] = _norm_values[1];
    norm_values[2] = _norm_values[2];

    return 0;
}

int Yolo::detect(const cv::Mat &rgb, std::vector<Object> &objects, float prob_threshold,
                 float nms_threshold) {
    int width = rgb.cols;
    int height = rgb.rows;

    // pad to multiple of 32
    int w = width;
    int h = height;
    float scale = 1.f;
    if (w > h) {
        scale = (float) target_size / w;
        w = target_size;
        h = h * scale;
    } else {
        scale = (float) target_size / h;
        h = target_size;
        w = w * scale;
    }

    ncnn::Mat in = ncnn::Mat::from_pixels_resize(rgb.data, ncnn::Mat::PIXEL_RGB2BGR, width, height,
                                                 w, h);

    // pad to target_size rectangle
    int w_pad = (w + 31) / 32 * 32 - w;
    int h_pad = (h + 31) / 32 * 32 - h;
    ncnn::Mat in_pad;
    ncnn::copy_make_border(in, in_pad, h_pad / 2, h_pad - h_pad / 2, w_pad / 2, w_pad - w_pad / 2,
                           ncnn::BORDER_CONSTANT, 0.f);

    in_pad.substract_mean_normalize(0, norm_values);

    ncnn::Extractor ex = yolo.create_extractor();

    ex.input("images", in_pad);

    std::vector<Object> proposals;

    ncnn::Mat out;
    ex.extract("output", out);

    std::vector<int> strides = {8, 16, 32}; // might have stride=64
    std::vector<GridAndStride> grid_strides;
    generate_grids_and_stride(in_pad.w, in_pad.h, strides, grid_strides);
    generate_proposals(grid_strides, out, prob_threshold, proposals);

    // sort all proposals by score from highest to lowest
    qsort_descent_inplace(proposals);

    // apply nms with nms_threshold
    std::vector<int> picked;
    nms_sorted_bboxes(proposals, picked, nms_threshold);

    int count = picked.size();

    objects.resize(count);
    for (int i = 0; i < count; i++) {
        objects[i] = proposals[picked[i]];

        // adjust offset to original unpadded
        float x0 = (objects[i].rect.x - (w_pad / 2)) / scale;
        float y0 = (objects[i].rect.y - (h_pad / 2)) / scale;
        float x1 = (objects[i].rect.x + objects[i].rect.width - (w_pad / 2)) / scale;
        float y1 = (objects[i].rect.y + objects[i].rect.height - (h_pad / 2)) / scale;

        // clip
        x0 = std::max(std::min(x0, (float) (width - 1)), 0.f);
        y0 = std::max(std::min(y0, (float) (height - 1)), 0.f);
        x1 = std::max(std::min(x1, (float) (width - 1)), 0.f);
        y1 = std::max(std::min(y1, (float) (height - 1)), 0.f);

        objects[i].rect.x = x0;
        objects[i].rect.y = y0;
        objects[i].rect.width = x1 - x0;
        objects[i].rect.height = y1 - y0;
    }

    // sort objects by area
    struct {
        bool operator()(const Object &a, const Object &b) const {
            return a.rect.area() > b.rect.area();
        }
    } objects_area_greater;
    std::sort(objects.begin(), objects.end(), objects_area_greater);
    return 0;
}

void Yolo::setNativeCallback(JavaVM *vm, jobject pJobject) {
    javaVM = vm;

    /**
     * JNIEnv不支持跨线程调用
     * */
    JNIEnv *env;
    vm->AttachCurrentThread(&env, NULL);
    j_obj = env->NewGlobalRef(pJobject);
}

int Yolo::draw(cv::Mat &rgb, const std::vector<Object> &objects) {
    static const char *class_names[] = {
            "三脚架", "三通", "人", "切断阀", "危险告知牌",
            "压力测试仪", "压力表", "反光衣", "呼吸面罩", "喉箍",
            "圆头水枪", "安全告知牌", "安全帽", "安全标识", "安全绳",
            "对讲机", "尖头水枪", "开关", "报警装置", "接头",
            "施工路牌", "气体检测仪", "水带", "水带_矩形", "流量计",
            "消火栓箱", "灭火器", "照明设备", "熄火保护", "电线暴露",
            "电路图", "警戒线", "调压器", "调长器", "贴纸",
            "跨电线", "路锥", "软管", "过滤器", "配电箱",
            "长柄阀门", "阀门", "风管"
    };

    static const unsigned char colors[19][3] = {
            {54,  67,  244},
            {99,  30,  233},
            {176, 39,  156},
            {183, 58,  103},
            {181, 81,  63},
            {243, 150, 33},
            {244, 169, 3},
            {212, 188, 0},
            {136, 150, 0},
            {80,  175, 76},
            {74,  195, 139},
            {57,  220, 205},
            {59,  235, 255},
            {7,   193, 255},
            {0,   152, 255},
            {34,  87,  255},
            {72,  85,  121},
            {158, 158, 158},
            {139, 125, 96}
    };

    int color_index = 0;

    JNIEnv *env;
    javaVM->AttachCurrentThread(&env, NULL);
    jclass clazz = env->GetObjectClass(j_obj);
    jmethodID j_method_id = env->GetMethodID(clazz, "onDetect", "(Ljava/lang/String;)V");

    for (const auto &obj: objects) {
        float reliability = obj.prob * 100;
        if (reliability > 75) {
            const unsigned char *color = colors[color_index % 19];

            color_index++;

            cv::Scalar cc(color[0], color[1], color[2]);

            cv::rectangle(rgb, obj.rect, cc, 2);

            char text[256];
            sprintf(text, "%s %.1f%%", class_names[obj.label], reliability);

            int baseLine = 0;
            cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1,
                                                  &baseLine);

            int x = obj.rect.x;
            int y = obj.rect.y - label_size.height - baseLine;
            if (y < 0)
                y = 0;
            if (x + label_size.width > rgb.cols)
                x = rgb.cols - label_size.width;

            cv::Size size = cv::Size(label_size.width, label_size.height + baseLine);
            cv::Rect rc = cv::Rect(cv::Point(x, y), size);
            cv::rectangle(rgb, rc, cc, -1);

            cv::Scalar text_scalar = (color[0] + color[1] + color[2] >= 381)
                                     ? cv::Scalar(0, 0, 0)
                                     : cv::Scalar(255, 255, 255);


            cv::putText(rgb, text,
                        cv::Point(x, y + label_size.height),
                        cv::FONT_HERSHEY_SIMPLEX,
                        0.5,
                        text_scalar, 1
            );

            //回调给Java/Kotlin层
            jstring jstr = env->NewStringUTF(text);
            const char *msg = env->GetStringUTFChars(jstr, nullptr);
            env->CallVoidMethod(j_obj, j_method_id, jstr);
            env->ReleaseStringUTFChars(jstr, msg);
        } else {
            __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "可信度太低： %.1f%%", reliability);
        }
    }

    return 0;
}
