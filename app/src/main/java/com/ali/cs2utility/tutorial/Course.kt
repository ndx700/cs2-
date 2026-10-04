package com.ali.cs2utility.tutorial

import com.ali.cs2utility.domain.Vec3
import com.ali.cs2utility.scene.CameraState
import kotlin.math.*

enum class LessonView { STANCE, AIM, OVERVIEW }
enum class LessonStage { STANCE, AIM, THROW, FLIGHT, RESULT, ENDED }
data class SourceEvidence(val url: String?, val gameBuild: String?, val releaseVideoSeconds: Double?, val calibrated: Boolean)
data class PathKey(val seconds: Double, val position: Vec3, val bounce: Boolean = false)
data class SmokeEvent(val id: String, val center: Vec3, val radius: Vec3, val start: Double,
    val formed: Double, val fade: Double, val end: Double)
data class FireZone(val id: String, val center: Vec3, val radius: Float, val start: Double, val fade: Double, val end: Double)
data class HeEvent(val id: String, val center: Vec3, val radius: Float, val start: Double,
    val open: Double, val refill: Double, val end: Double, val smokeIds: Set<String>)

/** Immutable data shared by all views. Times are relative to release (zero), never video progress. */
data class Course(val lessonId: String, val relatedIds: List<String>, val title: String,
    val mapVersion: String, val coordinateSpace: String, val evidence: SourceEvidence,
    val foot: Vec3, val eye: Vec3, val aim: Vec3, val bodyYaw: Float, val throwHint: String,
    val stanceCamera: CameraState, val aimCamera: CameraState, val overviewCamera: CameraState,
    val path: List<PathKey>, val smoke: List<SmokeEvent>, val fire: List<FireZone>, val he: List<HeEvent>,
    val begin: Double = -6.0, val aimAt: Double = -3.0, val throwAt: Double = -1.0, val duration: Double, val aimFov: Float=70f,
    val aimAcceptance: AimAcceptance?=null) {
    fun validate(actualMapVersion: String) {
        require(lessonId.isNotBlank() && mapVersion == actualMapVersion) { "课程地图版本不匹配" }
        require(coordinateSpace == "display-m-y-up-v1") { "课程必须使用已转换一次的显示坐标" }
        require(duration.isFinite() && duration in 0.1..300.0 && begin.isFinite() && begin in -60.0..0.0)
        require(begin <= aimAt && aimAt <= throwAt && throwAt <= 0 && bodyYaw.isFinite())
        fun point(p: Vec3) = require(listOf(p.x,p.y,p.z).all { it.isFinite() && abs(it) <= 1000 })
        listOf(foot,eye,aim).forEach(::point)
        listOf(stanceCamera,aimCamera,overviewCamera).forEach { c ->
            require(listOf(c.x,c.y,c.z,c.yaw,c.pitch,c.distance).all { it.isFinite() })
            require(c.distance > 0 && abs(c.pitch) <= 85)
        }
        require(aimFov.isFinite() && aimFov in 30f..110f)
        require(aimCamera.free && !stanceCamera.free && !overviewCamera.free)
        require(path.size in 2..256 && path.first().seconds == 0.0 && path.last().seconds <= duration)
        path.forEach { require(it.seconds.isFinite() && it.seconds >= 0);point(it.position) }
        require(path.zipWithNext().all { (a,b) -> a.seconds < b.seconds })
        require(smoke.size <= 8 && fire.size <= 64 && he.size <= 16)
        require(smoke.size*96+fire.size*6+he.size*32+1<=1024) {"课程超过动态点元预算"}
        val ids=smoke.map {it.id}+fire.map {it.id}+he.map {it.id}
        require(ids.all {it.isNotBlank()} && ids.distinct().size == ids.size)
        fun times(vararg t: Double) { require(t.all {it.isFinite() && it in 0.0..duration});require(t.toList().zipWithNext().all {(a,b)->a<b}) }
        smoke.forEach { point(it.center);point(it.radius);require(minOf(it.radius.x,it.radius.y,it.radius.z)>0);times(it.start,it.formed,it.fade,it.end) }
        fire.forEach { point(it.center);require(it.radius.isFinite() && it.radius in .01f..10f);times(it.start,it.fade,it.end) }
        he.forEach { point(it.center);require(it.radius.isFinite() && it.radius in .01f..20f);times(it.start,it.open,it.refill,it.end);require(it.smokeIds.all {id->smoke.any {s->s.id==id}}) }
        if(evidence.calibrated) {
            require(abs(eye.x-aimCamera.x)<.001f && abs(eye.y-aimCamera.y)<.001f && abs(eye.z-aimCamera.z)<.001f)
            val dx=aim.x-eye.x;val dy=aim.y-eye.y;val dz=aim.z-eye.z
            val length=sqrt(dx*dx+dy*dy+dz*dz);require(length>.01f)
            val forward=com.ali.cs2utility.scene.FreeCamera.forward(aimCamera)
            require((dx*forward[0]+dy*forward[1]+dz*forward[2])/length>.9999f) {"准星朝向与世界瞄点不一致"}
        }
        if(evidence.calibrated) require(!evidence.url.isNullOrBlank() && !evidence.gameBuild.isNullOrBlank() &&
            evidence.releaseVideoSeconds?.let {it.isFinite() && it>=0} == true)
        if(evidence.calibrated) requireNotNull(aimAcceptance) {"正式课程缺少 C013 瞄点贴图验收记录"}.validate(this)
    }
    fun camera(view: LessonView)=when(view) {LessonView.STANCE->stanceCamera;LessonView.AIM->aimCamera;LessonView.OVERVIEW->overviewCamera}
    fun stage(t: Double)=when {t<aimAt->LessonStage.STANCE;t<throwAt->LessonStage.AIM;t<0->LessonStage.THROW;t<path.last().seconds->LessonStage.FLIGHT;t<duration->LessonStage.RESULT;else->LessonStage.ENDED}
    fun grenade(t: Double): Vec3? {
        if(t<0 || t>path.last().seconds)return null
        val index=path.indexOfFirst {it.seconds>=t}.coerceAtLeast(1)
        val a=path[index-1];val b=path[index];val u=((t-a.seconds)/(b.seconds-a.seconds)).toFloat()
        return Vec3(a.position.x+(b.position.x-a.position.x)*u,a.position.y+(b.position.y-a.position.y)*u,a.position.z+(b.position.z-a.position.z)*u)
    }
}

data class PlaybackSnapshot(val lessonId: String?, val seconds: Double, val playing: Boolean, val view: LessonView,
    val aimHintEnabled: Boolean=true,val aimHintStarted: Double?=null)

/** Owned by GL thread; the Activity only reads immutable snapshots. No background wall-clock catchup. */
class CoursePlayer {
    var course: Course? = null; private set
    var seconds=0.0; private set
    var playing=false; private set
    var view=LessonView.STANCE; private set
    private var aimHintEnabled=true
    private var aimHintStarted: Double?=null
    private var lastTick: Long?=null
    private var suspended=false
    fun load(value: Course, actualMapVersion: String) {value.validate(actualMapVersion);course=value;seconds=value.begin;playing=false;view=LessonView.STANCE;aimHintEnabled=true;aimHintStarted=null;lastTick=null}
    fun play() { if(course!=null && seconds < course!!.duration) playing=true;lastTick=null }
    fun pause() {playing=false;lastTick=null}
    fun suspend() {suspended=true;lastTick=null}
    fun resume() {suspended=false;lastTick=null}
    fun replay() {seconds=course?.begin ?: 0.0;playing=course!=null;aimHintStarted=if(view==LessonView.AIM)seconds else null;lastTick=null}
    fun seek(value: Double) {require(value.isFinite());seconds=value.coerceIn(course?.begin ?: 0.0,course?.duration ?: 0.0);lastTick=null}
    fun setView(value: LessonView) {view=value;if(value==LessonView.AIM && aimHintStarted==null)aimHintStarted=seconds}
    fun setAimHint(value: Boolean) {aimHintEnabled=value;if(value)aimHintStarted=if(view==LessonView.AIM)seconds else null}
    fun aimHintAlpha()=AimGuidance.alpha(aimHintEnabled,view,aimHintStarted,seconds)
    fun clear() {course=null;seconds=0.0;playing=false;lastTick=null;view=LessonView.STANCE;aimHintEnabled=true;aimHintStarted=null}
    fun tick(monotonicMillis: Long) {
        val old=lastTick;lastTick=monotonicMillis
        if(playing && !suspended && old!=null && monotonicMillis>=old) {
            seconds=(seconds+(monotonicMillis-old)/1000.0).coerceAtMost(course!!.duration)
            if(seconds>=course!!.duration)playing=false
        }
    }
    fun snapshot()=PlaybackSnapshot(course?.lessonId,seconds,playing,view,aimHintEnabled,aimHintStarted)
    fun restore(value: PlaybackSnapshot) {
        val c=requireNotNull(course);require(value.lessonId==c.lessonId)
        seek(value.seconds);view=value.view;playing=value.playing && seconds<c.duration
        aimHintEnabled=value.aimHintEnabled
        aimHintStarted=value.aimHintStarted?.also {require(it.isFinite())}?.coerceIn(c.begin,seconds)
            ?: if(view==LessonView.AIM)seconds else null
        lastTick=null
    }
}

/** Pure deterministic evaluation: seeking/replaying produces the same state, with no old particle pool. */
object CourseEffects {
    fun smokeAmount(e: SmokeEvent,t: Double): Float=when {
        t<e.start || t>=e.end->0f
        t<e.formed->((t-e.start)/(e.formed-e.start)).toFloat()
        t<e.fade->1f
        else->((e.end-t)/(e.end-e.fade)).toFloat()
    }
    fun fireAmount(e: FireZone,t: Double): Float=when {
        t<e.start || t>=e.end->0f
        t<e.fade->minOf(1.0,(t-e.start)/.4).toFloat()
        else->((e.end-t)/(e.end-e.fade)).toFloat()
    }
    fun holeAmount(e: HeEvent,t: Double): Float=when {
        t<e.start || t>=e.end->0f
        t<e.open->((t-e.start)/(e.open-e.start)).toFloat()
        t<e.refill->1f
        else->((e.end-t)/(e.end-e.refill)).toFloat()
    }
    fun density(c: Course,smoke: SmokeEvent,point: Vec3,t: Double): Float {
        val amount=smokeAmount(smoke,t);if(amount<=0)return 0f
        val r=sqrt(amount)
        val dx=(point.x-smoke.center.x)/(smoke.radius.x*r)
        val dy=(point.y-smoke.center.y)/(smoke.radius.y*r)
        val dz=(point.z-smoke.center.z)/(smoke.radius.z*r)
        val d=dx*dx+dy*dy+dz*dz;if(d>=1)return 0f
        var density=amount*(1-d)
        c.he.filter { smoke.id in it.smokeIds }.forEach {he ->
            val x=point.x-he.center.x;val y=point.y-he.center.y;val z=point.z-he.center.z
            val distance=sqrt(x*x+y*y+z*z)
            val mask=(1-distance/he.radius).coerceIn(0f,1f)
            density*=1-holeAmount(he,t)*mask
        }
        return density
    }
}
