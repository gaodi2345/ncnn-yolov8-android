package com.pengxh.ncnn.yolov8

interface INativeCallback {
    fun onDetect(str: String)
}