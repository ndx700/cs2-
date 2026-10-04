package com.ali.cs2utility.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import com.ali.cs2utility.R
import com.ali.cs2utility.domain.VideoKind
import com.ali.cs2utility.domain.VideoSource
import com.ali.cs2utility.domain.ImageCrop

class VideoActivity : Activity() {
    private var player: VideoView?=null
    private var position=0
    private var resumePlayback=false
    private lateinit var message: TextView
    private var bili: BiliLessonView?=null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if(intent.getStringExtra("kind")==VideoKind.BILIBILI.name) { showBiliLesson(savedInstanceState);return }
        position=savedInstanceState?.getInt("position") ?: 0
        resumePlayback=savedInstanceState?.getBoolean("playing") ?: intent.getBooleanExtra("autoplay",false)
        val root=column().apply { setBackgroundColor(Palette.bg); setPadding(dp(16),dp(12),dp(16),dp(16)); fitsSystemWindows=true }
        root.addView(button("‹ 返回地图") { finish() })
        root.addView(text(intent.getStringExtra("title") ?: "教学视频",23f,bold=true))
        message=text("点击播放按钮开始",13f,Palette.muted); root.addView(message)
        val kind=runCatching { VideoKind.valueOf(intent.getStringExtra("kind") ?: "DEMO") }.getOrDefault(VideoKind.DEMO)
        val url=intent.getStringExtra("url") ?: ""
        if(kind==VideoKind.EXTERNAL) {
            root.addView(text("此素材由外部视频平台提供。",14f,Palette.muted))
            root.addView(button("打开原始教学页面",true) {
                if(!validHttps(url)) { message.text="视频地址无效"; return@button }
                try { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url))) }
                catch(e: ActivityNotFoundException) { message.text="未找到可打开视频的应用" }
            })
        } else {
            val frame=FrameLayout(this)
            val video=VideoView(this); player=video
            frame.addView(video,FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
            root.addView(frame,LinearLayout.LayoutParams(-1,0,1f))
            val controls=MediaController(this); controls.setAnchorView(video); video.setMediaController(controls)
            video.setOnPreparedListener {
                video.seekTo(position)
                message.text=if(kind==VideoKind.DEMO) "离线流程样片 · 不含实测道具教学" else "视频已就绪"
                if(resumePlayback) video.start()
            }
            video.setOnCompletionListener { position=0; resumePlayback=false; message.text="播放结束，可重新播放" }
            video.setOnErrorListener { _,_,_ ->
                resumePlayback=false; message.text="视频暂时无法播放，请检查网络或素材格式，然后重试。"; true
            }
            fun load() {
                val uri=if(kind==VideoKind.DEMO) Uri.parse("android.resource://$packageName/${R.raw.flow_demo}")
                    else if(validHttps(url)) Uri.parse(url) else null
                if(uri!=null) video.setVideoURI(uri) else message.text="只接受有效的 HTTPS 视频地址"
            }
            root.addView(row().apply {
                addWeighted(button("▶ 播放 / 暂停",true) {
                    if(video.isPlaying) { video.pause(); resumePlayback=false }
                    else { video.start(); resumePlayback=true }
                })
                addWeighted(button("重新加载") { position=0; resumePlayback=false; load() })
            })
            load()
        }
        val source=intent.getStringExtra("source") ?: ""
        if(validHttps(source)) root.addView(button("原教程 / 播放备用") {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(source))) }
                .onFailure { message.text="未找到可打开原教程的应用" }
        })
        root.addView(text("素材归属：${intent.getStringExtra("attribution") ?: "待登记"}",12f,Palette.muted))
        setContentView(root)
    }
    private fun showBiliLesson(saved: Bundle?) {
        val root=column().apply { setBackgroundColor(Palette.bg);setPadding(dp(12),dp(8),dp(12),dp(12));fitsSystemWindows=true }
        val scroll=ScrollView(this).apply { isFillViewport=true }
        val body=column()
        body.addView(row().apply {
            addWeighted(button("‹ 返回出生位") { finish() })
            addWeighted(text(intent.getStringExtra("title") ?: "VIP 烟示例",18f,bold=true))
        })
        body.addView(text("瞄点参考 · 点图片可放大",13f,Palette.accent,true))
        val a=intent.getIntArrayExtra("aimCrop")
        val crop=a?.takeIf { it.size==4 }?.let { ImageCrop(it[0],it[1],it[2],it[3]) }
        val url=intent.getStringExtra("aimImage") ?: ""
        if(validHttps(url)) body.addView(TutorialImageView(this,url,crop=crop),LinearLayout.LayoutParams(-1,dp(175)))
        body.addView(text(intent.getStringExtra("aim") ?: "按视频中的开镜特写定位瞄点",13f))
        val video=VideoSource(VideoKind.BILIBILI,intent.getStringExtra("url") ?: "",intent.getStringExtra("attribution") ?: "",
            intent.getStringExtra("bvid") ?: "",intent.getLongExtra("cid",0),intent.getIntExtra("startSeconds",0),intent.getIntExtra("endSeconds",0))
        bili=BiliLessonView(this,video,saved?.getInt("position") ?: video.startSeconds*1000,
            saved?.getBoolean("playing") ?: true);body.addView(bili)
        body.addView(text("投掷：${intent.getStringExtra("throwMode") ?: "按原视频示范"}",13f,Palette.accent))
        body.addView(text("${video.attribution}\n来源教程尚未在当前 CS2 build 复测。",11f,Palette.muted))
        scroll.addView(body);root.addView(scroll,LinearLayout.LayoutParams(-1,-1));setContentView(root)
    }
    private fun validHttps(url: String)=runCatching { Uri.parse(url).let { it.scheme=="https" && !it.host.isNullOrBlank() } }.getOrDefault(false)
    override fun onPause() {
        bili?.pause()
        player?.let { position=it.currentPosition; resumePlayback=it.isPlaying; it.pause() }
        super.onPause()
    }
    override fun onResume() { super.onResume();bili?.resume(); if(resumePlayback) player?.start() }
    override fun onSaveInstanceState(out: Bundle) {
        val b=bili?.snapshot()
        out.putInt("position",b?.first ?: player?.currentPosition ?: position)
        out.putBoolean("playing",b?.second ?: (player?.isPlaying==true || resumePlayback))
        super.onSaveInstanceState(out)
    }
    override fun onDestroy() { bili?.dispose();player?.stopPlayback(); super.onDestroy() }
}
