package com.pengxh.ncnn.yolov8

@Retention(AnnotationRetention.SOURCE)
annotation class YoloStateConst {
    companion object {
        /**
         * Yolo当前状态
         * <br>------------------------------<br>
         * 0 - 分类
         * 1 - 分割
         * 2 - 检测
         * 3 - 绘制
         * */
        const val CLASSIFY = 0
        const val SEGMENTATION = 1
        const val DETECT = 2
        const val DRAW = 3
    }
}