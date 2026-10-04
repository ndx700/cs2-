package com.ali.cs2utility.scene

import kotlin.math.*

/** Orbit coordinates are a target; free coordinates are the eye. Positive pitch looks down. */
data class CameraState(val yaw: Float,val pitch: Float,val distance: Float,val x: Float,val z: Float,
    val y: Float=0f,val free: Boolean=false,val speed: Float=6f)

object FreeCamera {
    fun look(c: CameraState,dx: Float,dy: Float)=c.copy(yaw=(c.yaw-dx*.25f)%360f,
        pitch=(c.pitch+dy*.25f).coerceIn(-85f,85f))
    fun forward(c: CameraState): FloatArray {
        val yaw=Math.toRadians(c.yaw.toDouble());val pitch=Math.toRadians(c.pitch.toDouble())
        return floatArrayOf((-sin(yaw)*cos(pitch)).toFloat(),(-sin(pitch)).toFloat(),(-cos(yaw)*cos(pitch)).toFloat())
    }
    fun advance(c: CameraState,strafe: Float,forward: Float,vertical: Float,seconds: Float): CameraState {
        if(!c.free)return c
        val length=sqrt(strafe*strafe+forward*forward+vertical*vertical).coerceAtLeast(1f)
        val step=seconds.coerceIn(0f,.1f)*c.speed.coerceIn(3f,12f)/length
        val yaw=Math.toRadians(c.yaw.toDouble())
        // Horizontal running does not dive when looking down; height has dedicated controls.
        return c.copy(x=c.x+(cos(yaw)*strafe-sin(yaw)*forward).toFloat()*step,
            z=c.z+(-sin(yaw)*strafe-cos(yaw)*forward).toFloat()*step,y=c.y+vertical*step)
    }
}

object SceneLoadPolicy {
    fun workerCount(cores: Int)=(cores-2).coerceIn(2,6)
    fun priority(distanceSquared: Float,radius: Float)=distanceSquared/(radius*radius+.25f)
}

/** Capacity stays reserved until GL consumes decoded bytes, including across scene changes. */
class DecodeBudget(private val maxJobs: Int,private val maxBytes: Long) {
    private var jobs=0;private var bytes=0L
    @Synchronized fun acquire(cost: Long): Boolean {
        if(cost<=0 || jobs>=maxJobs || cost>maxBytes-bytes)return false
        jobs++;bytes+=cost;return true
    }
    @Synchronized fun release(cost: Long) {check(jobs>0 && cost in 1..bytes);jobs--;bytes-=cost}
    @Synchronized fun hasCapacity()=jobs<maxJobs && bytes<maxBytes
}
