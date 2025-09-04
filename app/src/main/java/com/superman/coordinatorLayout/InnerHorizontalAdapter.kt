package com.superman.coordinatorLayout


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.superman.drilldemo.R // 确保 R 文件导入正确

// 假设内部 RecyclerView 显示简单的图片数据 (这里用 Int 代表 drawable 资源 ID)
data class InnerItem(val imageResId: Int, val id: String = java.util.UUID.randomUUID().toString())

class InnerHorizontalAdapter(private val innerItems: List<InnerItem>) :
    RecyclerView.Adapter<InnerHorizontalAdapter.InnerViewHolder>() {

    class InnerViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.inner_image_view)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): InnerViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_horizontal_image, parent, false)
        return InnerViewHolder(view)
    }

    override fun onBindViewHolder(holder: InnerViewHolder, position: Int) {
        val item = innerItems[position]
        holder.imageView.setImageResource(item.imageResId)
        // 你可以在这里为内部项设置点击监听等
    }

    override fun getItemCount(): Int = innerItems.size
}
