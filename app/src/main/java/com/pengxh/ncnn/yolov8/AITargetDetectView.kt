package com.pengxh.ncnn.yolov8

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import com.pengxh.kt.lite.extensions.sp2px

class AITargetDetectView constructor(context: Context, attrs: AttributeSet) : View(context, attrs) {

    private val kTag = "DetectView"
    private val textPaint by lazy { TextPaint() }
    private val backgroundPaint by lazy { Paint() }
    private val rectF by lazy { RectF() }
    private var result: DetectResult? = null

    init {
        textPaint.color = Color.WHITE
        textPaint.isAntiAlias = true
        textPaint.textAlign = Paint.Align.CENTER
        textPaint.textSize = 14f.sp2px(context)

        backgroundPaint.color = Color.BLUE
        backgroundPaint.style = Paint.Style.FILL
        backgroundPaint.isAntiAlias = true
    }

    fun updateTargetPosition(result: DetectResult?) {
        this.result = result
        invalidate()
    }

    fun clearTargetPosition() {
        this.result = null
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        result?.apply {
            //画文字
//            canvas.drawText(
//                type,
//                (position[0] + position[2] / 2).dp2px(context),
//                (position[1] + position[3] / 2).dp2px(context),
//                textPaint
//            )

            //画背景
//            val textWidth = textPaint.measureText(typeName)
//            rectF.set(
//                rectPosition[0].dp2px(context),
//                rectPosition[1].dp2px(context),
//                (rectPosition[2] + rectPosition[0]).dp2px(context),
//                (rectPosition[3] + rectPosition[1]).dp2px(context)
//            )
//            canvas.drawOval(rectF, backgroundPaint)
        }
        invalidate()
    }
}