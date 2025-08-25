package com.superman.drilldemo

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.util.UnstableApi
import com.superman.drilldemo.activity.TestProgressBarActivity
import com.superman.drilldemo.databinding.ActivityDemoBinding
import com.superman.drilldemo.play.SongMainActivity
import com.superman.drilldemo.play.SongMainActivity22
import com.superman.drilldemo.play.download.DownloadWithViewModelActivity

/**
 *
 * @author 张学阳
 * @date : 2025/8/16
 * @description:
 */
class DemoActivity:AppCompatActivity() {
    private val vb by lazy { ActivityDemoBinding.inflate(layoutInflater) }

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(vb.root)
        vb.btnCustomProgressBar.setOnClickListener {
            startActivity(Intent(this, TestProgressBarActivity::class.java))
        }
        vb.btnSongAct.setOnClickListener {
            startActivity(Intent(this, SongMainActivity::class.java))
        }
        vb.btnSongAct2.setOnClickListener {
//            startActivity(Intent(this, SongMainActivity22::class.java))
//            overridePendingTransition( R.anim.fade_in,  // 新 Activity 进入的动画 (enterAnimResId)
//                R.anim.fade_out)
            SongMainActivity22.start(this)
        }
        vb.btnDownload.setOnClickListener {
            DownloadWithViewModelActivity.start(this)
        }
    }
}