package com.superman.drilldemo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * @author 张学阳
 * @date : 2025/8/14
 * @description:https://www.cnblogs.com/skiyoumi/p/13912330.html
 */
public class HalfProgressBar extends View {
    private String TAG = "HalfProgressBar";

    //进度条底色
    private int bankBackground;
    //进度颜色
    private int progressBackground;
    //进度条宽度
    private int progressBarHeight;
    //原点宽度
    private int dotHeight;
    //圆点颜色
    private int dotBackground;
    //进度条最大值
    private float maxNum;
    //进度条当前进度
    private float presentNum;

    //画笔
    private Paint barPaint;
    private Paint progressPaint;
    private Paint dotPaint;

    //滑动时的位置
    private float moveX, moveY;
    //点击的位置
    private float downX, downY;

    //小圆点坐标
    private float x = 0, y = 0;

    private ProgressOfTheInter progressOfTheInter;

    //向外暴漏方法实现拖动进度接口
    public void setProgressOfTheInter(ProgressOfTheInter progressOfTheInter) {
        this.progressOfTheInter = progressOfTheInter;
    }

    //向外暴露方法设置进度条最大值
    public void setMaxNum(int maxNum) {
        this.maxNum = maxNum;
    }

    //向外暴露方法设置进度
    public void setPresentNum(int presentNum) {
        this.presentNum = presentNum;
        //重绘
        invalidate();
    }

    //向外暴漏方法获取当前进度
    public float getPresentNum() {
        return presentNum;
    }

    //new时调用
    public HalfProgressBar(Context context) {
        this(context, null);
    }

    //xml文件调用
    public HalfProgressBar(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public HalfProgressBar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    //初始化变量
    private void init() {
        bankBackground = getResources().getColor(R.color.color1);
        progressBackground = getResources().getColor(R.color.color2);
        dotBackground = getResources().getColor(R.color.black);
        progressBarHeight = 10;
        dotHeight = 10;
        maxNum = 100;
        presentNum = 0;
        barPaint = new Paint();
        progressPaint = new Paint();
        dotPaint = new Paint();
    }

    //    宽高测量
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        //拿到宽度和高度
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(width == height ? height : width, width == height ? height : width);
    }

//    画笔

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        //画进度条
        onDrawProgressBar(canvas);
        //画进度
        onDrawProgress(canvas);
        //画圆点
        onDrawdDot(canvas);
    }

    //画整个进度条
    private void onDrawProgressBar(Canvas canvas) {
        //定义区域
        RectF rectF = new RectF(progressBarHeight, progressBarHeight, getWidth() - progressBarHeight, getHeight() - progressBarHeight);
        barPaint.setAntiAlias(true);
        barPaint.setStrokeWidth(progressBarHeight);//设置画笔宽度
        barPaint.setColor(bankBackground);//设置颜色
        barPaint.setStyle(Paint.Style.STROKE);//设置为实心画笔
        barPaint.setStrokeCap(Paint.Cap.ROUND);
        barPaint.setAntiAlias(true);
        canvas.drawArc(rectF, 360, 180, false, barPaint);
    }

    //画进度
    private void onDrawProgress(Canvas canvas) {
        if (presentNum > maxNum)
            presentNum = maxNum;
        //定义区域
        RectF rectF = new RectF(progressBarHeight, progressBarHeight, getWidth() - progressBarHeight, getHeight() - progressBarHeight);
        progressPaint.setAntiAlias(true);
        progressPaint.setStrokeWidth(progressBarHeight);//设置画笔宽度
        progressPaint.setColor(progressBackground);//设置颜色
        progressPaint.setStyle(Paint.Style.STROKE);//设置为实心画笔
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setAntiAlias(true);
        canvas.drawArc(rectF, 180, -((presentNum / maxNum) * 180), false, progressPaint);
    }

    //画圆点
    private void onDrawdDot(Canvas canvas) {
        dotPaint.setAntiAlias(true);
        dotPaint.setStrokeWidth(progressBarHeight);//设置画笔宽度
        dotPaint.setColor(dotBackground);//设置颜色
        dotPaint.setStyle(Paint.Style.FILL);//设置为实心画笔
        dotPaint.setStrokeCap(Paint.Cap.ROUND);
        dotPaint.setAntiAlias(true);
        //判断角度是否大于90度
        //通过三角函数计算x,y坐标
        if ((Math.cos(Math.toRadians((presentNum / maxNum) * 180))) * (getWidth() / 2 - progressBarHeight / 2) >= 0) {
            x = (float) (getWidth() / 2 - (Math.cos(Math.toRadians((presentNum / maxNum) * 180))) * (getWidth() / 2 - progressBarHeight / 2)) + progressBarHeight / 2;
        } else {
            x = (float) (getWidth() / 2 - (Math.cos(Math.toRadians((presentNum / maxNum) * 180))) * (getWidth() / 2 - progressBarHeight / 2)) - progressBarHeight / 2;
        }
        y = (float) ((getHeight() / 2 - progressBarHeight / 2) + (Math.sin(Math.toRadians((presentNum / maxNum) * 180))) * (getHeight() / 2 - progressBarHeight / 2));
        canvas.drawCircle(x, y, (float) dotHeight, dotPaint);
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            //手指按住
            case MotionEvent.ACTION_DOWN:
                downX = event.getX();
                downY = event.getY();
                //判断按下的位置是否在小圆点附件15像素位置
                if (rangeInDefined((int) downX, (int) x - 15, (int) x + 15) && rangeInDefined((int) downY, (int) y - 15, (int) y + 15))
                    return true;
                else
                    return false;
                //手指移动
            case MotionEvent.ACTION_MOVE:
                //获取当前触摸点，赋值给当前进度
                setMotionProgress(event);
                return true;
            //手指松开
            case MotionEvent.ACTION_UP:
                if (progressOfTheInter != null)
                    progressOfTheInter.upProgress((int) presentNum);
                return false;
        }
        return true;
    }

    //拖动时改变进度值
    private void setMotionProgress(MotionEvent event) {
        //获取当前触摸点，赋值给当前进度
        moveX = (int) event.getX();
        moveY = (int) event.getY();
        //圆心坐标（(getWidth()/2 ,(getHeight()/2）
        double longX = Math.pow((moveX - getWidth() / 2), 2);
        double longY = Math.pow((moveY - getHeight() / 2), 2);
        double longDuibian = moveY - (getWidth() / 2);
        double asin = Math.asin(longDuibian / (Math.sqrt(longX + longY)));
        //弧度转化为角度
        double angle = Math.toDegrees(asin);
        if (moveX > getWidth() / 2)
            angle = 180 - angle;
        if (angle >= 180)
            angle = 180;
        if (angle < 0)
            angle = 0;
        //角度等比例转换为进度
        presentNum = (int) (angle * maxNum) / 180;
        //progressOfTheInter接口是否为空
        if (progressOfTheInter != null)
            progressOfTheInter.moveProgress((int) presentNum);
        invalidate();
    }

    private boolean rangeInDefined(int current, int min, int max) {
        return Math.max(min, current) == Math.min(current, max);
    }

    public interface ProgressOfTheInter {
        //获取拖动时的进度
        void moveProgress(int index);

        //松开时进度
        void upProgress(int index);
    }
}
