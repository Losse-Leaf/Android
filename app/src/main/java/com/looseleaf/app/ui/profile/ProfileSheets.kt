package com.looseleaf.app.ui.profile

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.looseleaf.app.model.CuratorProfile
import com.looseleaf.app.model.UpdateProfileRequest
import com.looseleaf.app.utils.CityDictionary
import com.looseleaf.app.theme.ArchiveFontFamilies
import com.looseleaf.app.theme.LooseLeafTheme
import com.looseleaf.app.ui.components.*
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditProfileSheet(
    profile: CuratorProfile,
    onDismiss: () -> Unit,
    onSave: (UpdateProfileRequest) -> Unit
) {
    val colors = LooseLeafTheme.colors
    var name by remember(profile) { mutableStateOf(profile.name) }
    var title by remember(profile) { mutableStateOf(profile.title ?: "") }
    var cityCode by remember(profile) {
        val initialLoc = profile.currentCity.takeIf { !it.isNullOrBlank() } ?: profile.location ?: ""
        mutableStateOf(CityDictionary.codeForLocation(initialLoc) ?: "")
    }
    var cityName by remember(profile) {
        mutableStateOf(profile.currentCity.takeIf { !it.isNullOrBlank() } ?: profile.location ?: "")
    }
    var motto by remember(profile) { mutableStateOf(profile.motto ?: "") }
    var manifesto by remember(profile) { mutableStateOf(profile.manifesto ?: "") }

    ArchiveBottomSheet(
        onDismissRequest = onDismiss,
        title = "《主理人手卷》",
        actionButtonText = "保存档案",
        isActionEnabled = name.isNotBlank(),
        onActionClick = {
            val finalCity = cityName.trim()
            onSave(
                UpdateProfileRequest(
                    name = name.trim(),
                    title = title.trim().takeIf { it.isNotBlank() },
                    currentCity = finalCity.takeIf { it.isNotBlank() },
                    location = finalCity.takeIf { it.isNotBlank() },
                    motto = motto.trim().takeIf { it.isNotBlank() },
                    manifesto = manifesto.trim().takeIf { it.isNotBlank() }
                )
            )
        }
    ) {
        // 1. 称谓输入
        Text("称谓", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
        Spacer(modifier = Modifier.height(3.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.inkPrimary
            ),
            cursorBrush = SolidColor(colors.accentWarm),
            decorationBox = { innerTextField ->
                if (name.isEmpty()) {
                    Text(
                        text = "输入主理人称谓…",
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 18.sp,
                        color = colors.inkMuted.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(14.dp))

        // 2. 头衔与身份
        Text("头衔与身份", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
        Spacer(modifier = Modifier.height(3.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = title,
            onValueChange = { title = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = 15.sp,
                color = colors.inkPrimary
            ),
            cursorBrush = SolidColor(colors.accentWarm),
            decorationBox = { innerTextField ->
                if (title.isEmpty()) {
                    Text(
                        text = "如：生活记录者、独立摄影师…",
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 15.sp,
                        color = colors.inkMuted.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(14.dp))

        // 3. 常居城市（严格使用统一行政区划选择器，定位到地级市！）
        Text("常居城市 (点选定位至地级市)", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
        Spacer(modifier = Modifier.height(6.dp))
        PaperCityPickerField(
            selectedCityCode = cityCode,
            selectedCityName = cityName,
            onCitySelected = { c, n ->
                cityCode = c
                cityName = n
            },
            placeholder = "点选所在省市 (375 国标行政区划)…"
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(14.dp))

        // 4. 生活信条与格言
        Text("生活格言", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
        Spacer(modifier = Modifier.height(3.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = motto,
            onValueChange = { motto = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = 14.5.sp,
                color = colors.accentWarm
            ),
            cursorBrush = SolidColor(colors.accentWarm),
            decorationBox = { innerTextField ->
                if (motto.isEmpty()) {
                    Text(
                        text = "写下一句指引你的生活信条…",
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 14.5.sp,
                        color = colors.inkMuted.copy(alpha = 0.5f)
                    )
                }
                innerTextField()
            }
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(14.dp))

        // 5. 自白手记正文
        Text("自白手记", fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
        Spacer(modifier = Modifier.height(6.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = manifesto,
            onValueChange = { manifesto = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 240.dp),
            textStyle = TextStyle(
                fontFamily = ArchiveFontFamilies.BodySans,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = colors.inkPrimary
            ),
            cursorBrush = SolidColor(colors.accentWarm),
            decorationBox = { innerTextField ->
                if (manifesto.isEmpty()) {
                    Text(
                        text = "关于你自己、关于时间、关于生活的自白散篇…",
                        fontFamily = ArchiveFontFamilies.BodySans,
                        fontSize = 14.sp,
                        lineHeight = 22.sp,
                        color = colors.inkMuted.copy(alpha = 0.45f)
                    )
                }
                innerTextField()
            }
        )
    }
}
