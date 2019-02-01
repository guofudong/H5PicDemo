package com.gfd.h5pic

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.webkit.WebSettings.LayoutAlgorithm.SINGLE_COLUMN
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.gfd.h5pic.PhotoUtils.compressPicture
import com.gfd.h5pic.PhotoUtils.getSdCardDirectory
import com.gfd.h5pic.PhotoUtils.startAlbum
import com.gfd.h5pic.PhotoUtils.startCamera
import com.gfd.h5pic.R.id.mWebView
import kotlinx.android.synthetic.main.activity_main.*
import permissions.dispatcher.NeedsPermission
import permissions.dispatcher.RuntimePermissions
import java.io.File

@RuntimePermissions
class MainActivity : AppCompatActivity() {

    private var mSelectPhotoDialog: SelectDialog? = null
    private var mFilePathCallback: ValueCallback<Array<Uri>>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        initWebView()
        mWebView.loadUrl("file:///android_asset/index.html")
    }

    @NeedsPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.CAMERA)
    fun applyPermission() {

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        onRequestPermissionsResult(requestCode, grantResults)

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PhotoUtils.RESULT_CODE_CAMERA && resultCode == Activity.RESULT_OK) {
            //拍照并确定
            compressPicture(PhotoUtils.PATH_PHOTO)
        } else if (requestCode == PhotoUtils.RESULT_CODE_PHOTO && resultCode == Activity.RESULT_OK) {
            //相册选择并确定
            val result = data?.data
            val path = result?.let { PhotoUtils.getPath(this, it) }
            if (path == null) {
                mFilePathCallback?.onReceiveValue(null)
            } else {
                compressPicture(path)
            }
        } else {
            mFilePathCallback?.onReceiveValue(null)
        }
    }

    /**
     * 压缩图片
     */
    private fun compressPicture(path: String) {
        PhotoUtils.compressPicture(this, path, object : PhotoUtils.OnPictureCompressListener() {
            override fun onSuccess(file: File) {
                mFilePathCallback?.onReceiveValue(arrayOf(Uri.fromFile(file)))
            }

            override fun onError(e: Throwable) {
                mFilePathCallback?.onReceiveValue(null)
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        val settings = mWebView.settings
        settings.javaScriptEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = true
        //自适应屏幕
        settings.layoutAlgorithm = SINGLE_COLUMN
        settings.loadWithOverviewMode = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.setSupportZoom(false)
        mWebView.isHorizontalScrollBarEnabled = false
        mWebView.isVerticalScrollBarEnabled = false
        mWebView.webViewClient = webViewClient
        mWebView.webChromeClient = webViewChromeClient
    }

    private val webViewClient = object : WebViewClient() {

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            applyPermissionWithPermissionCheck()
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            view.loadUrl(request.url.toString())
            return true
        }
    }

    private val webViewChromeClient = object : WebChromeClient() {

        override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>>,
                                       fileChooserParams: FileChooserParams): Boolean {
            mFilePathCallback = filePathCallback
            val acceptTypes = fileChooserParams.acceptTypes
            if (acceptTypes.contains("image/*")) {
                showSelectDialog()
            }
            return true
        }
    }

    /**
     * 显示相册/拍照选择对话框
     */
    private fun showSelectDialog() {
        if (mSelectPhotoDialog == null) {
            mSelectPhotoDialog = SelectDialog(this, View.OnClickListener { view ->
                when (view.id) {
                    R.id.tv_camera -> startCamera()
                    R.id.tv_photo -> startAlbum()
                    //不管选择还是不选择，必须有返回结果，否则就会调用一次
                    R.id.tv_cancel -> {
                        mFilePathCallback?.onReceiveValue(null)
                        mFilePathCallback = null
                    }
                }
            })
        }
        mSelectPhotoDialog?.show()
    }

    /**
     * 打开相册
     */
    private fun startAlbum() {
        PhotoUtils.startAlbum(this)
    }

    /**
     * 拍照
     */
    @NeedsPermission(Manifest.permission.CAMERA)
    fun startCamera() {
        PhotoUtils.startCamera(this)
    }

    override fun onDestroy() {
        if (mWebView != null) {
            val parent = this.parent
            if (parent != null) {
                (parent as ViewGroup).removeView(mWebView)
            }
            mWebView.stopLoading()
            mWebView.onPause()
            mWebView.settings.javaScriptEnabled = false
            mWebView.clearHistory()
            mWebView.clearView()
            mWebView.removeAllViews()
            mWebView.destroy()
        }
        super.onDestroy()
    }
}
