package com.ali.cs2utility.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.*
import android.widget.LinearLayout
import android.widget.TextView

/** Uses the author's public viewer. No model authentication or extraction is attempted. */
class FineMapActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var status: TextView
    private val embed="https://sketchfab.com/models/42091af5b78941e68b01396d0c955aac/embed?autostart=1"
    private val source="https://sketchfab.com/3d-models/de-mirage-cs2-42091af5b78941e68b01396d0c955aac"
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root=column().apply { setBackgroundColor(Palette.bg);fitsSystemWindows=true;setPadding(dp(12),dp(8),dp(12),dp(12)) }
        root.addView(row().apply {
            addWeighted(button("‹ 返回") { finish() })
            addWeighted(text("Mirage 精细 3D",18f,bold=true))
        })
        status=text("正在打开精细模型，需要网络…",12f,Palette.muted);root.addView(status)
        web=WebView(this).apply {
            setBackgroundColor(Palette.bg)
            settings.javaScriptEnabled=true
            settings.domStorageEnabled=true
            settings.allowFileAccess=false
            settings.allowContentAccess=false
            settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture=true
            webViewClient=object : WebViewClient() {
                override fun onPageFinished(view: WebView?,url: String?) {
                    status.text="公开模型播放器 · 等待载入后可旋转、缩放"
                }
                override fun onReceivedError(view: WebView?,request: WebResourceRequest?,error: WebResourceError?) {
                    if(request?.isForMainFrame==true) status.text="精细模型暂未打开，可刷新或查看原页。离线模型仍可使用。"
                }
                override fun shouldOverrideUrlLoading(view: WebView?,request: WebResourceRequest?): Boolean {
                    val uri=request?.url ?: return false
                    if(!request.isForMainFrame) return false
                    if(uri.scheme=="https" && uri.host=="sketchfab.com" && uri.path?.contains("/embed")==true) return false
                    if(uri.scheme=="https") runCatching { startActivity(Intent(Intent.ACTION_VIEW,uri)) }
                    return true
                }
            }
        }
        root.addView(web,LinearLayout.LayoutParams(-1,0,1f))
        root.addView(text("De_mirage Cs2 · Kyrsia · 发布页标注 CC BY 4.0\n约 130 万三角面；在线导览，实际站位仍以教程原图为准。",11f,Palette.muted))
        root.addView(row().apply {
            addWeighted(button("刷新") { status.text="重新加载中…";web.loadUrl(embed) })
            addWeighted(button("原页") { runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(source))) } })
            addWeighted(button("VIP 快烟",true) { setResult(RESULT_OK);finish() })
        })
        setContentView(root)
        if(savedInstanceState==null || web.restoreState(savedInstanceState)==null) web.loadUrl(embed)
    }
    override fun onResume() { super.onResume();if(::web.isInitialized) { web.onResume();web.resumeTimers() } }
    override fun onPause() { if(::web.isInitialized) { web.onPause();web.pauseTimers() };super.onPause() }
    override fun onSaveInstanceState(out: Bundle) { if(::web.isInitialized) web.saveState(out);super.onSaveInstanceState(out) }
    override fun onDestroy() {
        if(::web.isInitialized) { web.stopLoading();(web.parent as? android.view.ViewGroup)?.removeView(web);web.destroy() }
        super.onDestroy()
    }
}
