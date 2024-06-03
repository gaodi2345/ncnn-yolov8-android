package com.pengxh.ncnn.yolov8;

import android.content.res.AssetManager;
import android.view.Surface;

public class Yolov8ncnn {
    public native boolean loadModel(AssetManager mgr, int model_id, int processor);

    public native boolean openCamera(int facing);

    public native boolean closeCamera();

    public native boolean setOutputWindow(Surface surface, DetectResult input, long nativeObjAddr, INativeCallback nativeCallback);

    static {
        System.loadLibrary("yolov8ncnn");
    }
}
