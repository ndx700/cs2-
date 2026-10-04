package com.ali.cs2utility.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.*

object Palette {
    val bg=Color.rgb(16,21,29); val card=Color.rgb(28,36,48)
    val text=Color.rgb(243,239,230); val muted=Color.rgb(157,173,193)
    val accent=Color.rgb(240,190,120)
}
fun Context.dp(n: Int)=(n*resources.displayMetrics.density+.5f).toInt()
fun surface(color: Int, radius: Float = 16f)=GradientDrawable().apply { setColor(color); cornerRadius=radius }
fun Context.text(value: String, size: Float = 14f, color: Int = Palette.text, bold: Boolean=false)=TextView(this).apply {
    text=value; textSize=size; setTextColor(color)
    if(bold) setTypeface(typeface,Typeface.BOLD)
    setPadding(0,dp(4),0,dp(4))
}
fun Context.column()=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
fun Context.row()=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=android.view.Gravity.CENTER_VERTICAL }
fun Context.button(label: String, primary: Boolean = false, action: () -> Unit)=Button(this).apply {
    text=label; textSize=13f; isAllCaps=false; minHeight=dp(48); minimumHeight=dp(48)
    setTextColor(if(primary) Palette.bg else Palette.text)
    background=surface(if(primary) Palette.accent else Palette.card,dp(12).toFloat())
    setPadding(dp(12),0,dp(12),0); setOnClickListener { action() }
    contentDescription=label
}
fun LinearLayout.addWeighted(view: View, weight: Float=1f) {
    addView(view,LinearLayout.LayoutParams(0,-2,weight).apply { marginEnd=context.dp(6) })
}
