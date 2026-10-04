package com.ali.cs2utility.ui

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.*
import android.view.View
import android.widget.*
import com.ali.cs2utility.domain.VideoSource
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Resolves the public, free-quality stream afresh; never stores an expiring CDN URL. */
class BiliLessonView(private val activity: Activity,private val source: VideoSource,
                     initialPosition: Int = source.startSeconds*1000,
                     initiallyPlaying: Boolean = true) : LinearLayout(activity) {
    private val handler=Handler(Looper.getMainLooper())
    private val status=activity.text("正在加载 B 站国内视频…",12f,Palette.muted)
    private val video=VideoView(activity)
    private val frame=FrameLayout(activity)
    private var web: WebView?=null
    private var fullScreen: Dialog?=null
    private var fullCallback: WebChromeClient.CustomViewCallback?=null
    private var token=0
    private var disposed=false
    private var foreground=false
    private var resumePlaying=initiallyPlaying
    private var playbackPosition=initialPosition.coerceIn(source.startSeconds*1000,
        maxOf(source.startSeconds*1000,source.endSeconds*1000))
    private val stopAtEnd=object : Runnable {
        override fun run() {
            if(disposed) return
            if(source.endSeconds>source.startSeconds && video.isPlaying && video.currentPosition>=source.endSeconds*1000) {
                video.pause();resumePlaying=false;status.text="本位置示例已结束 · 可点重播"
            }
            handler.postDelayed(this,300)
        }
    }
    init {
        orientation=VERTICAL
        addView(status)
        frame.setBackgroundColor(android.graphics.Color.BLACK)
        frame.addView(video,FrameLayout.LayoutParams(-1,-1,android.view.Gravity.CENTER))
        addView(frame,LayoutParams(-1,activity.dp(225)))
        addView(activity.row().apply {
            addWeighted(activity.button("重播此位置") { reload() })
            addWeighted(activity.button("B站播放器") { officialPlayer() })
            addWeighted(activity.button("B站原页") { openSource() })
        })
        video.setMediaController(MediaController(activity).apply { setAnchorView(frame) })
        video.setOnPreparedListener { media ->
            if(disposed) return@setOnPreparedListener
            media.isLooping=false
            video.seekTo(playbackPosition)
            if(foreground && resumePlaying) video.start()
            status.text="${source.startSeconds}–${source.endSeconds} 秒 · 对应出生位示例"
            handler.removeCallbacks(stopAtEnd);handler.post(stopAtEnd)
        }
        video.setOnErrorListener { _,_,_ ->
            status.text="直连播放失败，已切换 B 站公开播放器"
            officialPlayer();true
        }
        resolve()
    }
    private fun resolve() {
        val request=++token
        network.execute {
            val result=runCatching {
                require(source.bvid.matches(Regex("BV[0-9A-Za-z]{10}")) && source.cid>0)
                val url=URL("https://api.bilibili.com/x/player/playurl?bvid=${source.bvid}&cid=${source.cid}&qn=16&fnval=0&fourk=0")
                val connection=url.openConnection() as HttpURLConnection
                connection.connectTimeout=10000;connection.readTimeout=10000
                connection.setRequestProperty("User-Agent","Mozilla/5.0")
                connection.setRequestProperty("Referer",source.url)
                try {
                    require(connection.responseCode==200)
                    val bytes=connection.inputStream.use { input ->
                        val out=java.io.ByteArrayOutputStream();val b=ByteArray(4096)
                        while(true) { val n=input.read(b);if(n<0)break;require(out.size()+n<=1024*1024);out.write(b,0,n) };out.toByteArray()
                    }
                    val json=JSONObject(String(bytes,Charsets.UTF_8));require(json.getInt("code")==0)
                    val data=json.getJSONObject("data")
                    require(data.optInt("is_preview",0)==0) { "Restricted preview" }
                    val streams=data.getJSONArray("durl");require(streams.length()==1)
                    val uri=Uri.parse(streams.getJSONObject(0).getString("url"))
                    require(uri.scheme=="https" && uri.host?.let {
                        it=="bilivideo.com" || it.endsWith(".bilivideo.com") || it.endsWith(".bilivideo.cn") || it.endsWith(".hdslb.com")
                    }==true) { "Unexpected media host" }
                    uri
                } finally { connection.disconnect() }
            }
            handler.post {
                if(disposed || request!=token) return@post
                result.fold({ uri -> video.setVideoURI(uri,mapOf("Referer" to source.url,"User-Agent" to "Mozilla/5.0")) }, {
                    status.text="直连暂不可用，已切换 B 站公开播放器"
                    officialPlayer()
                })
            }
        }
    }
    private fun reload() {
        if(disposed) return
        playbackPosition=source.startSeconds*1000;resumePlaying=true
        web?.let { it.loadUrl(embed());return }
        status.text="重新加载此位置示例…";video.stopPlayback();resolve()
    }
    private fun embed()="https://player.bilibili.com/player.html?bvid=${source.bvid}&cid=${source.cid}&page=1&t=${source.startSeconds}&autoplay=1&danmaku=0"
    @android.annotation.SuppressLint("SetJavaScriptEnabled")
    private fun officialPlayer() {
        if(disposed) return
        ++token;handler.removeCallbacks(stopAtEnd);video.stopPlayback();frame.removeAllViews()
        web?.destroy()
        web=WebView(activity).apply {
            settings.javaScriptEnabled=true;settings.domStorageEnabled=true
            settings.allowFileAccess=false;settings.allowContentAccess=false
            settings.mixedContentMode=WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.mediaPlaybackRequiresUserGesture=false
            webViewClient=object : WebViewClient() {
                override fun onPageFinished(view: WebView?,url: String?) {
                    status.text="B 站公开播放器 · 若未自动播放，点画面中的播放键"
                    if(!this@BiliLessonView.foreground) view?.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){v.pause();});",null)
                }
                override fun onReceivedError(v: WebView?,r: WebResourceRequest?,e: WebResourceError?) {
                    if(r?.isForMainFrame==true) status.text="视频暂未打开，请重试或点 B站原页"
                }
                override fun shouldOverrideUrlLoading(v: WebView?,r: WebResourceRequest?): Boolean {
                    val uri=r?.url ?: return false
                    if(!r.isForMainFrame) return false
                    if(uri.scheme=="https" && (uri.host=="player.bilibili.com" || (uri.host=="www.bilibili.com" && uri.path?.startsWith("/blackboard/")==true))) return false
                    if(uri.scheme=="https") runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW,uri)) }
                    return true
                }
            }
            webChromeClient=object : WebChromeClient() {
                override fun onShowCustomView(view: View?,callback: CustomViewCallback?) {
                    if(view==null || fullScreen!=null) { callback?.onCustomViewHidden();return }
                    fullCallback=callback
                    fullScreen=Dialog(activity,android.R.style.Theme_Black_NoTitleBar_Fullscreen).apply {
                        setContentView(view);setOnDismissListener { fullCallback?.onCustomViewHidden();fullCallback=null;fullScreen=null };show()
                    }
                }
                override fun onHideCustomView() { fullScreen?.dismiss() }
            }
            loadUrl(embed())
        }
        frame.addView(web,FrameLayout.LayoutParams(-1,-1))
    }
    private fun openSource() { runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(source.url+"?t=${source.startSeconds}"))) } }
    fun pause() {
        foreground=false
        if(video.currentPosition>0) {
            resumePlaying=video.isPlaying;playbackPosition=video.currentPosition.coerceAtLeast(source.startSeconds*1000)
        }
        video.pause();handler.removeCallbacks(stopAtEnd);web?.onPause()
        web?.evaluateJavascript("document.querySelectorAll('video').forEach(function(v){v.pause();});",null)
    }
    fun resume() {
        foreground=true
        web?.onResume()
        if(resumePlaying) { video.seekTo(playbackPosition);video.start();handler.post(stopAtEnd) }
    }
    fun snapshot(): Pair<Int,Boolean> =
        (video.currentPosition.takeIf { it>0 } ?: playbackPosition) to (video.isPlaying || (!foreground && resumePlaying))
    fun dispose() {
        disposed=true;++token;handler.removeCallbacksAndMessages(null);video.stopPlayback()
        fullScreen?.dismiss();web?.stopLoading();web?.destroy();web=null
    }
    companion object { private val network=Executors.newFixedThreadPool(2) }
}
