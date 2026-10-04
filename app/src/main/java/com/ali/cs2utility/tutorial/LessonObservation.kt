package com.ali.cs2utility.tutorial

import com.ali.cs2utility.scene.CameraState

data class LessonViewport(val x: Int,val top: Int,val width: Int,val height: Int) {
    fun contains(px: Float,py: Float)=px>=x && px<x+width && py>=top && py<top+height
}

/** UI and GL use the same rectangle, including the enlarged view's touch-to-return area. */
object LessonObservation {
    fun viewport(width: Int,height: Int,density: Float,expanded: Boolean): LessonViewport {
        require(width>0 && height>0 && density.isFinite() && density>0)
        if(expanded)return LessonViewport(0,0,width,height)
        val margin=(6*density).toInt().coerceAtMost(minOf(width/32,height/16))
        val w=maxOf(width*.32f,144*density).toInt().coerceIn(1,(width*.44f).toInt().coerceAtLeast(1))
        val h=(w*.85f).toInt().coerceIn(1,(height*.42f).toInt().coerceAtLeast(1))
        return LessonViewport(width-w-margin,margin,w,h)
    }
    fun following(c: Course,seconds: Double): CameraState {
        val p=if(seconds<0)c.eye else c.grenade(seconds) ?: c.path.last().position
        return c.camera(LessonView.FOLLOW).copy(x=p.x,y=p.y,z=p.z,free=false)
    }
    fun landing(c: Course)=c.landingCamera ?: c.overviewCamera
}
