package com.example.musicxmldemo.util

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.FileUtils
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import androidx.core.net.toFile
import java.io.*
import kotlin.concurrent.thread

/**
 * @ClassName FileUtil
 * @Description 文件工具类，包括获取文件目录，文件格式转换
 * @Author AlexLu_1406496344@qq.com
 * @Date 2021/4/15 10:54
 */

object FilePath {

    /**
     *  私有目录-files
     */
    fun getAppFilePath(context: Context,subDir:String?=null): String {
        val path = StringBuilder(context.filesDir.absolutePath)
        subDir?.let {
            path.append(File.separator).append(it).append(File.separator)
        }
        val dir = File(path.toString())
        if (!dir.exists()) dir.mkdir()
        return path.toString()
    }

    /**
     * 私有目录-cache
     */
    fun getAppCachePath(context: Context,subDir:String?=null):String{
        val path = StringBuilder(context.cacheDir.absolutePath)
        subDir?.let {
            path.append(File.separator).append(it).append(File.separator)
        }
        val dir = File(path.toString())
        if (!dir.exists()) dir.mkdir()
        return path.toString()
    }

}
