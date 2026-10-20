package com.looseleaf.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R

/** MOB-P1-25：相纸装裱框形状常量 */
private val FilmFrameShape = RoundedCornerShape(2.dp)

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
    // MOB-P1-25：形状常量
    val frameShape = FilmFrameShape

    // 0 = 加载中 / 1 = 成功 / 2 = 失败（MOB-P0-08：替代 SubcomposeAsyncImage 的 loading/error 槽）
    var loadPhase by remember(imageUrl) { mutableStateOf(0) }

    val imageRequest = remember(imageUrl) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .crossfade(true)
            // 不设 memoryCacheKey：Coil 会把目标尺寸并入缓存键，
            // 手动统一成 imageUrl 会让列表缩略图污染全屏大图的缓存（AOPT-04b）
            .diskCacheKey(imageUrl)
            .build()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            // MOB-P2-33：原先每个相框都挂 `shadow(...)` ——
            // 非零 elevation 会为**每个**图片节点生成离屏缓冲 + 模糊 pass，
            // 九宫格就是 9 个离屏层。相框本身已有 0.5dp 描边与衬纸底色，
            // 这里改用 1dp 的内描边表达厚度，视觉几乎无损而开销归零。
            .border(1.dp, colors.borderDivider, frameShape)
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
            // MOB-P0-08：原先用 SubcomposeAsyncImage —— 它会为**每张图片**创建独立的子组合，
            // 在 LazyColumn 里滑动时每帧都要多走一次组合 + 测量 + 布局，
            // 是 Compose 官方点名的头号反模式。
            // 改用 AsyncImage + onState 回调，加载/失败文案改由外层叠加，
            // 视觉完全一致，但滚动期不再产生子组合。
            AsyncImage(
                model = imageRequest,
                contentDescription = metadataText ?: "Film Photo",
                contentScale = ContentScale.Crop,
                // fillMaxSize：外层 Box 固定了宽高比，图片必须撑满整框（原 fillMaxWidth 会留白）
                modifier = Modifier.fillMaxSize(),
                onState = { state ->
                    val phase = when (state) {
                        is AsyncImagePainter.State.Loading -> 0
                        is AsyncImagePainter.State.Error -> 2
                        else -> 1
                    }
                    if (phase != loadPhase) loadPhase = phase
                }
            )

            if (loadPhase == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.film_frame_text_01),
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 11.sp,
                        color = colors.inkMuted
                    )
                }
            } else if (loadPhase == 2) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.film_frame_text_02),
                        fontFamily = ArchiveFontFamilies.MonoScale,
                        fontSize = 11.sp,
                        color = colors.inkMuted
                    )
                }
            }
        }

        if (!metadataText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = metadataText,
                fontFamily = ArchiveFontFamilies.MonoScale,
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = colors.inkMuted,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}
