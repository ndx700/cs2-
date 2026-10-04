package com.ali.cs2utility.scene

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.hypot

/** Visual controls; the parent owns pointer IDs so moving, turning and height can be held together. */
class RunControls(context: Context): View(context) {
    private val d=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    var stickX=0f;var stickY=0f;var speed=6f
    private val cx get()=70*d
    private val cy get()=height-76*d
    private val radius get()=50*d
    private val bx get()=width-55*d
    fun hit(x: Float,y: Float): String?=when {
        hypot(x-cx,y-cy)<=radius*1.25f -> "stick"
        x>width-112*d && y<54*d -> "speed"
        kotlin.math.abs(x-bx)<43*d && kotlin.math.abs(y-(height-126*d))<23*d -> "up"
        kotlin.math.abs(x-bx)<43*d && kotlin.math.abs(y-(height-72*d))<23*d -> "down"
        else -> null
    }
    fun stick(x: Float,y: Float) {
        val dx=(x-cx)/radius;val dy=(y-cy)/radius;val length=hypot(dx,dy).coerceAtLeast(1f)
        stickX=dx/length;stickY=dy/length;invalidate()
    }
    fun clear() {stickX=0f;stickY=0f;invalidate()}
    override fun onDraw(c: Canvas) {
        paint.color=0xc910151d.toInt();c.drawCircle(cx,cy,radius,paint)
        paint.color=0xff87baff.toInt();paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d
        c.drawCircle(cx,cy,radius,paint);paint.style=Paint.Style.FILL
        paint.textAlign=Paint.Align.CENTER;paint.textSize=12*d;paint.color=0xffdae6f5.toInt()
        c.drawText("前",cx,cy-radius+17*d,paint);c.drawText("后",cx,cy+radius-8*d,paint)
        c.drawText("左",cx-radius+13*d,cy+4*d,paint);c.drawText("右",cx+radius-13*d,cy+4*d,paint)
        paint.color=0xc987baff.toInt();c.drawCircle(cx+stickX*radius*.62f,cy+stickY*radius*.62f,16*d,paint)
        fun key(label: String,x: Float,y: Float,w: Float) {
            paint.color=0xdd10151d.toInt();c.drawRoundRect(x-w*d,y-22*d,x+w*d,y+22*d,9*d,9*d,paint)
            paint.color=0xffe2ebf8.toInt();paint.textSize=13*d;c.drawText(label,x,y+5*d,paint)
        }
        key("↑ 上升",bx,height-126*d,41f);key("↓ 下降",bx,height-72*d,41f)
        key("速度 ${speed.toInt()} m/s",width-62*d,27*d,50f)
    }
}
