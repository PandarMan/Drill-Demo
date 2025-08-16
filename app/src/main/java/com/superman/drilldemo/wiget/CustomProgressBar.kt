package com.superman.drilldemo.wiget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.superman.drilldemo.R
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class CustomProgressBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // Default styleable attribute values
    private var progressColor = Color.BLUE
    private var trackColor = Color.LTGRAY
    private var progressBarHeight = 20f // in pixels
    private var maxProgress = 100
    private var currentProgress = 0
    private var cornerRadius = 0f

    // Thumb properties
    private var thumbDrawable: Drawable? = null
    private var thumbWidth: Int = 0
    private var thumbHeight: Int = 0
    private var thumbTint: Int? = null

    // Interaction control
    private var isUserSeekable: Boolean = true
    private var isThumbDraggable: Boolean = true
    private var isDraggingThumb: Boolean = false

    // Paint objects
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val progressRect = RectF() // Reusable RectF for drawing

    // Thumb position
    private var thumbX: Float = 0f // Current X position of the thumb's center
    private var thumbY: Float = 0f // Current Y position of the thumb's center

    companion object {
        private const val TAG = "CustomProgressBar" // For logging
        private const val DEFAULT_MIN_VERTICAL_TOUCH_DP = 48f // Default target for vertical touch area
    }

    init {
        attrs?.let {
            val typedArray = context.obtainStyledAttributes(
                it,
                R.styleable.CustomProgressBar,
                defStyleAttr,
                0
            )
            progressColor = typedArray.getColor(
                R.styleable.CustomProgressBar_cpb_progressColor,
                progressColor
            )
            trackColor = typedArray.getColor(
                R.styleable.CustomProgressBar_cpb_trackColor,
                trackColor
            )
            progressBarHeight = typedArray.getDimension(
                R.styleable.CustomProgressBar_cpb_progressBarHeight,
                progressBarHeight
            )
            maxProgress = typedArray.getInt(
                R.styleable.CustomProgressBar_cpb_max,
                maxProgress
            ).coerceAtLeast(1) // Max progress should be at least 1

            currentProgress = typedArray.getInt(
                R.styleable.CustomProgressBar_cpb_progress,
                currentProgress
            ).coerceIn(0, maxProgress)

            cornerRadius = typedArray.getDimension(
                R.styleable.CustomProgressBar_cpb_cornerRadius,
                cornerRadius
            )

            if (typedArray.hasValue(R.styleable.CustomProgressBar_cpb_thumbDrawable)) {
                thumbDrawable = typedArray.getDrawable(R.styleable.CustomProgressBar_cpb_thumbDrawable)
            }

            thumbWidth = typedArray.getDimensionPixelSize(
                R.styleable.CustomProgressBar_cpb_thumbWidth,
                thumbDrawable?.intrinsicWidth ?: 0
            )
            thumbHeight = typedArray.getDimensionPixelSize(
                R.styleable.CustomProgressBar_cpb_thumbHeight,
                thumbDrawable?.intrinsicHeight ?: 0
            )

            if (typedArray.hasValue(R.styleable.CustomProgressBar_cpb_thumbTint)) {
                thumbTint = typedArray.getColor(R.styleable.CustomProgressBar_cpb_thumbTint, Color.TRANSPARENT)
            }

            isUserSeekable = typedArray.getBoolean(R.styleable.CustomProgressBar_cpb_isUserSeekable, true)
            isThumbDraggable = typedArray.getBoolean(R.styleable.CustomProgressBar_cpb_isThumbDraggable, true)

            if (!isUserSeekable) {
                isThumbDraggable = false
            }

            typedArray.recycle()
        }
        setupPaints()
        applyThumbTint()
        updateThumbCoordinatesAndBounds(calculateThumbXFromProgress(currentProgress)) // Initial position based on progress
    }

    private fun setupPaints() {
        trackPaint.color = trackColor
        trackPaint.style = Paint.Style.FILL
        progressPaint.color = progressColor
        progressPaint.style = Paint.Style.FILL
    }

    private fun applyThumbTint() {
        thumbDrawable?.let { drawable ->
            val wrappedDrawable = DrawableCompat.wrap(drawable).mutate()
            thumbTint?.let { color ->
                DrawableCompat.setTint(wrappedDrawable, color)
            } ?: DrawableCompat.setTintList(wrappedDrawable, null)
        }
    }

    private fun convertDpToPixel(dp: Float, context: Context): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, context.resources.displayMetrics)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = MeasureSpec.getSize(widthMeasureSpec)
        val specModeHeight = MeasureSpec.getMode(heightMeasureSpec)
        val specSizeHeight = MeasureSpec.getSize(heightMeasureSpec)

        val actualThumbH = if (thumbDrawable != null) (if (thumbHeight > 0) thumbHeight else thumbDrawable!!.intrinsicHeight) else 0
        val contentHeight = max(progressBarHeight, actualThumbH.toFloat())
        val desiredHeight = (contentHeight + paddingTop + paddingBottom).toInt()

        val measuredHeight = when (specModeHeight) {
            MeasureSpec.EXACTLY -> specSizeHeight
            MeasureSpec.AT_MOST -> min(desiredHeight, specSizeHeight)
            else -> desiredHeight
        }
        setMeasuredDimension(desiredWidth, measuredHeight)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // When size changes, recalculate thumb position based on current progress
        updateThumbCoordinatesAndBounds(calculateThumbXFromProgress(currentProgress))
    }


    /**
     * Calculates the target X coordinate for the thumb's center based on a given progress value.
     */
    private fun calculateThumbXFromProgress(progressValue: Int): Float {
        val viewWidth = width.toFloat()
        val drawableWidth = viewWidth - paddingLeft - paddingRight
        if (drawableWidth <= 0) return paddingLeft.toFloat()

        val progressRatio = if (maxProgress > 0) progressValue.toFloat() / maxProgress.toFloat() else 0f
        return paddingLeft + (drawableWidth * progressRatio.coerceIn(0f, 1f))
    }

    /**
     * Updates the thumb's X and Y coordinates and its drawable bounds.
     * @param newThumbX The new X coordinate for the thumb's center.
     */
    private fun updateThumbCoordinatesAndBounds(newThumbX: Float) {
        this.thumbX = newThumbX

        val contentDrawableHeight = height - paddingTop - paddingBottom
        if (contentDrawableHeight <= 0 && height > 0) { // Check if padding consumes all height
            Log.w(TAG, "updateThumbCoordinatesAndBounds: Content drawable height is zero or negative. Thumb Y position might be incorrect.")
            this.thumbY = height / 2f
        } else {
            this.thumbY = paddingTop + contentDrawableHeight / 2f
        }


        thumbDrawable?.let {
            val actualThumbW = if (thumbWidth > 0) thumbWidth else it.intrinsicWidth
            val actualThumbH = if (thumbHeight > 0) thumbHeight else it.intrinsicHeight

            if (actualThumbW <= 0 || actualThumbH <= 0 && thumbDrawable != null) {
                Log.w(TAG, "updateThumbCoordinatesAndBounds: Thumb intrinsic/defined size is zero or negative (W:$actualThumbW, H:$actualThumbH). It might not be visible or sized correctly.")
            }

            val halfThumbW = actualThumbW / 2f
            val halfThumbH = actualThumbH / 2f
            it.setBounds(
                (this.thumbX - halfThumbW).roundToInt(),
                (this.thumbY - halfThumbH).roundToInt(),
                (this.thumbX + halfThumbW).roundToInt(),
                (this.thumbY + halfThumbH).roundToInt()
            )
        }
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val viewWidth = width.toFloat()
        val contentDrawableHeight = height - paddingTop - paddingBottom
        val barTop = paddingTop + (contentDrawableHeight - progressBarHeight) / 2f
        val barBottom = barTop + progressBarHeight

        progressRect.set(paddingLeft.toFloat(), barTop, viewWidth - paddingRight, barBottom)
        canvas.drawRoundRect(progressRect, cornerRadius, cornerRadius, trackPaint)

        if (maxProgress > 0 && currentProgress > 0) {
            // Draw progress based on currentProgress, not necessarily the visual thumbX during drag
            val progressEndX = calculateThumbXFromProgress(currentProgress)
            if (progressEndX > paddingLeft.toFloat()) {
                progressRect.set(paddingLeft.toFloat(), barTop, progressEndX, barBottom)
                canvas.drawRoundRect(progressRect, cornerRadius, cornerRadius, progressPaint)
            }
        }
        thumbDrawable?.draw(canvas)
    }

    // --- Getters and Setters ---
    fun getProgress(): Int = currentProgress

    fun setProgress(progress: Int, fromUser: Boolean = false) { // Added fromUser for clarity
        val newProgress = progress.coerceIn(0, maxProgress)
        val progressChanged = newProgress != currentProgress
        currentProgress = newProgress

        // If not dragging, or if progress changed programmatically, update thumb to match progress
        if (!isDraggingThumb || !fromUser) {
            updateThumbCoordinatesAndBounds(calculateThumbXFromProgress(currentProgress))
        }
        // If dragging, thumb position is handled by onTouchEvent for smoother visual tracking.
        // It will snap to progress on ACTION_UP.

        if (progressChanged) {
            invalidate()
            // listener?.onProgressChanged(this, currentProgress, fromUser)
        } else if (fromUser && !isDraggingThumb) { // e.g. click to seek, ensure redraw even if progress value is same
            invalidate()
        }
    }
    // ... (other getters/setters: getMax, setMax, setColor, setHeight, setCornerRadius, setThumb, etc. remain largely the same)
    fun getMax(): Int = maxProgress
    fun setMax(max: Int) {
        val newMax = max.coerceAtLeast(1)
        if (newMax != maxProgress) {
            maxProgress = newMax
            // Re-coerce current progress with the new max
            val oldProgress = currentProgress
            currentProgress = currentProgress.coerceIn(0, maxProgress)
            if(currentProgress != oldProgress || !isDraggingThumb){ // Update thumb if progress changed or not dragging
                updateThumbCoordinatesAndBounds(calculateThumbXFromProgress(currentProgress))
            }
            invalidate()
        }
    }
    fun setProgressColor(color: Int) { progressColor = color; progressPaint.color = progressColor; invalidate() }
    fun setTrackColor(color: Int) { trackColor = color; trackPaint.color = trackColor; invalidate() }
    fun setProgressBarHeight(heightPx: Float) { if (heightPx >= 0) { progressBarHeight = heightPx; requestLayout(); invalidate() } }
    fun setCornerRadius(radiusPx: Float) { cornerRadius = radiusPx.coerceAtLeast(0f); invalidate() }

    fun setThumb(drawable: Drawable?) {
        thumbDrawable = drawable
        // Use XML/setter values if available, otherwise intrinsic
        val newThumbW = typedArray?.getDimensionPixelSize(R.styleable.CustomProgressBar_cpb_thumbWidth, drawable?.intrinsicWidth ?: 0) ?: (drawable?.intrinsicWidth ?: 0)
        val newThumbH = typedArray?.getDimensionPixelSize(R.styleable.CustomProgressBar_cpb_thumbHeight, drawable?.intrinsicHeight ?: 0) ?: (drawable?.intrinsicHeight ?: 0)
        if (thumbWidth == 0 || thumbDrawable == null) thumbWidth = newThumbW // Only set if not already set or drawable changed
        if (thumbHeight == 0 || thumbDrawable == null) thumbHeight = newThumbH

        applyThumbTint()
        updateThumbCoordinatesAndBounds(calculateThumbXFromProgress(currentProgress))
        requestLayout()
        invalidate()
    }
    // Store TypedArray to access attributes in setThumb if needed, or re-evaluate attribute logic
    private var typedArray: android.content.res.TypedArray? = null // Example, better to handle in init

    fun setThumbResource(resId: Int) { setThumb(if (resId != 0) ContextCompat.getDrawable(context, resId) else null) }
    fun setThumbTint(color: Int?) { thumbTint = color; applyThumbTint(); invalidate() }


    // --- Touch Handling ---
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isUserSeekable) {
            return super.onTouchEvent(event)
        }

        val x = event.x
        val y = event.y

        // --- Thumb Dragging Logic ---
        if (isThumbDraggable && thumbDrawable != null) {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    thumbDrawable?.bounds?.let { bounds ->
                        val slopRatio = 0.7f
                        val actualThumbVisualWidth = if (thumbWidth > 0) thumbWidth.toFloat() else bounds.width().toFloat()
                        val actualThumbVisualHeight = if (thumbHeight > 0) thumbHeight.toFloat() else bounds.height().toFloat()

                        val touchSlopHorizontal = (if (actualThumbVisualWidth > 0) actualThumbVisualWidth else convertDpToPixel(20f, context)) * slopRatio
                        val touchSlopVertical = (if (actualThumbVisualHeight > 0) actualThumbVisualHeight else convertDpToPixel(20f, context)) * slopRatio

                        val extendedBounds = RectF(
                            bounds.left - touchSlopHorizontal,
                            bounds.top - touchSlopVertical,
                            bounds.right + touchSlopHorizontal,
                            bounds.bottom + touchSlopVertical
                        )
                        if (extendedBounds.contains(x, y)) {
                            isDraggingThumb = true
                            parent?.requestDisallowInterceptTouchEvent(true)
                            // Update visual thumb position directly to touch point
                            val constrainedX = x.coerceIn(paddingLeft.toFloat(), (width - paddingRight).toFloat())
                            updateThumbCoordinatesAndBounds(constrainedX)
                            // Also update logical progress
                            updateProgressFromTouchX(constrainedX, true, true) // isDragging = true
                            invalidate()
                            // listener?.onStartTrackingTouch(this)
                            Log.d(TAG, "Thumb ACTION_DOWN: Accepted drag. Visual thumbX: $thumbX")
                            return true
                        }
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDraggingThumb) {
                        // Update visual thumb position directly to touch point
                        val constrainedX = x.coerceIn(paddingLeft.toFloat(), (width - paddingRight).toFloat())
                        updateThumbCoordinatesAndBounds(constrainedX)
                        // Also update logical progress
                        updateProgressFromTouchX(constrainedX, true, true) // isDragging = true
                        invalidate()
                        // Log.d(TAG, "Thumb ACTION_MOVE: Visual thumbX: $thumbX, Progress: $currentProgress")
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingThumb) {
                        isDraggingThumb = false
                        parent?.requestDisallowInterceptTouchEvent(false)
                        // Final update of progress based on last touch
                        val constrainedX = x.coerceIn(paddingLeft.toFloat(), (width - paddingRight).toFloat())
                        updateProgressFromTouchX(constrainedX, true, false) // isDragging = false
                        // Snap thumb to the final progress value
                        updateThumbCoordinatesAndBounds(calculateThumbXFromProgress(currentProgress))
                        performClick()
                        invalidate()
                        // listener?.onStopTrackingTouch(this)
                        Log.d(TAG, "Thumb ACTION_UP/CANCEL: Ended drag. Snapped thumbX: $thumbX, Progress: $currentProgress")
                        return true
                    }
                }
            }
        }

        // --- Click-to-Seek Logic (Track Click) ---
        if (!isDraggingThumb) { // Process only if not dragging
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP -> {
                    val trackLeftBound = paddingLeft.toFloat()
                    val trackRightBound = (width - paddingRight).toFloat()
                    val contentDrawableHeight = height - paddingTop - paddingBottom
                    if (contentDrawableHeight <=0) return super.onTouchEvent(event)
                    val progressBarCenterY = paddingTop + contentDrawableHeight / 2f
                    val targetMinVerticalTouchPx = convertDpToPixel(DEFAULT_MIN_VERTICAL_TOUCH_DP, context)
                    val effectiveVerticalTouchHeight = min(max(progressBarHeight, targetMinVerticalTouchPx), contentDrawableHeight.toFloat())
                    var hitAreaTop = progressBarCenterY - effectiveVerticalTouchHeight / 2f
                    var hitAreaBottom = progressBarCenterY + effectiveVerticalTouchHeight / 2f
                    hitAreaTop = max(paddingTop.toFloat(), hitAreaTop)
                    hitAreaBottom = min((height - paddingBottom).toFloat(), hitAreaBottom)

                    if (x >= trackLeftBound && x <= trackRightBound && y >= hitAreaTop && y <= hitAreaBottom) {
                        if (event.action == MotionEvent.ACTION_DOWN) {
                            updateProgressFromTouchX(x, true, false) // fromUser=true, isDragging=false
                            // Thumb will be updated by setProgress calling updateThumbCoordinatesAndBounds
                            // No need to call invalidate() here as setProgress will do it if progress changes
                            Log.d(TAG, "TrackClick ACTION_DOWN: Accepted. Progress: $currentProgress")
                            return true
                        } else if (event.action == MotionEvent.ACTION_UP) {
                            // Progress already updated on ACTION_DOWN for track click
                            performClick()
                            Log.d(TAG, "TrackClick ACTION_UP: Accepted.")
                            return true
                        }
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun MotionEvent.actionToString(): String { /* ... same as before ... */
        return when (action) {
            MotionEvent.ACTION_DOWN -> "DOWN"
            MotionEvent.ACTION_MOVE -> "MOVE"
            MotionEvent.ACTION_UP -> "UP"
            MotionEvent.ACTION_CANCEL -> "CANCEL"
            else -> action.toString()
        }
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /**
     * Updates currentProgress based on a touch X-coordinate.
     * @param touchX The raw touch X coordinate.
     * @param fromUser True if the change is from user interaction.
     * @param isCurrentlyDragging Helps setProgress decide if it should snap the thumb.
     */
    private fun updateProgressFromTouchX(touchX: Float, fromUser: Boolean, isCurrentlyDragging: Boolean) {
        val effectiveWidth = width - paddingLeft - paddingRight
        if (effectiveWidth <= 0) return

        // Calculate progress based on the touchX relative to the drawable area
        val progressRatio = (touchX - paddingLeft) / effectiveWidth.toFloat()
        val newProgress = (progressRatio.coerceIn(0f, 1f) * maxProgress).roundToInt()

        // Use a temporary variable for isDragging state within setProgress if needed
        // For now, setProgress is simplified.
        val oldProgress = currentProgress
        currentProgress = newProgress.coerceIn(0, maxProgress) // Ensure it's within bounds

        if (currentProgress != oldProgress || (fromUser && !isCurrentlyDragging)) { // If progress changed, or if it's a click-to-seek
            // If dragging, setProgress won't snap thumb; visual thumb is already updated.
            // If not dragging (e.g., track click), setProgress will snap thumb.
            if (!isCurrentlyDragging) { // For track clicks or programmatic changes
                updateThumbCoordinatesAndBounds(calculateThumbXFromProgress(currentProgress))
            }
            invalidate()
            // listener?.onProgressChanged(this, currentProgress, fromUser)
        }
    }


    // --- Public methods to control seekability programmatically ---
    fun setUserSeekable(seekable: Boolean) { /* ... same as before ... */
        isUserSeekable = seekable
        if (!isUserSeekable) { isThumbDraggable = false }
        invalidate()
    }
    fun isUserSeekable(): Boolean = isUserSeekable

    fun setIsThumbDraggable(draggable: Boolean) { /* ... same as before ... */
        isThumbDraggable = draggable && isUserSeekable
        invalidate()
    }
    fun isThumbDraggable(): Boolean = isThumbDraggable && isUserSeekable

    /*
    interface OnProgressChangeListener { ... }
    private var listener: OnProgressChangeListener? = null
    fun setOnProgressChangeListener(listener: OnProgressChangeListener?) { ... }
    */
}

