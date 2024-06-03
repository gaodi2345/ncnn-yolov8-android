package com.pengxh.ncnn.yolov8;

import java.util.ArrayList;

public interface INativeCallback {
    void onDetect(ArrayList<DetectResult> output);
}
