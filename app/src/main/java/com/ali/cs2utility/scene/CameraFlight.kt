package com.ali.cs2utility.scene

/** Pure camera tween; yaw takes the shorter arc and reaches the exact final pose. */
class CameraFlight(val from: CameraState,val to: CameraState,val started: Long,val duration: Long = 850) {
    fun at(now: Long): CameraState {
        val t=((now-started).toFloat()/duration).coerceIn(0f,1f)
        if(t>=1f) return to
        val s=t*t*(3f-2f*t)
        fun mix(a: Float,b: Float)=a+(b-a)*s
        val turn=((to.yaw-from.yaw+540f)%360f)-180f
        return CameraState(from.yaw+turn*s,mix(from.pitch,to.pitch),mix(from.distance,to.distance),
            mix(from.x,to.x),mix(from.z,to.z),mix(from.y,to.y))
    }
    fun finished(now: Long)=now-started>=duration
}
