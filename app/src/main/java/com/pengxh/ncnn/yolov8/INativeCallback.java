package com.pengxh.ncnn.yolov8;

public interface INativeCallback {
    void onDetect(DetectResult output);
}
