package com.pengxh.ncnn.yolov8

interface INativeCallback {
    fun onDetect(label: String, rect: FloatArray)
}