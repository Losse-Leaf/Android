package com.looseleaf.app

import com.looseleaf.app.updater.UpdateChecker
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * MOB-P1-62：发行变体的安装包选择。
 *
 * 同一个 Release 里现在有两个 APK（公网版 / 局域网版），内置更新器**必须**
 * 按当前安装的变体挑对 —— 挑错的话要么装成另一个 App（包名不同），
 * 要么明文策略对不上（公网版装了个允许明文的包，等于把洞重新打开）。
 */
class UpdateAssetSelectionTest {

    private val json = Json

    private fun assets(vararg names: String): JsonArray = buildJsonArray {
        names.forEachIndexed { index, name ->
            add(
                buildJsonObject {
                    put("name", name)
                    put("browser_download_url", "https://example.com/$name")
                    put("digest", "sha256:${"a".repeat(64)}")
                    put("size", 1000 + index)
                }
            )
        }
    }

    @Test
    fun `公网版挑中不带 lan 标记的包`() {
        val picked = UpdateChecker.selectApkAsset(
            assets("LooseLeaf-v1.6.2.lan.apk", "LooseLeaf-v1.6.2.apk"),
            wantLan = false
        )
        assertNotNull(picked)
        assertEquals("LooseLeaf-v1.6.2.apk", picked!!.name)
    }

    @Test
    fun `局域网版挑中带 lan 标记的包`() {
        val picked = UpdateChecker.selectApkAsset(
            assets("LooseLeaf-v1.6.2.lan.apk", "LooseLeaf-v1.6.2.apk"),
            wantLan = true
        )
        assertNotNull(picked)
        assertEquals("LooseLeaf-v1.6.2.lan.apk", picked!!.name)
    }

    @Test
    fun `兼容连字符写法`() {
        val picked = UpdateChecker.selectApkAsset(
            assets("LooseLeaf-v1.6.2-lan.apk", "LooseLeaf-v1.6.2-public.apk"),
            wantLan = true
        )
        assertEquals("LooseLeaf-v1.6.2-lan.apk", picked?.name)
    }

    @Test
    fun `只有单个 APK 的老 Release 局域网版拿不到包`() {
        // MOB-P1-xx：老 Release 只有公网版一个包。局域网版**不能**拿它 ——
        // 两个变体共用同一把签名密钥，签名校验区分不出来，
        // 拿错包会一路走到系统安装器，最后因 applicationId 不同被拒，
        // 用户只看到「装不上」这种毫无指向性的提示。
        val picked = UpdateChecker.selectApkAsset(
            assets("LooseLeaf-v1.6.1.apk"),
            wantLan = true
        )
        assertNull("局域网版不应拿到公网版的包", picked)
    }

    @Test
    fun `只有单个公网 APK 时公网版照常可用`() {
        val picked = UpdateChecker.selectApkAsset(
            assets("LooseLeaf-v1.6.1.apk"),
            wantLan = false
        )
        assertEquals("LooseLeaf-v1.6.1.apk", picked?.name)
    }

    @Test
    fun `没有 APK 时返回空`() {
        val picked = UpdateChecker.selectApkAsset(
            buildJsonArray {
                add(buildJsonObject {
                    put("name", "source-code.zip")
                    put("browser_download_url", "https://example.com/source.zip")
                })
            },
            wantLan = false
        )
        assertNull(picked)
    }

    @Test
    fun `摘要与体积一并带出`() {
        val picked = UpdateChecker.selectApkAsset(
            assets("LooseLeaf-v1.6.2.apk"),
            wantLan = false
        )
        // 摘要的 "sha256:" 前缀会被剥掉，只留 64 位十六进制
        assertEquals("a".repeat(64), picked?.digest)
        assertEquals(1000L, picked?.size)
    }
}
