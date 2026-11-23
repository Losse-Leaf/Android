package com.looseleaf.app.ui.components

import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.LightArchiveColors

/**
 * 散页 · Folio 线性图形集
 *
 * 全局禁止用文字符号（下三角、对勾、退格符、书名号箭头等）冒充图标：
 * 它们在不同系统字体下会被渲染成彩色表情或粗细不一的字形。
 * 这里用 24 视口、1.6 线宽、圆头圆角统一绘制，配合 Icon(tint) 着色。
 */
object FolioGlyphs {

    private fun stroked(
        name: String,
        width: Float = 1.6f,
        block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit
    ): ImageVector = ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = null,
            stroke = SolidColor(LightArchiveColors.inkPrimary),
            strokeLineWidth = width,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block
        )
    }.build()

    /** 向下展开 */
    val ChevronDown: ImageVector by lazy {
        stroked("FolioChevronDown") {
            moveTo(6.5f, 9.5f)
            lineTo(12f, 15f)
            lineTo(17.5f, 9.5f)
        }
    }

    /** 向上收起 */
    val ChevronUp: ImageVector by lazy {
        stroked("FolioChevronUp") {
            moveTo(6.5f, 14.5f)
            lineTo(12f, 9f)
            lineTo(17.5f, 14.5f)
        }
    }

    /** 向右进入 */
    val ChevronRight: ImageVector by lazy {
        stroked("FolioChevronRight") {
            moveTo(9.5f, 6.5f)
            lineTo(15f, 12f)
            lineTo(9.5f, 17.5f)
        }
    }

    /** 向左返回 */
    val ChevronLeft: ImageVector by lazy {
        stroked("FolioChevronLeft") {
            moveTo(14.5f, 6.5f)
            lineTo(9f, 12f)
            lineTo(14.5f, 17.5f)
        }
    }

    /** 选中对勾 */
    val Check: ImageVector by lazy {
        stroked("FolioCheck", width = 1.9f) {
            moveTo(5.5f, 12.5f)
            lineTo(10f, 17f)
            lineTo(18.5f, 7.5f)
        }
    }

    /** 加号 */
    val Plus: ImageVector by lazy {
        stroked("FolioPlus", width = 1.8f) {
            moveTo(12f, 5.5f)
            lineTo(12f, 18.5f)
            moveTo(5.5f, 12f)
            lineTo(18.5f, 12f)
        }
    }

    /** 关闭 */
    val Close: ImageVector by lazy {
        stroked("FolioClose", width = 1.7f) {
            moveTo(6.5f, 6.5f)
            lineTo(17.5f, 17.5f)
            moveTo(17.5f, 6.5f)
            lineTo(6.5f, 17.5f)
        }
    }

    /** 退格：左尖的标签框 + 叉 */
    val Backspace: ImageVector by lazy {
        stroked("FolioBackspace", width = 1.6f) {
            moveTo(9f, 5.5f)
            lineTo(19f, 5.5f)
            curveTo(20.1f, 5.5f, 21f, 6.4f, 21f, 7.5f)
            lineTo(21f, 16.5f)
            curveTo(21f, 17.6f, 20.1f, 18.5f, 19f, 18.5f)
            lineTo(9f, 18.5f)
            lineTo(3f, 12f)
            close()
            moveTo(11.5f, 9.5f)
            lineTo(16.5f, 14.5f)
            moveTo(16.5f, 9.5f)
            lineTo(11.5f, 14.5f)
        }
    }

    /** 同步：两段圆弧箭头 */
    val Sync: ImageVector by lazy {
        stroked("FolioSync", width = 1.6f) {
            moveTo(19f, 9.5f)
            curveTo(17.9f, 6.6f, 15.2f, 4.75f, 12f, 4.75f)
            curveTo(8.4f, 4.75f, 5.4f, 7.3f, 4.85f, 10.7f)
            moveTo(19f, 5f)
            lineTo(19f, 9.5f)
            lineTo(14.5f, 9.5f)
            moveTo(5f, 14.5f)
            curveTo(6.1f, 17.4f, 8.8f, 19.25f, 12f, 19.25f)
            curveTo(15.6f, 19.25f, 18.6f, 16.7f, 19.15f, 13.3f)
            moveTo(5f, 19f)
            lineTo(5f, 14.5f)
            lineTo(9.5f, 14.5f)
        }
    }

    /** 搜索 */
    val Search: ImageVector by lazy {
        stroked("FolioSearch", width = 1.6f) {
            moveTo(10.75f, 4.75f)
            curveTo(14.06f, 4.75f, 16.75f, 7.44f, 16.75f, 10.75f)
            curveTo(16.75f, 14.06f, 14.06f, 16.75f, 10.75f, 16.75f)
            curveTo(7.44f, 16.75f, 4.75f, 14.06f, 4.75f, 10.75f)
            curveTo(4.75f, 7.44f, 7.44f, 4.75f, 10.75f, 4.75f)
            close()
            moveTo(15.2f, 15.2f)
            lineTo(19.5f, 19.5f)
        }
    }

    /** 筛选：三条递短的横线 */
    val Filter: ImageVector by lazy {
        stroked("FolioFilter", width = 1.6f) {
            moveTo(4.5f, 7f)
            lineTo(19.5f, 7f)
            moveTo(7.5f, 12f)
            lineTo(16.5f, 12f)
            moveTo(10.5f, 17f)
            lineTo(13.5f, 17f)
        }
    }

    /** 设置：两条滑杆 */
    val Tune: ImageVector by lazy {
        stroked("FolioTune", width = 1.6f) {
            moveTo(4.5f, 8f)
            lineTo(12.5f, 8f)
            moveTo(17.5f, 8f)
            lineTo(19.5f, 8f)
            moveTo(15f, 5.75f)
            curveTo(16.24f, 5.75f, 17.25f, 6.76f, 17.25f, 8f)
            curveTo(17.25f, 9.24f, 16.24f, 10.25f, 15f, 10.25f)
            curveTo(13.76f, 10.25f, 12.75f, 9.24f, 12.75f, 8f)
            curveTo(12.75f, 6.76f, 13.76f, 5.75f, 15f, 5.75f)
            close()
            moveTo(4.5f, 16f)
            lineTo(6.5f, 16f)
            moveTo(11.5f, 16f)
            lineTo(19.5f, 16f)
            moveTo(9f, 13.75f)
            curveTo(10.24f, 13.75f, 11.25f, 14.76f, 11.25f, 16f)
            curveTo(11.25f, 17.24f, 10.24f, 18.25f, 9f, 18.25f)
            curveTo(7.76f, 18.25f, 6.75f, 17.24f, 6.75f, 16f)
            curveTo(6.75f, 14.76f, 7.76f, 13.75f, 9f, 13.75f)
            close()
        }
    }

    /** 落笔：斜置的笔杆 + 笔尖 */
    val Pen: ImageVector by lazy {
        stroked("FolioPen", width = 1.6f) {
            moveTo(14.5f, 5.5f)
            lineTo(18.5f, 9.5f)
            moveTo(16.5f, 3.5f)
            lineTo(20.5f, 7.5f)
            lineTo(9f, 19f)
            lineTo(4f, 20f)
            lineTo(5f, 15f)
            close()
        }
    }

    /** 时钟（往年今日） */
    val Clock: ImageVector by lazy {
        stroked("FolioClock", width = 1.6f) {
            moveTo(12f, 4.5f)
            curveTo(16.14f, 4.5f, 19.5f, 7.86f, 19.5f, 12f)
            curveTo(19.5f, 16.14f, 16.14f, 19.5f, 12f, 19.5f)
            curveTo(7.86f, 19.5f, 4.5f, 16.14f, 4.5f, 12f)
            curveTo(4.5f, 7.86f, 7.86f, 4.5f, 12f, 4.5f)
            close()
            moveTo(12f, 8f)
            lineTo(12f, 12f)
            lineTo(14.75f, 13.75f)
        }
    }

    /** 地点：针形 */
    val Pin: ImageVector by lazy {
        stroked("FolioPin", width = 1.6f) {
            moveTo(12f, 20f)
            curveTo(12f, 20f, 18f, 14.6f, 18f, 10f)
            curveTo(18f, 6.69f, 15.31f, 4f, 12f, 4f)
            curveTo(8.69f, 4f, 6f, 6.69f, 6f, 10f)
            curveTo(6f, 14.6f, 12f, 20f, 12f, 20f)
            close()
            moveTo(12f, 8.25f)
            curveTo(12.97f, 8.25f, 13.75f, 9.03f, 13.75f, 10f)
            curveTo(13.75f, 10.97f, 12.97f, 11.75f, 12f, 11.75f)
            curveTo(11.03f, 11.75f, 10.25f, 10.97f, 10.25f, 10f)
            curveTo(10.25f, 9.03f, 11.03f, 8.25f, 12f, 8.25f)
            close()
        }
    }

    /** 警示：圆中竖线 */
    val Alert: ImageVector by lazy {
        stroked("FolioAlert", width = 1.6f) {
            moveTo(12f, 4.5f)
            curveTo(16.14f, 4.5f, 19.5f, 7.86f, 19.5f, 12f)
            curveTo(19.5f, 16.14f, 16.14f, 19.5f, 12f, 19.5f)
            curveTo(7.86f, 19.5f, 4.5f, 16.14f, 4.5f, 12f)
            curveTo(4.5f, 7.86f, 7.86f, 4.5f, 12f, 4.5f)
            close()
            moveTo(12f, 8f)
            lineTo(12f, 12.5f)
            moveTo(12f, 15.75f)
            lineTo(12f, 16f)
        }
    }

    /** 锁 */
    val Lock: ImageVector by lazy {
        stroked("FolioLock", width = 1.6f) {
            moveTo(6.5f, 10.5f)
            lineTo(17.5f, 10.5f)
            curveTo(18.33f, 10.5f, 19f, 11.17f, 19f, 12f)
            lineTo(19f, 18.5f)
            curveTo(19f, 19.33f, 18.33f, 20f, 17.5f, 20f)
            lineTo(6.5f, 20f)
            curveTo(5.67f, 20f, 5f, 19.33f, 5f, 18.5f)
            lineTo(5f, 12f)
            curveTo(5f, 11.17f, 5.67f, 10.5f, 6.5f, 10.5f)
            close()
            moveTo(8.5f, 10.5f)
            lineTo(8.5f, 7.5f)
            curveTo(8.5f, 5.57f, 10.07f, 4f, 12f, 4f)
            curveTo(13.93f, 4f, 15.5f, 5.57f, 15.5f, 7.5f)
            lineTo(15.5f, 10.5f)
            moveTo(12f, 14f)
            lineTo(12f, 16.5f)
        }
    }

    /** 更多：三点（用短线段绘制圆点） */
    val More: ImageVector by lazy {
        stroked("FolioMore", width = 2.4f) {
            moveTo(6f, 12f)
            lineTo(6.01f, 12f)
            moveTo(12f, 12f)
            lineTo(12.01f, 12f)
            moveTo(18f, 12f)
            lineTo(18.01f, 12f)
        }
    }
}
