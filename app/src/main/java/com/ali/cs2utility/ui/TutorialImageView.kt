package com.ali.cs2utility.ui

import android.content.Context
import android.graphics.*
import android.view.View
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import com.ali.cs2utility.domain.ImageCrop

/** Fetches the source's public reference image; no third-party video is copied into the APK. */
class TutorialImageView(context: Context, private val url: String,
                        private val crop: ImageCrop? = null) : View(context) {
    private var bitmap: Bitmap?=null
    private var message="加载原教程参考图…"
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val imageRect=RectF()
    private val d=resources.displayMetrics.density
    init {
        setOnClickListener {
            bitmap?.let { b ->
                val picture=android.widget.ImageView(context).apply { setImageBitmap(b);scaleType=android.widget.ImageView.ScaleType.FIT_CENTER }
                android.app.AlertDialog.Builder(context).setTitle("原教程参考图")
                    .setView(picture).setPositiveButton("关闭",null).show()
            }
        }
        contentDescription="原教程参考图，点击放大"
        network.execute {
            val result=runCatching {
                val parsed=URL(url);require(parsed.protocol=="https")
                val connection=parsed.openConnection() as HttpURLConnection
                connection.connectTimeout=12000;connection.readTimeout=12000
                try {
                    require(connection.responseCode==200)
                    val bytes=connection.inputStream.use { input ->
                        val output=java.io.ByteArrayOutputStream()
                        val chunk=ByteArray(8192)
                        while(true) { val n=input.read(chunk);if(n<0)break;require(output.size()+n<=8*1024*1024);output.write(chunk,0,n) }
                        output.toByteArray()
                    }
                    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
                    BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
                    require(bounds.outWidth in 1..8192 && bounds.outHeight in 1..8192)
                    val options=BitmapFactory.Options().apply {
                        inSampleSize=if(bounds.outWidth>4096) 4 else if(bounds.outWidth>2048) 2 else 1
                    }
                    if(crop!=null) {
                        require(crop.x>=0 && crop.y>=0 && crop.width>0 && crop.height>0 && crop.x+crop.width<=bounds.outWidth && crop.y+crop.height<=bounds.outHeight)
                        @Suppress("DEPRECATION")
                        val decoder=BitmapRegionDecoder.newInstance(bytes,0,bytes.size,false)
                        try { requireNotNull(decoder?.decodeRegion(Rect(crop.x,crop.y,crop.x+crop.width,crop.y+crop.height),BitmapFactory.Options())) }
                        finally { decoder?.recycle() }
                    } else requireNotNull(BitmapFactory.decodeByteArray(bytes,0,bytes.size,options))
                } finally { connection.disconnect() }
            }
            post {
                if(!isAttachedToWindow) { result.getOrNull()?.recycle();return@post }
                result.fold({ bitmap=it;invalidate() }, { message="参考图暂未加载，可点击下方位置观看视频。";invalidate() })
            }
        }
    }
    override fun onMeasure(w: Int,h: Int) {
        val width=MeasureSpec.getSize(w)
        setMeasuredDimension(width,resolveSize((width*9f/16f).toInt(),h))
    }
    override fun onDraw(canvas: Canvas) {
        canvas.drawColor(Palette.card)
        bitmap?.let { image ->
            val ratio=minOf(width.toFloat()/image.width,height.toFloat()/image.height)
            val w=image.width*ratio;val h=image.height*ratio
            imageRect.set((width-w)/2,(height-h)/2,(width+w)/2,(height+h)/2)
            canvas.drawBitmap(image,null,imageRect,paint)
            paint.style=Paint.Style.FILL
        } ?: run {
            paint.style=Paint.Style.FILL;paint.color=Palette.muted;paint.textSize=12*d;paint.textAlign=Paint.Align.CENTER
            canvas.drawText(message,width/2f,height/2f,paint)
        }
    }
    override fun performClick(): Boolean { super.performClick();return true }
    companion object { private val network=Executors.newFixedThreadPool(2) }
}
