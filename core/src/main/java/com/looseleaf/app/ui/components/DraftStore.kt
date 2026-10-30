package com.looseleaf.app.ui.components

import android.content.Context
import android.content.SharedPreferences

/**
 * 表单草稿存储 (MOB-P1-59)
 *
 * 记账修订 / 造物编辑 / 心愿编辑这类「填一半被系统回收或误关」的表单，
 * 此前关闭即丢内容，用户只能凭记忆重填。
 * 这里把表单字段按「对象 + 字段」写入 SharedPreferences，
 * 重新打开时自动恢复，提交成功后按前缀清空。
 *
 * 与 `quick_note_draft` 的差别：速记抽屉自带一套，这里做成通用设施，
 * 让所有编辑抽屉共用同一套「写入 / 恢复 / 清空」语义。
 */
class DraftStore private constructor(context: Context) {

    companion object {
        private const val PREFS_NAME = "looseleaf_form_drafts"

        @Volatile
        private var instance: DraftStore? = null

        fun get(context: Context): DraftStore {
            return instance ?: synchronized(this) {
                instance ?: DraftStore(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getString(key: String): String? = prefs.getString(key, null)

    fun putString(key: String, value: String?) {
        prefs.edit().apply {
            if (value == null) remove(key) else putString(key, value)
        }.apply()
    }

    /** 清空某个对象的所有草稿字段（提交成功后调用） */
    fun clearPrefix(prefix: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }
}
