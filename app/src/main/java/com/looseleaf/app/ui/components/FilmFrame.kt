package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme

/**
 * 银盐暗房相纸装裱框 (.film-frame)
 * 严格还原相纸贴在手账本上的物理微隆起与哑光衬纸留白，
 * 底部展示经典底片机型/镜头元数据
 */
@Composable
fun FilmFrame(
    imageUrl: String,
    metadataText: String? = null,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 4f / 3f,
    onClick: (() -> Unit)? = null
) {
    val colors = LooseLeafTheme.colors
    val context = LocalContext.current
    val frameShape = RoundedCornerShape(2.dp)

    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            .memoryCacheKey(imageUrl)
            .diskCacheKey(imageUrl)
            .build()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (colors.isDark) 4.dp else 2.dp,
                shape = frameShape,
                ambientColor = colors.frameShadowAmbient,
                spotColor = colors.frameShadowSpot
            )
            .background(colors.archiveSubtle, frameShape)
            .border(0.5.dp, colors.borderDivider, frameShape)
            .padding(top = 10.dp, start = 10.dp, end = 10.dp, bottom = 8.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspectRatio)
                .background(colors.filmMatteBg)
                .clip(RoundedCornerShape(1.dp))
        ) {
            AsyncImage(
                model = imageRequest,
                contentDescription = metadataText ?: "Film Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!metadataText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = metadataText,
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = 10.sp,
                letterSpacing = 0.5.sp,
                color = colors.inkMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
