package com.topjohnwu.magisk.ui.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 自定义超级用户图标：实心盾牌 + "#" 号镂空
 * 盾牌路径来自 Magisk ic_superuser_filled_md2.xml
 * "#" 路径来自 Magisk ic_superuser.xml（core），缩放 0.5x 并居中于盾牌内
 * 使用 EvenOdd 填充规则实现镂空效果
 */
val SuperuserIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "SuperuserIcon",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            // 实心盾牌
            moveTo(12f, 1f)
            lineTo(3f, 5f)
            verticalLineTo(11f)
            curveTo(3f, 16.55f, 6.84f, 21.74f, 12f, 23f)
            curveTo(17.16f, 21.74f, 21f, 16.55f, 21f, 11f)
            verticalLineTo(5f)
            lineTo(12f, 1f)
            close()

            // "#" 号外轮廓（缩放 0.5x 居中: x' = 0.5*x + 6, y' = 0.5*y + 6.5）
            moveTo(8.705f, 17f)
            lineTo(9.06f, 15f)
            horizontalLineTo(7.06f)
            lineTo(7.235f, 14f)
            horizontalLineTo(9.235f)
            lineTo(9.765f, 11f)
            horizontalLineTo(7.765f)
            lineTo(7.94f, 10f)
            horizontalLineTo(9.94f)
            lineTo(10.295f, 8f)
            horizontalLineTo(11.295f)
            lineTo(10.94f, 10f)
            horizontalLineTo(13.94f)
            lineTo(14.295f, 8f)
            horizontalLineTo(15.295f)
            lineTo(14.94f, 10f)
            horizontalLineTo(16.94f)
            lineTo(16.765f, 11f)
            horizontalLineTo(14.765f)
            lineTo(14.235f, 14f)
            horizontalLineTo(16.235f)
            lineTo(16.06f, 15f)
            horizontalLineTo(14.06f)
            lineTo(13.705f, 17f)
            horizontalLineTo(12.705f)
            lineTo(13.06f, 15f)
            horizontalLineTo(10.06f)
            lineTo(9.705f, 17f)
            horizontalLineTo(8.705f)
            close()

            // "#" 号中心菱形孔洞
            moveTo(10.765f, 11f)
            lineTo(10.235f, 14f)
            horizontalLineTo(13.235f)
            lineTo(13.765f, 11f)
            horizontalLineTo(10.765f)
            close()
        }
    }.build()
}
