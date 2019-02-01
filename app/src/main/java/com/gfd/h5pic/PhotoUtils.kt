package com.gfd.h5pic

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.core.content.FileProvider
import top.zibin.luban.Luban
import top.zibin.luban.OnCompressListener
import java.io.File


/**
 * @Author ：郭富东
 * @Date：2019/2/1:10:37
 * @Email：878749089@qq.com
 * @descriptio：
 */
object PhotoUtils {

    const val RESULT_CODE_CAMERA = 0x02
    const val RESULT_CODE_PHOTO = 0x04

    lateinit var PATH_PHOTO: String

/**
 * 拍照
 * @param context Activity
 */
fun startCamera(context: Activity) {
    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    PATH_PHOTO = getSdCardDirectory(context) + "/temp.png"
    val temp = File(PATH_PHOTO)
    if (!temp.parentFile.exists()) {
        temp.parentFile.mkdirs()
    }
    if (temp.exists()) {
        temp.delete()
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        //添加这一句表示对目标应用临时授权该Uri所代表的文件
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        // 通过FileProvider创建一个content类型的Uri
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", temp)
        intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
    } else {
        intent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(temp))
    }
    context.startActivityForResult(intent, RESULT_CODE_CAMERA)
}

    /**
     * 打开相册
     * @param context Activity
     */
    fun startAlbum(context: Activity) {
        val albumIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        albumIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        albumIntent.setDataAndType(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "image/*")
        context.startActivityForResult(albumIntent, RESULT_CODE_PHOTO)
    }

    abstract class OnPictureCompressListener {
        fun onStart() {}
        abstract fun onSuccess(file: File)
        abstract fun onError(e: Throwable)
    }

    /**
     * 压缩图片
     * @param context Context
     * @param path String
     * @param listener OnPictureCompressListener?
     */
    fun compressPicture(context: Context, path: String, listener: OnPictureCompressListener?) {
        Luban.with(context)
                .load(path)
                .ignoreBy(100)
                .setTargetDir(getSdCardDirectory(context))
                .filter { path -> !(TextUtils.isEmpty(path) || path.toLowerCase().endsWith(".gif")) }
                .setCompressListener(object : OnCompressListener {
                    override fun onStart() {
                        //压缩开始前调用，可以在方法内启动 loading UI
                        listener?.onStart()
                    }

                    override fun onSuccess(file: File) {
                        //压缩成功后调用，返回压缩后的图片文件
                        listener?.onSuccess(file)
                    }

                    override fun onError(e: Throwable) {
                        //当压缩过程出现问题时调用
                        listener?.onError(e)
                    }
                }).launch()
    }

    fun getSdCardDirectory(context: Context): String {
        var sdDir: File? = null
        if (Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
            sdDir = Environment.getExternalStorageDirectory()
        } else {
            sdDir = context.cacheDir
        }
        val cacheDir = File(sdDir, "h5pic")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        return cacheDir.path
    }

    @RequiresApi(Build.VERSION_CODES.KITKAT)
    fun getPath(context: Context, uri: Uri): String? {
        val isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT
        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            // ExternalStorageProvider
            if (isExternalStorageDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]
                if ("primary".equals(type, ignoreCase = true)) {
                    return Environment.getExternalStorageDirectory().path + "/" + split[1]
                }
            } else if (isDownloadsDocument(uri)) {
                val id = DocumentsContract.getDocumentId(uri)
                val contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), java.lang.Long.valueOf(id))
                return getDataColumn(context, contentUri, null, null)
            } else if (isMediaDocument(uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                val split = docId.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val type = split[0]

                var contentUri: Uri? = null
                if ("image" == type) {
                    contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                } else if ("video" == type) {
                    contentUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                } else if ("audio" == type) {
                    contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val selection = "_id=?"
                val selectionArgs = arrayOf(split[1])

                return getDataColumn(context, contentUri, selection, selectionArgs)
            }
        } else if ("content".equals(uri.scheme, ignoreCase = true)) {
            return getDataColumn(context, uri, null, null)
        } else if ("file".equals(uri.scheme, ignoreCase = true)) {
            return uri.path
        }
        return null
    }

    private fun getDataColumn(context: Context, uri: Uri?, selection: String?, selectionArgs: Array<String>?): String? {
        var cursor: Cursor? = null
        val column = "_data"
        val projection = arrayOf(column)

        try {
            cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
            if (cursor != null && cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndexOrThrow(column)
                return cursor?.getString(columnIndex)
            }
        } finally {
            if (cursor != null) cursor!!.close()
        }
        return null
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private fun isExternalStorageDocument(uri: Uri): Boolean {
        return "com.android.externalstorage.documents" == uri.authority
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private fun isDownloadsDocument(uri: Uri): Boolean {
        return "com.android.providers.downloads.documents" == uri.authority
    }


    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private fun isMediaDocument(uri: Uri): Boolean {
        return "com.android.providers.media.documents" == uri.authority
    }
}