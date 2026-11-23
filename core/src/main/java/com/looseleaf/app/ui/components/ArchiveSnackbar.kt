package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.looseleaf.app.theme.ArchiveShadows
import com.looseleaf.app.theme.ArchiveShapes
import com.looseleaf.app.theme.ArchiveTextStyles
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 轻提示数据
 */
data class ArchiveSnackbarData(
    val message: String,
    val actionLabel: String? = null,
    val onActionClick: (() -> Unit)? = null
)

@Composable
fun ArchiveSnackbar(
    data: ArchiveSnackbarData,
    modifier: Modifier = Modifier,
    onDismiss: (() -> Unit)? = null
) {
    ArchiveSnackbar(
        message = data.message,
        actionLabel = data.actionLabel,
        onActionClick = {
            data.onActionClick?.invoke()
            onDismiss?.invoke()
        },
        modifier = modifier
    )
}

/**
 * 底部轻提示 (ArchiveSnackbar)
 * 反相墨色胶囊：左侧说明，右侧反相朱砂动作，悬于底部导航之上。
 */
@Composable
fun ArchiveSnackbar(
    message: String,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = ArchiveShadows.floatingLayerElevation,
                shape = ArchiveShapes.lg,
                spotColor = colors.frameShadowSpot,
                ambientColor = colors.frameShadowAmbient
            )
            .clip(ArchiveShapes.lg)
            .background(colors.inverseSurface)
            .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = ArchiveTextStyles.body,
            color = colors.inverseInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp)
        )
        if (!actionLabel.isNullOrBlank() && onActionClick != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = actionLabel,
                style = ArchiveTextStyles.label,
                fontWeight = FontWeight.SemiBold,
                color = colors.inverseAccent,
                modifier = Modifier
                    .clip(ArchiveShapes.full)
                    .clickable(role = Role.Button, onClick = onActionClick)
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
