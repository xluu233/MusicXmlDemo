package com.example.musicxmldemo.util

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import android.content.res.AssetManager




/**
 * @ClassName AssertMangerUtil
 * @Description TODO
 * @Author AlexLu_1406496344@qq.com
 * @Date 2021/8/23 9:23
 */
object AssertMangerUtil {

    /**
     * 拷贝asset文件到指定路径，可变更文件名
     *
     * @param context   context
     * @param assetName asset文件
     * @param savePath  目标路径
     * @param saveName  目标文件名
     */
    fun copyFileFromAssets(
        context: Context,
        assetName: String,
        savePath: String,
        saveName: String
    ):Boolean{
        // 若目标文件夹不存在，则创建
        val dir = File(savePath)
        if (!dir.exists()) {
            if (!dir.mkdir()) {
                Log.d("FileUtils", "mkdir error: $savePath")
                return false
            }
        }

        // 拷贝文件
        val filename = "$savePath/$saveName"
        val file = File(filename)
        if (!file.exists()) {
            try {
                val inStream: InputStream = context.assets.open(assetName)
                val fileOutputStream = FileOutputStream(filename)
                var byteread: Int
                val buffer = ByteArray(1024)
                while (inStream.read(buffer).also { byteread = it } != -1) {
                    fileOutputStream.write(buffer, 0, byteread)
                }
                fileOutputStream.flush()
                inStream.close()
                fileOutputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
                return false
            }
            Log.d("FileUtils", "[copyFileFromAssets] copy asset file: $assetName to : $filename")
        } else {
            Log.d("FileUtils", "[copyFileFromAssets] file is exist: $filename")
        }
        return true
    }

    /**
     * 拷贝asset目录下所有文件到指定路径
     *
     * @param context    context
     * @param assetsPath asset目录
     * @param savePath   目标目录
     */
    fun copyFilesFromAssets(context: Context, assetsPath: String, savePath: String) {
        try {
            // 获取assets指定目录下的所有文件
            val fileList: Array<String> ?= context.assets.list(assetsPath)
            if (!fileList.isNullOrEmpty()) {
                val file = File(savePath)
                // 如果目标路径文件夹不存在，则创建
                if (!file.exists()) {
                    if (!file.mkdirs()) {
                        Log.d("FileUtils", "mkdir error: $savePath")
                        return
                    }
                }
                for (fileName in fileList) {
                    copyFileFromAssets(context, "$assetsPath/$fileName", savePath, fileName)
                }
            }
        } catch (e: Exception) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
    }

    fun loadAssetsToCache(context: Context, assertName: String):File {
        val filePath: String = FilePath.getAppCachePath(context)
        val file = File("$filePath/$assertName")
        val assetManager = context.assets
        try {
            val inputStream = assetManager.open(assertName)
            //保存到本地的文件夹下的文件
            val fileOutputStream = FileOutputStream(file)
            val buffer = ByteArray(1024)
            var count = 0
            while (inputStream.read(buffer).also { count = it } > 0) {
                fileOutputStream.write(buffer, 0, count)
            }
            fileOutputStream.flush()
            fileOutputStream.close()
            inputStream.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return file
    }
}