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
import com.pengxh.kt.lite.extensions.toJson
import com.pengxh.ncnn.yolov8.databinding.ActivityMainBinding

class MainActivity : KotlinBaseActivity<ActivityMainBinding>(), SurfaceHolder.Callback,
    INativeCallback {

    private val kTag = "MainActivity"
    private val yolov8ncnn by lazy { Yolov8ncnn() }
    private val classNames = arrayListOf(
        "三脚架", "三通", "人", "切断阀", "危险告知牌",
        "压力测试仪", "压力表", "反光衣", "呼吸面罩", "喉箍",
        "圆头水枪", "安全告知牌", "安全帽", "安全标识", "安全绳",
        "对讲机", "尖头水枪", "开关", "报警装置", "接头",
        "施工路牌", "气体检测仪", "水带", "水带_矩形", "流量计",
        "消火栓箱", "灭火器", "照明设备", "熄火保护", "电线暴露",
        "电路图", "警戒线", "调压器", "调长器", "贴纸",
        "跨电线", "路锥", "软管", "过滤器", "配电箱",
        "长柄阀门", "阀门", "风管"
    )
    private var facing = 1
    private var currentModel = 0
    private var currentProcessor = 0

    override fun initEvent() {
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
        yolov8ncnn.setOutputWindow(holder.surface, DetectResult(), this)
    }

    override fun onDetect(output: DetectResult) {
        Log.d(kTag, output.toJson())
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