package com.looseleaf.app.utils

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 轻量崩溃捕获 (MOB-P1-63)
 *
 * 此前 App 没有任何崩溃可观测性：用户遇到白屏/闪退后无从反馈，
 * 开发者只能靠口述复现。
 *
 * 这里不引入任何三方 SDK（保持 APK 体积与隐私边界），
 * 而是自己接管 `Thread.setDefaultUncaughtExceptionHandler`：
 * 1. 把崩溃信息（时间 / 版本 / 机型 / 线程 / 堆栈）写入应用私有目录；
 * 2. 下次启动时由界面读出并以「纸页写坏了」的方式友好提示用户；
 * 3. 原始异常仍然照常抛给系统（不影响系统的崩溃上报与进程回收逻辑）。
 */
object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val CRASH_DIR = "crash"
    private const val CRASH_FILE = "last_crash.txt"

    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /** 安装全局异常处理器（必须在 Application.onCreate 的最早期调用） */
    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { writeReport(appContext, thread, throwable) }
                .onFailure { Log.e(TAG, "写入崩溃报告失败: ${it.message}") }
            // 交还给系统默认处理（进程回收 / 系统崩溃对话框）
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** 读取上次崩溃报告（存在即代表上次是非正常退出） */
    fun lastCrashReport(context: Context): String? {
        return runCatching {
            val file = crashFile(context)
            if (file.exists()) file.readText().takeIf { it.isNotBlank() } else null
        }.getOrNull()
    }

    /** 清除上次崩溃报告（用户已确认后调用，避免反复提示） */
    fun clearLastCrash(context: Context) {
        runCatching { crashFile(context).delete() }
    }

    private fun crashFile(context: Context): File {
        val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
        return File(dir, CRASH_FILE)
    }

    private fun writeReport(context: Context, thread: Thread, throwable: Throwable) {
        val dir = File(context.filesDir, CRASH_DIR).apply { mkdirs() }
        val file = File(dir, CRASH_FILE)
        val sb = StringBuilder()
        sb.append("time=").append(timeFormat.format(Date())).append('\n')
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = packageInfo?.versionName ?: "unknown"
        val versionCode = if (Build.VERSION.SDK_INT >= 28) {
            packageInfo?.longVersionCode ?: 0L
        } else {
            @Suppress("DEPRECATION")
            packageInfo?.versionCode?.toLong() ?: 0L
        }
        sb.append("app=").append(versionName).append(" (").append(versionCode).append(")\n")
        sb.append("android=").append(Build.VERSION.RELEASE)
            .append(" / API ").append(Build.VERSION.SDK_INT).append('\n')
        sb.append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append("thread=").append(thread.name).append('\n')
        sb.append("exception=").append(throwable.javaClass.name)
            .append(": ").append(throwable.message ?: "").append('\n')
        sb.append("stack=\n")
        sb.append(throwable.stackTraceToString().take(8000))
        // 先写临时文件再改名，避免进程被杀时留下半截文件
        val tmp = File(dir, "$CRASH_FILE.tmp")
        tmp.writeText(sb.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(sb.toString())
            tmp.delete()
        }
    }
}
