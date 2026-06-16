package com.aicamera.app.core.camera

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 照片保存工具类。
 * 将拍照结果保存到系统相册（MediaStore），支持 Android 10+ 作用域存储。
 */
@Singleton
class PhotoSaver @Inject constructor(
    private val context: Context
) {

    /** 生成带时间戳的文件名 */
    private fun generateFileName(): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "IMG_${timestamp}_${(1000..9999).random()}"
    }

    /**
     * 在 MediaStore 中创建一个待写入的 URI（Android 10+ 推荐方式）。
     * @return 可写入的 URI，null 表示创建失败
     */
    fun createPendingPhotoUri(): Uri? {
        return try {
            val displayName = generateFileName()
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AICamera")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将 URI 标记为非待定状态（写入完成后调用）。
     */
    fun markPhotoReady(uri: Uri) {
        try {
            val values = ContentValues().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
            }
            context.contentResolver.update(uri, values, null, null)
        } catch (_: Exception) {
        }
    }

    /**
     * 将 Bitmap 直接保存到系统相册。
     * @param bitmap 要保存的图片
     * @return 保存后的 URI，null 表示失败
     */
    fun saveBitmap(bitmap: Bitmap): Uri? {
        return try {
            val displayName = generateFileName()
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AICamera")
                put(MediaStore.Images.Media.WIDTH, bitmap.width)
                put(MediaStore.Images.Media.HEIGHT, bitmap.height)
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                outputStream.flush()
            }

            uri
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 将 JPEG byte 数组保存到系统相册。
     * @param jpegBytes JPEG 编码的字节数组
     * @return 保存后的 URI，null 表示失败
     */
    fun saveJpegBytes(jpegBytes: ByteArray): Uri? {
        return try {
            val displayName = generateFileName()
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/AICamera")
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return null

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(jpegBytes)
                outputStream.flush()
            }

            uri
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 加载缩略图用于预览。
     * @param uri 照片 URI
     * @return 缩略图 Bitmap
     */
    fun loadThumbnail(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.loadThumbnail(uri, Size(200, 200), null)
        } catch (e: Exception) {
            null
        }
    }
}