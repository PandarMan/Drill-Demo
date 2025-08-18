package com.superman.drilldemo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.superman.drilldemo.activity.TestProgressBarActivity
import com.superman.drilldemo.databinding.ActivityDemoBinding

/**
 *
 * @author 张学阳
 * @date : 2025/8/16
 * @description:
 */
class DemoActivity:AppCompatActivity() {
    private val vb by lazy { ActivityDemoBinding.inflate(layoutInflater) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(vb.root)
        vb.btnCustomProgressBar.setOnClickListener {
            startActivity(Intent(this, TestProgressBarActivity::class.java))
        }
        vb.btnSongAct.setOnClickListener {
            startActivity(Intent(this, SongMainActivity::class.java))
        }
    }
}