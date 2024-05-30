package com.pengxh.ncnn.yolov8

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.widget.AdapterView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.pengxh.kt.lite.base.KotlinBaseActivity
import com.pengxh.ncnn.yolov8.databinding.ActivityMainBinding

class MainActivity : KotlinBaseActivity<ActivityMainBinding>(), SurfaceHolder.Callback {

    private val kTag = "MainActivity"
    private val yolov8ncnn by lazy { Yolov8ncnn() }
    private var facing = 0
    private var currentModel = 0
    private var currentProcessor = 0

    override fun initEvent() {
        binding.switchCameraButton.setOnClickListener {
            val new = 1 - facing
            yolov8ncnn.closeCamera()
            yolov8ncnn.openCamera(new)
            facing = new
        }

        binding.modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                arg0: AdapterView<*>?, arg1: View, position: Int, id: Long
            ) {
                if (position != currentModel) {
                    currentModel = position
                    reloadModel()
                }
            }

            override fun onNothingSelected(arg0: AdapterView<*>?) {}
        }

        binding.processorSpinner.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    arg0: AdapterView<*>?, arg1: View, position: Int, id: Long
                ) {
                    if (position != currentProcessor) {
                        currentProcessor = position
                        reloadModel()
                    }
                }

                override fun onNothingSelected(arg0: AdapterView<*>?) {}
            }
    }

    private fun reloadModel() {
        val result = yolov8ncnn.loadModel(assets, currentModel, currentProcessor)
        if (!result) {
            Log.d(kTag, "reload: yolov8ncnn loadModel failed")
        }
    }

    override fun initOnCreate(savedInstanceState: Bundle?) {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        binding.surfaceView.holder.setFormat(PixelFormat.RGBA_8888)
        binding.surfaceView.holder.addCallback(this)

        reloadModel()
    }

    override fun initViewBinding(): ActivityMainBinding {
        return ActivityMainBinding.inflate(layoutInflater)
    }

    override fun observeRequestState() {

    }

    override fun setupTopBarLayout() {

    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        yolov8ncnn.setOutputWindow(holder.surface)
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