package com.superman.coordinatorLayout

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.appbar.AppBarLayout
// import androidx.recyclerview.widget.RecyclerView // RecyclerView 已通过 vb.recyclerView 访问
// import androidx.swiperefreshlayout.widget.SwipeRefreshLayout // SwipeRefreshLayout 已通过 vb.swipeRefreshLayout 访问
import com.google.android.material.snackbar.Snackbar // 导入 Snackbar
import com.superman.drilldemo.R
import com.superman.drilldemo.databinding.ActivityCoordinatorLayoutBinding

// ... 其他 import ...
import com.superman.coordinatorLayout.InnerItem // 导入 InnerItem
import com.superman.coordinatorLayout.MyAdapter
import com.superman.coordinatorLayout.ParentItem // 导入 ParentItem
import kotlin.math.abs
import kotlin.ranges.coerceIn
import kotlin.text.toFloat

class CoordinatorLayoutActivity : AppCompatActivity() {
    private lateinit var myAdapter: MyAdapter
    // 更新数据类型
    private val sampleParentData = mutableListOf<ParentItem>()

    // ... ViewBinding 等 ...
    private val binding: ActivityCoordinatorLayoutBinding by lazy {
        ActivityCoordinatorLayoutBinding.inflate(layoutInflater)
    }

    private fun setupAppBarLayoutListener() {
        // 创建一个 AppBarLayout.OnOffsetChangedListener 的匿名对象实例
        val appBarOffsetListener = AppBarLayout.OnOffsetChangedListener { appBarLayout, verticalOffset ->
            // verticalOffset: 0 (完全展开) 到 -totalScrollRange (完全折叠)
            val totalScrollRange = appBarLayout.totalScrollRange
            if (totalScrollRange == 0) {
                binding.headerImage.alpha = 1f // 如果没有滚动范围，保持完全不透明
                return@OnOffsetChangedListener
            }

            val scrollPercentage = abs(verticalOffset).toFloat() / totalScrollRange.toFloat()
            val newAlpha = 1.0f - scrollPercentage
            binding.headerImage.alpha = newAlpha.coerceIn(0f, 1f)

            // Log.d("AppBarLayout", "Offset: $verticalOffset, Alpha: ${headerImageView.alpha}")
        }
        // 将监听器添加到 AppBarLayout
        binding.appbar.addOnOffsetChangedListener(appBarOffsetListener)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val toolbar: Toolbar = binding.toolbar
        setSupportActionBar(toolbar)

        setupRecyclerView()
        setupSwipeRefreshLayout()

        loadInitialParentData() // 重命名方法以反映数据类型
        setupAppBarLayoutListener()
    }

    private fun setupRecyclerView() {
        // 初始化 MyAdapter，传递 this (Context)
        myAdapter = MyAdapter(sampleParentData, this)

        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@CoordinatorLayoutActivity)
            adapter = myAdapter
            // (可选) 进一步优化嵌套 RecyclerView 的滚动性能
            // setHasFixedSize(true) // 如果外部列表项大小固定
            //setItemViewCacheSize(5) // 调整缓存大小
        }
    }

    private fun setupSwipeRefreshLayout() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            refreshParentData() // 重命名方法
        }
        binding.swipeRefreshLayout.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            // ... 其他颜色
        )
    }

    private fun generateDummyInnerItems(count: Int, prefix: String): List<InnerItem> {
        val drawables = listOf(
            R.drawable.ic_launcher_background, // 替换为你的 drawable
            R.drawable.ic_pause_filled, // 替换为你的 drawable
            // 添加更多 drawable 资源 ID
        )
        return (1..count).map {
            InnerItem(drawables.random()) // 随机选择一个 drawable
        }
    }

    private fun loadInitialParentData() {
        binding.swipeRefreshLayout.isRefreshing = true
        Handler(Looper.getMainLooper()).postDelayed({
            if (isDestroyed || isFinishing) return@postDelayed

            if (sampleParentData.isEmpty()) { // 只在初次加载时填充
                val items = mutableListOf<ParentItem>()
                for (i in 1..10) {
                    items.add(
                        ParentItem(
                            title = "Parent Item $i",
                            innerItemList = generateDummyInnerItems((3..7).random(), "Inner $i -")
                        )
                    )
                }
                sampleParentData.addAll(items)
                myAdapter.notifyDataSetChanged() // 或者 myAdapter.updateData(items)
            }

            binding.swipeRefreshLayout.isRefreshing = false
            Snackbar.make(binding.recyclerView, "Parent data loaded", Snackbar.LENGTH_SHORT).show()
        }, 2000)
    }

    private fun refreshParentData() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (isDestroyed || isFinishing) return@postDelayed

            val newParentItem = ParentItem(
                title = "Refreshed Parent ${sampleParentData.size + 1}",
                innerItemList = generateDummyInnerItems((4..6).random(), "New Inner -")
            )
            myAdapter.addItem(newParentItem, 0) // 使用 MyAdapter 的 helper 方法
            binding.recyclerView.scrollToPosition(0)
            binding.swipeRefreshLayout.isRefreshing = false
            Snackbar.make(binding.recyclerView, "Parent data refreshed!", Snackbar.LENGTH_SHORT).show()
        }, 1500)
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context,CoordinatorLayoutActivity::class.java))
        }
    }
}

