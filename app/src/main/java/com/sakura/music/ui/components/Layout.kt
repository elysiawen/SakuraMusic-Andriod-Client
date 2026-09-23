package com.sakura.music.ui.components

import androidx.compose.ui.unit.dp

/**
 * 列表底部的呼吸空间。
 *
 * 底部栏与迷你播放条的位置由根组件的 Scaffold 统一让出来——内容区本身已经被压缩到它们之上，
 * 所以这里只需要一点点余量，免得最后一行贴着边缘。
 */
val ListBottomPadding = 12.dp

/** 顶部内容与系统状态栏之间的固定间距（顶栏自己不加 insets）。 */
val ContentTopPadding = 8.dp
