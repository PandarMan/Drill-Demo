package com.superman.drilldemo // 替换为你的包名

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.*

class HalfCircleProgressBar2 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progressColor: Int
    private var trackColor: Int
    private var progressWidth: Float
    private var maxProgress: Int
    private var currentProgress: Int
    private var thumbColor: Int
    private var thumbRadius: Float
    private var touchEnabled: Boolean

    // 固定为标准的底部半圆 (180度范围，开口朝上)
    // 从左边水平线开始 (180度)，扫过180度到右边水平线 (0度或360度)
    private var actualStartAngle: Float = 180f
    private var actualSweepAngleRange: Float = 180f

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val progressRect = RectF()
    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var radius: Float = 0f

    private var currentSweepAngle: Float = 0f // 绘制进度时扫过的角度
    private var thumbX: Float = 0f
    private var thumbY: Float = 0f

    // 用于触摸判断的额外容差 (例如，使用户更容易点到细的进度条)
    private var touchSlopRadiusOffset: Float = 20f // px

    var onProgressChangedListener: ((Int) -> Unit)? = null

    init {
        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.HalfCircleProgressBar,
            defStyleAttr,
            0
        )

        progressColor = typedArray.getColor(
            R.styleable.HalfCircleProgressBar_hcp_progressColor,
            ContextCompat.getColor(context, android.R.color.holo_blue_bright)
        )
        trackColor = typedArray.getColor(
            R.styleable.HalfCircleProgressBar_hcp_trackColor,
            ContextCompat.getColor(context, android.R.color.darker_gray)
        )
        progressWidth = typedArray.getDimension(
            R.styleable.HalfCircleProgressBar_hcp_progressWidth,
            25f
        )
        maxProgress = typedArray.getInt(
            R.styleable.HalfCircleProgressBar_hcp_maxProgress,
            100
        )
        currentProgress = typedArray.getInt(
            R.styleable.HalfCircleProgressBar_hcp_progress,
            0
        ).coerceIn(0, maxProgress)

        thumbColor = typedArray.getColor(
            R.styleable.HalfCircleProgressBar_hcp_thumbColor,
            ContextCompat.getColor(context, android.R.color.black)
        )
        thumbRadius = typedArray.getDimension(
            R.styleable.HalfCircleProgressBar_hcp_thumbRadius,
            30f
        )
        // 允许通过 XML 覆盖 startAngle 和 sweepAngle，但默认使用标准的180度半圆
        actualStartAngle = typedArray.getFloat(R.styleable.HalfCircleProgressBar_hcp_startAngle, 180f)
        actualSweepAngleRange = typedArray.getFloat(R.styleable.HalfCircleProgressBar_hcp_sweepAngle, 180f)

        touchEnabled = typedArray.getBoolean(
            R.styleable.HalfCircleProgressBar_hcp_touchEnabled,
            true
        )

        typedArray.recycle()
        touchSlopRadiusOffset = thumbRadius // 触摸区域至少是滑块的半径

        setupPaints()
        updateCurrentSweepAngleAndThumb()
    }

    private fun setupPaints() {
        trackPaint.color = trackColor
        trackPaint.style = Paint.Style.STROKE
        trackPaint.strokeWidth = progressWidth
        trackPaint.strokeCap = Paint.Cap.ROUND

        progressPaint.color = progressColor
        progressPaint.style = Paint.Style.STROKE
        progressPaint.strokeWidth = progressWidth
        progressPaint.strokeCap = Paint.Cap.ROUND

        thumbPaint.color = thumbColor
        thumbPaint.style = Paint.Style.FILL
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val desiredWidth = MeasureSpec.getSize(widthMeasureSpec)
        // 对于标准的底部半圆，高度通常是宽度的一半加上一些padding和滑块/轨道宽度
        // 这里我们让用户在XML中定义高度，或者基于宽度计算一个期望高度
        val desiredHeight = (desiredWidth / 2f + progressWidth + thumbRadius + paddingTop + paddingBottom).toInt()
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(desiredWidth, measuredHeight)
    }


    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val viewWidth = w - paddingLeft - paddingRight
        val viewHeight = h - paddingTop - paddingBottom

        centerX = w / 2f // paddingLeft + viewWidth / 2f
        // 对于底部半圆，圆心Y坐标在视图的底部附近
        // 半径的计算需要确保它能容纳在提供的高度内
        // 同时，直径不能超过视图的宽度

        val maxDiameterFromWidth = viewWidth.toFloat()
        // 高度减去滑块和轨道厚度的一半，剩下的空间是给半径的
        // (因为半圆只占高度的一半)
        val maxRadiusFromHeight = viewHeight - progressWidth / 2f - thumbRadius

        radius = min(maxDiameterFromWidth / 2f, maxRadiusFromHeight) - progressWidth / 2f


        // 调整centerY，使半圆的平边与视图底部对齐 (或接近)
        // centerY 应该是从视图顶部算起，到 "圆盘底部再往上一个半径" 的位置
        centerY = h - paddingBottom - thumbRadius - progressWidth/2f - ( (actualSweepAngleRange / 180f -1 ) * radius / 2 ) // 微调以适应非180度的情况


        // 更新矩形区域
        progressRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )

        Log.d("HalfCircle", "onSizeChanged: w=$w, h=$h, padL=$paddingLeft, padR=$paddingRight, padT=$paddingTop, padB=$paddingBottom")
        Log.d("HalfCircle", "onSizeChanged: viewW=$viewWidth, viewH=$viewHeight, centerX=$centerX, centerY=$centerY, radius=$radius")

        updateCurrentSweepAngleAndThumb()
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 1. 绘制轨道
        canvas.drawArc(progressRect, actualStartAngle, actualSweepAngleRange, false, trackPaint)

        // 2. 绘制进度
        if (currentSweepAngle > 0.001f) { // 避免绘制非常小的线段
            canvas.drawArc(progressRect, actualStartAngle, currentSweepAngle, false, progressPaint)
        }

        // 3. 绘制滑块
        if (touchEnabled) {
            canvas.drawCircle(thumbX, thumbY, thumbRadius, thumbPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!touchEnabled) {
            return super.onTouchEvent(event)
        }

        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isTouchOnRing(x, y)) {
                    updateProgressFromTouchEvent(x, y)
                    parent.requestDisallowInterceptTouchEvent(true) // 请求父View不拦截事件
                    return true
                }
                return false // 没有点在圆环上，不消耗事件
            }
            MotionEvent.ACTION_MOVE -> {
                updateProgressFromTouchEvent(x, y) // 在移动时，即使移出圆环也继续更新
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent.requestDisallowInterceptTouchEvent(false)
                // performClick() // 如果你想在抬起时触发一个标准的点击事件
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isTouchOnRing(touchX: Float, touchY: Float): Boolean {
        val dx = touchX - centerX
        val dy = touchY - centerY
        val distanceSquared = dx * dx + dy * dy // 到圆心的距离的平方
        val outerRadius = radius + progressWidth / 2f + touchSlopRadiusOffset // 外环半径 + 容差
        val innerRadius = radius - progressWidth / 2f - touchSlopRadiusOffset // 内环半径 - 容差

        if (distanceSquared > outerRadius * outerRadius || distanceSquared < innerRadius * innerRadius) {
            return false // 不在径向范围内
        }

        // 检查角度是否在半圆的有效范围内
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) {
            angle += 360f
        }

        // 将角度标准化到 actualStartAngle 为 0 的坐标系
        var relativeAngle = (angle - actualStartAngle + 360f) % 360f

        return relativeAngle >= 0 && relativeAngle <= actualSweepAngleRange
    }


    private fun updateProgressFromTouchEvent(touchX: Float, touchY: Float) {
        val dx = touchX - centerX
        val dy = touchY - centerY
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        if (angle < 0) {
            angle += 360f
        }

        var newSweep = (angle - actualStartAngle + 360f) % 360f

        // 限制 newSweep 在 [0, actualSweepAngleRange]
        // 对于底部半圆 (180度到360/0度)，如果角度跳到另一边，需要特殊处理
        // 例如，如果 actualStartAngle=180, actualSweepAngleRange=180
        // 有效角度是 180 (最左) 到 360/0 (最右)
        // 如果 angle 接近 0 (例如 10度)，newSweep 应该是 180 + 10 = 190 (错误) -> 应接近 actualSweepAngleRange
        // 如果 angle 接近 350 (例如 350度)，newSweep 应该是 350 - 180 = 170 (正确) -> 接近 actualSweepAngleRange

        if (actualSweepAngleRange == 180f && actualStartAngle == 180f) { // 标准底部半圆的特殊处理
            if (angle > 90f && angle < 180f) { // 左上象限，超出范围
                newSweep = 0f
            } else if (angle < 270f && angle > 180f) { // 左下象限 (有效)
                newSweep = angle - 180f
            } else if (angle >= 270f || angle <=90f) { // 右侧 (有效)
                // angle 0-90 (例如 10度) -> newSweep 应该是 180+10 (如果直接减) -> 应该是 180- (90-angle)
                // angle 270-360 (例如 350度) -> newSweep 应该是 angle - 180
                if (angle <=90f) newSweep = 180f - (90-angle) //  (错误，这会反向)
                //  正确逻辑：
                //  angle 0..90 -> 映射到 sweep 的 90..180
                //  angle 270..360 -> 映射到 sweep 的 90..0 (反向)
                //  我们希望的是从左到右，角度从 180 -> 360/0
                //  所以，如果 angle 在 [0, 90] (右上方), sweep 应接近 180
                //  如果 angle 在 [270, 360] (右下方), sweep 应接近 180
                //  如果 angle 在 [180, 270] (左下方), sweep 应接近 0
                if (angle in 0f..actualStartAngle - 90f) { // 例如 0..90 for startAngle 180
                    newSweep = actualSweepAngleRange // 最右边
                } else if (angle > actualStartAngle + actualSweepAngleRange + 90f ) { //
                    newSweep = actualSweepAngleRange
                }

            }
        }
        // 更通用的钳制，适用于任何 startAngle 和 sweepAngle
        newSweep = newSweep.coerceIn(0f, actualSweepAngleRange)


        val newProgress = ((newSweep / actualSweepAngleRange) * maxProgress).toInt()
        setProgress(newProgress, true)
    }


    private fun updateCurrentSweepAngleAndThumb() {
        currentSweepAngle = (currentProgress.toFloat() / maxProgress.toFloat()) * actualSweepAngleRange
        updateThumbPosition()
    }

    private fun updateThumbPosition() {
        val angleRad = Math.toRadians((actualStartAngle + currentSweepAngle).toDouble())
        thumbX = centerX + (radius * cos(angleRad)).toFloat()
        thumbY = centerY + (radius * sin(angleRad)).toFloat()
    }

    // --- Public API ---
    fun getProgress(): Int = currentProgress

    fun setProgress(progress: Int, fromUser: Boolean = false) {
        val newProgress = progress.coerceIn(0, maxProgress)
        if (newProgress != currentProgress || !fromUser) { // 允许强制刷新即使值相同（如果不是用户触发）
            currentProgress = newProgress
            updateCurrentSweepAngleAndThumb()
            invalidate()
            onProgressChangedListener?.invoke(currentProgress)
        }
    }

    // ... (其他 getter/setter 方法可以保留或按需添加/修改)
    fun getMaxProgress(): Int = maxProgress

    fun setMaxProgress(max: Int) {
        if (max > 0) {
            maxProgress = max
            currentProgress = currentProgress.coerceIn(0, maxProgress)
            updateCurrentSweepAngleAndThumb()
            invalidate()
        }
    }

    fun setProgressColor(color: Int) {
        progressColor = color
        progressPaint.color = progressColor
        invalidate()
    }

    fun setTrackColor(color: Int) {
        trackColor = color
        trackPaint.color = trackColor
        invalidate()
    }

    fun setProgressWidth(width: Float) {
        progressWidth = width
        trackPaint.strokeWidth = progressWidth
        progressPaint.strokeWidth = progressWidth
        requestLayout() // 宽度改变需要重新测量和布局
        invalidate()
    }

    fun setThumbColor(color: Int) {
        thumbColor = color
        thumbPaint.color = thumbColor
        invalidate()
    }

    fun setThumbRadius(radiusParam: Float) {
        thumbRadius = radiusParam
        touchSlopRadiusOffset = thumbRadius // 更新触摸容差
        requestLayout() // 滑块半径改变需要重新测量和布局
        invalidate()
    }

    fun setTouchEnabled(enabled: Boolean) {
        touchEnabled = enabled
    }
}
