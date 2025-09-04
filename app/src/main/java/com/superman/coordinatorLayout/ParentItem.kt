package com.superman.coordinatorLayout

/**
 *
 * @author 张学阳
 * @date : 2025/9/3
 * @description:
 */

// ParentItem 现在包含一个标题和一组内部项的数据
data class ParentItem(
    val title: String,
    val innerItemList: List<InnerItem> // 用于内部横向 RecyclerView 的数据
)