package com.looseleaf.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 生物识别门禁锁定遮罩层
 */
@Composable
internal fun BiometricLockOverlay(
    onRequestUnlock: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LooseLeafTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .background(colors.archivePaper)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(com.looseleaf.core.R.drawable.ic_lock),
                contentDescription = stringResource(R.string.main_activity_contentDescription_01),
                tint = colors.accentWarm,
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.main_activity_text_01),
                fontFamily = ArchiveFontFamilies.EditorialSerif,
                fontSize = 20.sp,
                color = colors.inkPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.main_activity_text_02),
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = 13.sp,
                color = colors.inkSecondary
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onRequestUnlock,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentWarm,
                    contentColor = colors.archivePaper
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    stringResource(R.string.main_activity_verify_biometrics),
                    fontFamily = ArchiveFontFamilies.EditorialSerif
                )
            }
        }
    }
}
