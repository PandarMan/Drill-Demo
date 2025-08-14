package com.example.customview // 替换为你的包名

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.text.color
import com.superman.drilldemo.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class HalfCircleProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var progressColor: Int = Color.BLUE
    private var trackColor: Int = Color.LTGRAY
    private var progressWidth: Float = 20f
    private var maxProgress: Int = 100
    private var currentProgress: Int = 0
    private var thumbColor: Int = Color.DKGRAY
    private var thumbRadius: Float = 30f
    private var startAngle: Float = 135f //  135度开始 (左下)
    private var sweepAngleRange: Float = 270f //  扫过270度 (形成一个底部半圆)
    private var touchEnabled: Boolean = true

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val progressRect = RectF()
    private var centerX: Float = 0f
    private var centerY: Float = 0f
    private var radius: Float = 0f

    private var currentSweepAngle: Float = 0f
    private var thumbX: Float = 0f
    private var thumbY: Float = 0f

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
        startAngle = typedArray.getFloat(
            R.styleable.HalfCircleProgressBar_hcp_startAngle,
            135f //  默认底部半圆，开口朝上
        )
        sweepAngleRange = typedArray.getFloat(
            R.styleable.HalfCircleProgressBar_hcp_sweepAngle,
            270f //  默认扫过270度
        )
        touchEnabled = typedArray.getBoolean(
            R.styleable.HalfCircleProgressBar_hcp_touchEnabled,
            true
        )

        typedArray.recycle()

        setupPaints()
        updateCurrentSweepAngle()
    }

    private fun setupPaints() {
        trackPaint.color = trackColor
        trackPaint.style = Paint.Style.STROKE
        trackPaint.strokeWidth = progressWidth
        trackPaint.strokeCap = Paint.Cap.ROUND //  可选，使轨道末端变圆

        progressPaint.color = progressColor
        progressPaint.style = Paint.Style.STROKE
        progressPaint.strokeWidth = progressWidth
        progressPaint.strokeCap = Paint.Cap.ROUND //  可选，使进度末端变圆

        thumbPaint.color = thumbColor
        thumbPaint.style = Paint.Style.FILL
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        centerX = w / 2f
        centerY = h / 2f //  我们将基于较小的尺寸来确定半径，以适应半圆

        //  调整centerY和radius以确保半圆在视图内且开口方向正确
        //  这个计算假设半圆主要在底部或顶部。对于左右两侧的半圆，逻辑需要调整。
        val diameter = min(w, h * 2) //  因为是半圆，高度可以用来决定直径
        radius = (diameter / 2f) - progressWidth / 2f - thumbRadius //  为轨道宽度和滑块留出空间

        //  调整 centerY 使半圆看起来在视图的 "底部" 或 "顶部"
        //  如果 startAngle 在 90-270 之间 (底部半圆)
        if (startAngle >= 90 && startAngle + sweepAngleRange <= 450 && (startAngle + sweepAngleRange - 360 <= 90 || startAngle + sweepAngleRange <= 270 )) {
            centerY = h - radius - progressWidth / 2f - thumbRadius - paddingTop - paddingBottom // 假设底部半圆
        } else if (startAngle <= 90 || startAngle >=270 ) { // 假设顶部半圆
            centerY = radius + progressWidth / 2f + thumbRadius + paddingTop
        }
        //  以上 centerY 的调整比较粗略，可能需要根据你的具体 startAngle 和 sweepAngleRange 细化

        progressRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        updateThumbPosition()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 1. 绘制轨道
        canvas.drawArc(progressRect, startAngle, sweepAngleRange, false, trackPaint)
        // 2. 绘制进度
        if (currentSweepAngle > 0) {
            canvas.drawArc(progressRect, startAngle, currentSweepAngle, false, progressPaint)
        }
        // 3. 绘制滑块
        if (touchEnabled) { // 只有在可触摸时才绘制滑块，或根据需要始终绘制
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
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                updateProgressFromTouchEvent(x, y)
                return true //  消耗事件
            }
            MotionEvent.ACTION_UP -> {
                //  可选：执行一些操作，比如吸附到最近的刻度 (如果需要)
                performClick() //  处理点击事件
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        // 在这里处理点击逻辑，如果需要区分拖拽和纯点击的话
        //  当前实现中，ACTION_UP 也会触发进度更新
        return true
    }


    private fun updateProgressFromTouchEvent(touchX: Float, touchY: Float) {
        val dx = touchX - centerX
        val dy = touchY - centerY
        //  计算触摸点相对于圆心的角度 (弧度转角度)
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        //  将角度标准化到 0-360 范围
        if (angle < 0) {
            angle += 360f
        }

        //  将触摸角度映射到我们的进度条的有效角度范围内
        //  currentAngleInSweep = angle - startAngle (处理环绕情况)
        var relativeAngle = angle - startAngle
        if (relativeAngle < 0) {
            relativeAngle += 360f
        }

        //  确保角度在 sweepAngleRange 内
        if (relativeAngle > sweepAngleRange) {
            //  判断触摸点是在 sweepAngleRange 的开始侧还是结束侧
            //  这是一个简化的判断，可能需要更复杂的逻辑来处理边缘情况
            val endAngle = (startAngle + sweepAngleRange) % 360
            val midAngleSweep = sweepAngleRange / 2f
            var diffToStart = (relativeAngle + 360f) % 360f // angle relative to startAngle = 0
            var diffToEnd = (relativeAngle - sweepAngleRange + 360f) % 360f // angle relative to end of sweep

            //  更准确的方式是比较触摸点到起始弧线和结束弧线的距离
            //  这里我们简单地将超出范围的认为是0或最大值
            if (diffToStart > 180 && diffToStart < 360-midAngleSweep) { //  靠近 sweep 的结束
                currentSweepAngle = sweepAngleRange
            } else if (diffToStart < 180 && diffToStart > midAngleSweep) { // 靠近 sweep 的开始
                currentSweepAngle = 0f
            } else {
                //  如果角度在 sweep 范围之外，我们可能需要判断它是更接近0%还是100%
                //  一个简单的方法是，如果它超出了 sweepAngleRange，就把它限制在0或sweepAngleRange
                //  这里用一个粗略的方法，如果相对角度超过 sweepAngleRange 的一半，则设为最大，否则为0
                //  这部分逻辑对于边缘情况的拖拽体验很关键，需要仔细调整
                val angleToStart = calculateAngleDifference(angle, startAngle)
                val angleToEnd = calculateAngleDifference(angle, (startAngle + sweepAngleRange) % 360f)

                if (angleToStart < angleToEnd && angleToStart < 90) { //  假设小于90度差异算作有效
                    currentSweepAngle = 0f
                } else if (angleToEnd < angleToStart && angleToEnd < 90) {
                    currentSweepAngle = sweepAngleRange
                } else {
                    //  如果无法明确，可能不更新或根据上一次的位置判断
                    return
                }
            }

        } else {
            currentSweepAngle = relativeAngle
        }


        currentSweepAngle = currentSweepAngle.coerceIn(0f, sweepAngleRange)

        val newProgress = ((currentSweepAngle / sweepAngleRange) * maxProgress).toInt()
        setProgress(newProgress, true) //  通过true来表示是由用户交互触发的
    }

    // 计算两个角度之间的最小差异 (0-180)
    private fun calculateAngleDifference(angle1: Float, angle2: Float): Float {
        var diff = Math.abs(angle1 - angle2) % 360
        if (diff > 180) {
            diff = 360 - diff
        }
        return diff
    }


    private fun updateThumbPosition() {
        val angleRad = Math.toRadians((startAngle + currentSweepAngle).toDouble())
        thumbX = centerX + (radius * cos(angleRad)).toFloat()
        thumbY = centerY + (radius * sin(angleRad)).toFloat()
    }

    private fun updateCurrentSweepAngle() {
        currentSweepAngle = (currentProgress.toFloat() / maxProgress.toFloat()) * sweepAngleRange
        updateThumbPosition()
    }

    // --- Public API ---
    fun getProgress(): Int = currentProgress

    fun setProgress(progress: Int, fromUser: Boolean = false) {
        val newProgress = progress.coerceIn(0, maxProgress)
        if (newProgress != currentProgress) {
            currentProgress = newProgress
            updateCurrentSweepAngle()
            invalidate() //  重绘视图
            onProgressChangedListener?.invoke(currentProgress)
        }
    }

    fun getMaxProgress(): Int = maxProgress

    fun setMaxProgress(max: Int) {
        if (max > 0) {
            maxProgress = max
            currentProgress = currentProgress.coerceIn(0, maxProgress) // 重新约束当前进度
            updateCurrentSweepAngle()
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
        //  当宽度改变时，半径也需要重新计算
        onSizeChanged(width.toInt(), height, width.toInt(), height) // 触发onSizeChanged重新计算尺寸
        invalidate()
    }

    fun setThumbColor(color: Int) {
        thumbColor = color
        thumbPaint.color = thumbColor
        invalidate()
    }

    fun setThumbRadius(radius: Float) {
        thumbRadius = radius
        //  当滑块半径改变时，整体半径也可能需要调整
        onSizeChanged(width, height, width, height) // 触发onSizeChanged重新计算尺寸
        invalidate()
    }

    fun setTouchEnabled(enabled: Boolean) {
        touchEnabled = enabled
    }
}
