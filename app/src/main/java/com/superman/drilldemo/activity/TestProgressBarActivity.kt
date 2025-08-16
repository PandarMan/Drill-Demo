package com.superman.drilldemo.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.superman.drilldemo.databinding.ActivityTestProgressBarBinding

/**
 *
 * @author 张学阳
 * @date : 2025/8/16
 * @description:
 */
class TestProgressBarActivity:AppCompatActivity() {
    private val vb by lazy { ActivityTestProgressBarBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(vb.root)

        vb.btnIncreaseProgress.setOnClickListener {
            var current = vb.myCustomProgressBar.getProgress()
            current += 10
            vb.myCustomProgressBar.setProgress(current)
        }

        vb.btnDecreaseProgress.setOnClickListener {
            var current = vb.myCustomProgressBar.getProgress()
            current -= 10
            vb.myCustomProgressBar.setProgress(current)
        }
    }
}