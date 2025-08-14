package com.superman.drilldemo // 替换为你的包名

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.*

class HalfCircleProgressBar3 @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ... (其他属性声明保持不变)
    private var progressColor: Int
    private var trackColor: Int
    private var progressWidth: Float
    private var maxProgress: Int
    private var currentProgress: Int
    private var thumbColor: Int
    private var thumbRadius: Float
    private var touchEnabled: Boolean

    private var actualStartAngle: Float = 180f
    private var actualSweepAngleRange: Float = 180f

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

    private var touchSlopRadiusOffset: Float = 20f
    private var isDragging = false // 新增一个标志位，表示是否正在拖拽

    var onProgressChangedListener: ((Int) -> Unit)? = null


    init {
        val typedArray = context.obtainStyledAttributes(
            attrs,
            R.styleable.HalfCircleProgressBar, // 确保这个 R.styleable 路径正确
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
        actualStartAngle = typedArray.getFloat(R.styleable.HalfCircleProgressBar_hcp_startAngle, 180f)
        actualSweepAngleRange = typedArray.getFloat(R.styleable.HalfCircleProgressBar_hcp_sweepAngle, 180f)

        touchEnabled = typedArray.getBoolean(
            R.styleable.HalfCircleProgressBar_hcp_touchEnabled,
            true
        )

        typedArray.recycle()
        touchSlopRadiusOffset = thumbRadius // 触摸区域至少是滑块的半径, 也可以是 progressWidth / 2f

        setupPaints()
        updateCurrentSweepAngleAndThumb()
    }

    // ... (setupPaints, onMeasure, onSizeChanged, onDraw 保持不变)
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
        val desiredHeight = (desiredWidth / 2f + progressWidth + thumbRadius + paddingTop + paddingBottom ).toInt() // 缺陷修正：确保高度足够
        val measuredHeight = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(desiredWidth, measuredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val viewWidth = w - paddingLeft - paddingRight
        val viewHeight = h - paddingTop - paddingBottom

        centerX = paddingLeft + viewWidth / 2f // 使用 paddingLeft 修正 centerX

        // 调整centerY和radius，确保半圆底部与视图底部对齐，并能容纳在高度内
        // 同时，直径不能超过视图的宽度

        // 半径的计算基于更严格的约束
        val maxDiameterFromWidth = viewWidth.toFloat()
        // 高度减去滑块和轨道厚度的一半 (因为是半圆，且圆心在平边上或附近)
        val maxRadiusFromHeight = viewHeight - progressWidth / 2f - thumbRadius

        // 真实的半径是两者中较小的一个，再减去轨道宽度的一半（因为Paint.Style.STROKE是从中心向两侧扩展）
        radius = min(maxDiameterFromWidth / 2f, maxRadiusFromHeight)
        radius = radius - progressWidth / 2f // 为轨道厚度留出空间

        // 对于底部半圆，圆心Y坐标
        // 使得圆弧的最低点（包括滑块和轨道）大致在视图的底部
        // centerY 应该是从视图顶部算起，到 "视图底部 - 滑块半径 - 轨道一半厚度" 的位置
        // 这样，半径为 radius 的圆弧会画在这个 centerY 上方
        centerY = h - paddingBottom - thumbRadius - progressWidth / 2f


        progressRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        updateCurrentSweepAngleAndThumb()
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(progressRect, actualStartAngle, actualSweepAngleRange, false, trackPaint)
        if (currentSweepAngle > 0.001f) {
            canvas.drawArc(progressRect, actualStartAngle, currentSweepAngle, false, progressPaint)
        }
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
                    isDragging = true // 开始拖拽
                    parent.requestDisallowInterceptTouchEvent(true)
                    updateProgressFromTouchEvent(x, y) // 按下时也更新一次进度
                    return true
                }
                isDragging = false // 没有点在圆环上
                return false // 不消耗事件，允许父视图处理
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) { // 只有在拖拽状态下才响应MOVE
                    updateProgressFromTouchEvent(x, y)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    parent.requestDisallowInterceptTouchEvent(false)
                    // 可选: updateProgressFromTouchEvent(x, y) // 抬起时最后更新一次
                    isDragging = false // 结束拖拽
                    performClick() // 如果按下和抬起都在圆环内，可以认为是一次点击
                    return true
                }
            }
        }
        return super.onTouchEvent(event) // 对于其他未处理的情况
    }

    override fun performClick(): Boolean {
        // 如果需要区分纯点击和拖拽后的抬起，可以在这里加入逻辑
        // 例如，只有当ACTION_DOWN和ACTION_UP之间的移动距离很小时才视为点击
        Log.d("HalfCircleProgressBar", "performClick called")
        return super.performClick()
    }

    private fun isTouchOnRing(touchX: Float, touchY: Float): Boolean {
        val dx = touchX - centerX
        val dy = touchY - centerY
        val distanceSquared = dx * dx + dy * dy // 到圆心的距离的平方

        // 考虑 progressWidth 和 touchSlopRadiusOffset 来定义圆环的内外边界
        val effectiveOuterRadius = radius + progressWidth / 2f + touchSlopRadiusOffset
        val effectiveInnerRadius = radius - progressWidth / 2f - touchSlopRadiusOffset

        if (distanceSquared > effectiveOuterRadius * effectiveOuterRadius ||
            distanceSquared < effectiveInnerRadius * effectiveInnerRadius) {
            Log.d("HalfCircleDebug", "Touch out of radial bounds: distSq=$distanceSquared, outerSq=${effectiveOuterRadius * effectiveOuterRadius}, innerSq=${effectiveInnerRadius * effectiveInnerRadius}")
            return false // 不在径向范围内
        }

        // 检查角度是否在半圆的有效扫描范围内
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
        if (angle < 0) {
            angle += 360f
        }

        // 将触摸角度标准化，使其相对于 actualStartAngle (0度代表起始点)
        // (angle - actualStartAngle + 360f) % 360f 会得到从 startAngle 开始顺时针的角度值
        val relativeAngle = (angle - actualStartAngle + 360f) % 360f

        // 确保相对角度在 [0, actualSweepAngleRange] 之间
        // 同时，由于浮点数精度问题，可以给一个小的容差
        val isOnCorrectAngle = relativeAngle >= -0.5f && relativeAngle <= actualSweepAngleRange + 0.5f
        if (!isOnCorrectAngle) {
            Log.d("HalfCircleDebug", "Touch out of angular bounds: touchAngle=$angle, relativeAngle=$relativeAngle, startAngle=$actualStartAngle, sweep=$actualSweepAngleRange")
        }
        return isOnCorrectAngle
    }


    private fun updateProgressFromTouchEvent(touchX: Float, touchY: Float) {
        val dx = touchX - centerX
        val dy = touchY - centerY
        var angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()

        if (angle < 0) {
            angle += 360f
        }

        var newSweep = (angle - actualStartAngle + 360f) % 360f

        // 将 newSweep 限制在 [0, actualSweepAngleRange]
        // 这个钳制对于大部分情况是有效的。
        // 对于非常特殊的 startAngle 和 sweepAngle 组合，或者当手指快速滑过“死区”时，
        // 可能需要更复杂的逻辑来判断用户的真实意图（是想跳到0%还是100%）。
        // 但对于标准的底部半圆，这个钳制通常足够。
        if (newSweep > actualSweepAngleRange) {
            // 如果超出了扫描范围，判断是更接近0度还是actualSweepAngleRange度
            // (360 - newSweep) 是指从newSweep逆时针到360/0的距离
            // newSweep - actualSweepAngleRange 是指从actualSweepAngleRange顺时针到newSweep的距离
            // 如果触摸点明显偏离了扫描区域，我们可能需要根据它是在扫描区域的哪一“侧”来决定
            val distToStartViaWrap = (newSweep - actualSweepAngleRange) // 距离终点的"超调"量
            val distToEndViaWrap = (360f - newSweep) // 距离起点的"回绕"量 (如果startAngle接近0)

            // 一个简化的逻辑：如果 newSweep 超过 sweepRange 的一半以上，则认为是终点，否则是起点
            // 这个逻辑在手指滑出有效半圆到另一侧时，决定是吸附到0还是100
            if (newSweep > actualSweepAngleRange && newSweep < (360f + actualStartAngle - actualSweepAngleRange/2 ) %360f ) { //  (actualSweepAngleRange + 360f) / 2f
                currentSweepAngle = actualSweepAngleRange
            } else {
                currentSweepAngle = 0f
            }

        } else {
            currentSweepAngle = newSweep
        }
        currentSweepAngle = currentSweepAngle.coerceIn(0f, actualSweepAngleRange)


        val newProgress = ((currentSweepAngle / actualSweepAngleRange) * maxProgress).toInt()
        setProgress(newProgress, true) // fromUser = true
    }

    // ... (updateCurrentSweepAngleAndThumb, updateThumbPosition, getProgress, setProgress, 等其他API方法保持不变)
    private fun updateCurrentSweepAngleAndThumb() {
        currentSweepAngle = (currentProgress.toFloat() / maxProgress.toFloat()) * actualSweepAngleRange
        updateThumbPosition()
    }

    private fun updateThumbPosition() {
        // 确保 radius 是正数，避免 NaN
        val safeRadius = if (radius > 0) radius else 0f
        val angleRad = Math.toRadians((actualStartAngle + currentSweepAngle).toDouble())
        thumbX = centerX + (safeRadius * cos(angleRad)).toFloat()
        thumbY = centerY + (safeRadius * sin(angleRad)).toFloat()
    }

    fun getProgress(): Int = currentProgress

    fun setProgress(progress: Int, fromUser: Boolean = false) {
        val newProgress = progress.coerceIn(0, maxProgress)
        // 只有当进度实际改变，或者不是用户触发时（允许代码强制刷新）才执行
        if (newProgress != currentProgress || !fromUser) {
            currentProgress = newProgress
            updateCurrentSweepAngleAndThumb()
            invalidate() // 重绘视图
            onProgressChangedListener?.invoke(currentProgress)
        }
    }

    fun getMaxProgress(): Int = maxProgress

    fun setMaxProgress(max: Int) {
        if (max > 0) {
            maxProgress = max
            currentProgress = currentProgress.coerceIn(0, maxProgress)
            updateCurrentSweepAngleAndThumb()
            invalidate()
        }
    }
    // ... (其他setter和getter，例如颜色、宽度等)
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

    // 缺陷修正常量，如果布局仍然有问题，可以微调这个值
    companion object {
        private const val LAYOUT_DEFECT_CORRECTION = 5f // dp, 转换为 px
    }


}
