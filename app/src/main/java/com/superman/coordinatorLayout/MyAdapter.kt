package com.superman.coordinatorLayout // 使用你项目中的正确包名

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.superman.drilldemo.R // 确保 R 文件被正确导入

class MyAdapter(
    private val parentItems: MutableList<ParentItem>,
    private val context: Context // 需要 Context 来初始化 LinearLayoutManager
) : RecyclerView.Adapter<MyAdapter.ViewHolder>() {

    // 用于保存每个内部 RecyclerView 的状态，防止滚动时重复创建 Adapter 和 LayoutManager
    private val viewPool = RecyclerView.RecycledViewPool()

    // ViewHolder 类，用于缓存列表项视图中的子视图引用
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.item_text_view)
        val innerRecyclerView: RecyclerView = itemView.findViewById(R.id.inner_recycler_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // 使用包含横向 RecyclerView 的布局
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_string, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val parentItem = parentItems[position]
        holder.titleTextView.text = parentItem.title

        // 配置内部的横向 RecyclerView
        val innerLayoutManager = LinearLayoutManager(
            holder.innerRecyclerView.context, // 使用 holder 的 context
            LinearLayoutManager.HORIZONTAL,
            false
        )
        // 优化：LayoutManager 也可以只设置一次，如果它们不依赖于 position 的特定数据
        // holder.innerRecyclerView.layoutManager = innerLayoutManager (如果在 XML 设置了，这里可以不设置)

        // 确保内部 RecyclerView 的 LayoutManager 是正确的
        if (holder.innerRecyclerView.layoutManager == null) {
            holder.innerRecyclerView.layoutManager = innerLayoutManager
        }
        (holder.innerRecyclerView.layoutManager as? LinearLayoutManager)?.orientation = LinearLayoutManager.HORIZONTAL


        val innerAdapter = InnerHorizontalAdapter(parentItem.innerItemList)
        holder.innerRecyclerView.adapter = innerAdapter

        // 使用 RecycledViewPool 来优化嵌套 RecyclerView 的性能
        holder.innerRecyclerView.setRecycledViewPool(viewPool)

        // 如果在 XML 中没有设置 LayoutManager，可以在这里设置
        // if (holder.innerRecyclerView.layoutManager == null) {
        //     holder.innerRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        // }
    }

    override fun getItemCount(): Int {
        return parentItems.size
    }

    // Helper 方法来更新数据
    fun updateData(newParentItems: List<ParentItem>) {
        parentItems.clear()
        parentItems.addAll(newParentItems)
        notifyDataSetChanged() // 或者使用 DiffUtil 进行更高效的更新
    }

    fun addItem(item: ParentItem, position: Int = parentItems.size) {
        parentItems.add(position, item)
        notifyItemInserted(position)
    }

    fun clearItems() {
        val size = parentItems.size
        parentItems.clear()
        notifyItemRangeRemoved(0, size)
    }
}
