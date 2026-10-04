package com.ali.cs2utility.tutorial

import com.ali.cs2utility.domain.Vec3
import kotlin.math.abs

data class ProjectedAimHint(val lessonId: String,val x: Float,val y: Float,val alpha: Float)

/** Exact world point projection, without the height offset used for map text labels. */
object AimGuidance {
    fun alpha(enabled: Boolean,view: LessonView,started: Double?,seconds: Double): Float {
        if(!enabled || view!=LessonView.AIM || started==null)return 0f
        val age=(seconds-started).coerceAtLeast(0.0)
        return ((2.5-age)/1.0).coerceIn(0.0,1.0).toFloat()
    }
    fun project(lessonId: String,point: Vec3,vp: FloatArray,width: Int,height: Int,alpha: Float): ProjectedAimHint? {
        require(vp.size==16 && width>0 && height>0)
        val v=floatArrayOf(point.x,point.y,point.z,1f)
        val c=FloatArray(4) {row->(0..3).sumOf {col->(vp[col*4+row]*v[col]).toDouble()}.toFloat()}
        if(c.any {!it.isFinite()} || c[3]<=0f || !alpha.isFinite() || alpha<=0f)return null
        val x=c[0]/c[3];val y=c[1]/c[3];val z=c[2]/c[3]
        if(x !in -1f..1f || y !in -1f..1f || z !in -1f..1f)return null
        return ProjectedAimHint(lessonId,(x+1)*width/2,(1-y)*height/2,alpha.coerceIn(0f,1f))
    }
}

/** Structural evidence gate, not an automatic judgment of screenshot truth or teaching accuracy. */
data class AimAcceptance(val mapVersion: String,val appBuild: String,val referenceDescription: String,
    val cs2Frame: String,val appHintVisibleFrame: String,val appHintHiddenFrame: String,
    val localAssetAudit: String,val phoneDevice: String,val viewportWidth: Int,val viewportHeight: Int,
    val verticalFov: Float,val stanceContactReadable: Boolean,val referenceConsistent: Boolean,
    val phoneReadableWithoutHint: Boolean) {
    fun validate(course: Course) {
        require(mapVersion==course.mapVersion) {"瞄点验收证据的资源版本不匹配"}
        require(listOf(appBuild,referenceDescription,cs2Frame,appHintVisibleFrame,appHintHiddenFrame,localAssetAudit,phoneDevice)
            .all {it.isNotBlank() && it.lowercase() !in setOf("pending","待补","未测")}) {"正式课程缺少瞄点验收证据索引"}
        require(appHintVisibleFrame!=appHintHiddenFrame) {"提示显示与隐藏必须分别记录画面"}
        require(viewportWidth in 128..8192 && viewportHeight in 128..8192)
        require(verticalFov.isFinite() && abs(verticalFov-course.aimFov)<.001f)
        require(stanceContactReadable && referenceConsistent && phoneReadableWithoutHint) {"正式课程瞄点检查尚未通过"}
    }
}
