package com.pengxh.ncnn.yolov8;

import java.util.ArrayList;

public interface INativeCallback {
    /**
     * 分类
     */
    void onClassify(float[] possibles);
//    void onClassify(float[] possibles, String result);

    /**
     * 检测
     */
    void onDetect(ArrayList<DetectResult> output);
}
