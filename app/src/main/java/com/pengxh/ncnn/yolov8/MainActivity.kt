package com.pengxh.ncnn.yolov8

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.WindowManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pengxh.kt.lite.base.KotlinBaseActivity
import com.pengxh.kt.lite.extensions.toJson
import com.pengxh.kt.lite.widget.dialog.AlertControlDialog
import com.pengxh.ncnn.yolov8.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import org.opencv.core.Mat


class MainActivity : KotlinBaseActivity<ActivityMainBinding>(), SurfaceHolder.Callback,
    INativeCallback {

    private val kTag = "MainActivity"
    private val yolov8ncnn by lazy { Yolov8ncnn() }
    private val mat by lazy { Mat() }

    /**
     * 需要和训练出来的模型里面类别顺序保持一致
     * */
    private val classArray = arrayOf("电线整洁", "电线杂乱", "餐馆厨房")
    private var facing = 1
    private var isShowing = false

    override fun initEvent() {

    }

    override fun initOnCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        OpenCVLoader.initLocal()

        binding.surfaceView.holder.setFormat(PixelFormat.RGBA_8888)
        binding.surfaceView.holder.addCallback(this)

//        yolov8ncnn.loadModel(assets, 1, false, true, false, false)
        yolov8ncnn.loadMultiModel(assets, intArrayOf(0, 2), false)
    }

    override fun initViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun observeRequestState() {

    }

    override fun setupTopBarLayout() {

    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        yolov8ncnn.setOutputWindow(holder.surface, mat.nativeObjAddr, this)
    }

    override fun onClassify(possibles: FloatArray) {
        //找出最大值的下标
        var max = possibles[0]
        var maxIndex = 0
        possibles.forEachIndexed { index, fl ->
            if (fl > max) {
                max = fl
                maxIndex = index
            }
        }

        try {
            Log.d(kTag, "${possibles.contentToString()} - ${classArray[maxIndex]}")
            if (isShowing) {
                return
            }
            runOnUiThread {
                isShowing = true
                AlertControlDialog.Builder()
                    .setContext(this)
                    .setTitle("提示")
                    .setMessage("识别到目标场景，是否开始排查该场景的隐患？")
                    .setNegativeButton("稍后")
                    .setPositiveButton("好的").setOnDialogButtonClickListener(object :
                        AlertControlDialog.OnDialogButtonClickListener {
                        override fun onConfirmClick() {
                            //更换为检测模型
                            yolov8ncnn.loadModel(
                                assets,
                                2,
                                false,
                                false,
                                false,
                                true
                            )
                        }

                        override fun onCancelClick() {
                            isShowing = false
                        }
                    }).build().show()
            }
        } catch (e: ArrayIndexOutOfBoundsException) {
            e.printStackTrace()
        }
    }

    override fun onSegmentation(
        segmentationOutput: ArrayList<FloatArray>, detectOutput: ArrayList<FloatArray>
    ) {
        //转成泛型集合
        val segmentationResults = ArrayList<YoloResult>()
        segmentationOutput.forEach {
            val yolo = YoloResult()

            val array = FloatArray(4)
            array[0] = it[0]
            array[1] = it[1]
            array[2] = it[2]
            array[3] = it[3]
            yolo.position = array

            yolo.type = it[4].toInt()

            yolo.prob = "${it[5]}%"
            segmentationResults.add(yolo)
        }

        val detectResults = ArrayList<YoloResult>()
        detectOutput.forEach {
            val yolo = YoloResult()

            val array = FloatArray(4)
            array[0] = it[0]
            array[1] = it[1]
            array[2] = it[2]
            array[3] = it[3]
            yolo.position = array

            yolo.type = it[4].toInt()

            yolo.prob = "${it[5]}%"
            detectResults.add(yolo)
        }
        binding.detectView.updateTargetPosition(segmentationResults, detectResults)
    }

    override fun onDetect(output: ArrayList<FloatArray>) {
        //转成泛型集合
        val results = ArrayList<YoloResult>()
        output.forEach {
            /**
             * 前四位是目标Rect，第五位是目标名对应的角标，第六位是可信度
             *
             * [135.88397,120.17752,68.061325,204.02115,28.0,43.642334]
             * */
            val yolo = YoloResult()

            val array = FloatArray(4)
            array[0] = it[0]
            array[1] = it[1]
            array[2] = it[2]
            array[3] = it[3]
            yolo.position = array

            yolo.type = it[4].toInt()

            //保留两位有效小数
            yolo.prob = "${String.format("%.2f", it[5])}%"
            results.add(yolo)
        }
        Log.d(kTag, results.toJson())
        binding.detectView.updateTargetPosition(results)

//        if (mat.width() > 0 || mat.height() > 0) {
//            val bitmap = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888)
//            Utils.matToBitmap(mat, bitmap, true)
//            bitmap.saveImage("${createImageFileDir()}/${System.currentTimeMillis()}.png")
//        } else {
//            Log.d(kTag, "width: ${mat.width()}, height: ${mat.height()}")
//        }
    }

    override fun surfaceCreated(holder: SurfaceHolder) {}

    override fun surfaceDestroyed(holder: SurfaceHolder) {}

    override fun onResume() {
        super.onResume()
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_DENIED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.CAMERA), 100
            )
        }
        yolov8ncnn.openCamera(facing)
    }

    override fun onPause() {
        super.onPause()
        yolov8ncnn.closeCamera()
    }
}