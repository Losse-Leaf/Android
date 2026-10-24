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
import androidx.compose.ui.res.stringResource
import com.looseleaf.app.R
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
        title = stringResource(R.string.profile_sheets_title_01),
        actionButtonText = "保存档案",
        isActionEnabled = name.isNotBlank(),
        onActionClick = {
            // 契约 D：可清空文本字段「出现且空串 = 清空；缺省/null = 保持」。
            // 原先 takeIf { isNotBlank } 会把用户删空的字段整个省略，
            // 服务端按「保持原值」处理——「删空格言/头衔」从未真正生效。
            val finalCity = cityName.trim()
            onSave(
                UpdateProfileRequest(
                    name = name.trim(),
                    title = title.trim(),
                    currentCity = finalCity,
                    location = finalCity,
                    motto = motto.trim(),
                    manifesto = manifesto.trim()
                )
            )
        }
    ) {
        // 1. 称谓输入
        Text(stringResource(R.string.profile_sheets_title), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
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
                        text = stringResource(R.string.profile_sheets_text_01),
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
        Text(stringResource(R.string.profile_sheets_identity), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
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
                        text = stringResource(R.string.profile_sheets_text_02),
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
            placeholder = stringResource(R.string.profile_sheets_placeholder_01)
        )

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(thickness = 0.5.dp, color = colors.borderDivider.copy(alpha = 0.4f))
        Spacer(modifier = Modifier.height(14.dp))

        // 4. 生活信条与格言
        Text(stringResource(R.string.profile_sheets_motto), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
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
                        text = stringResource(R.string.profile_sheets_text_03),
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
        Text(stringResource(R.string.profile_sheets_confession), fontFamily = ArchiveFontFamilies.BodySans, fontSize = 11.5.sp, color = colors.inkMuted)
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
                        text = stringResource(R.string.profile_sheets_text_04),
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
