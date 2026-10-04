package com.ali.cs2utility.scene

import android.content.Context
import android.graphics.*
import android.opengl.GLSurfaceView
import android.view.*
import android.widget.FrameLayout
import com.ali.cs2utility.domain.*
import kotlin.math.hypot
import com.ali.cs2utility.tutorial.*

class MapSceneView(context: Context, private val onSelect: (String) -> Unit) : FrameLayout(context) {
    private val density=resources.displayMetrics.density
    private val overlay=MarkerOverlay(context)
    private val gl=GLSurfaceView(context)
    private val controls=RunControls(context)
    var lessonActive=false
        private set
    private var lessonView=LessonView.STANCE
    var freeMode=false
        private set
    var onModeChanged: (Boolean) -> Unit = {}
    private val roles=HashMap<Int,String>()
    private val pointerPositions=HashMap<Int,Pair<Float,Float>>()
    var onStatus: (String,Boolean) -> Unit = { _,_ -> }
    private val renderer=MapRenderer(context,{ gl.requestRender() },{ markers -> post { overlay.markers=markers; overlay.invalidate() } },{ message,error -> post { onStatus(message,error) } })
    private var selectedId: String? = null
    private var moved=false; private var multitouch=false; private var lastX=0f; private var lastY=0f
    private var downX=0f; private var downY=0f
    private var centroidX=0f;private var centroidY=0f
    private val slop=ViewConfiguration.get(context).scaledTouchSlop
    private val scale=ScaleGestureDetector(context,object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val factor=detector.scaleFactor
            gl.queueEvent { renderer.zoom(factor) }; gl.requestRender(); return true
        }
    })
    init {
        gl.setEGLContextClientVersion(2)
        gl.preserveEGLContextOnPause=true
        gl.setRenderer(renderer); gl.renderMode=GLSurfaceView.RENDERMODE_WHEN_DIRTY
        addView(gl,LayoutParams(-1,-1)); addView(overlay,LayoutParams(-1,-1));addView(controls,LayoutParams(-1,-1))
        controls.visibility=View.GONE
        contentDescription="沙二 3D 地图。单指旋转，双指缩放和拖动，点击全图复位。"
    }
    fun startCourse(c: Course,saved: PlaybackSnapshot?=null) {
        cancelInput();updateMode(false);lessonActive=true;lessonView=saved?.view ?: LessonView.STANCE
        overlay.crosshair=lessonView==LessonView.AIM;overlay.invalidate()
        gl.queueEvent {renderer.startCourse(c,saved)};gl.requestRender()
    }
    fun lessonCamera(value: LessonView) {if(!lessonActive)return;cancelInput();lessonView=value;overlay.crosshair=value==LessonView.AIM;overlay.invalidate();gl.queueEvent {renderer.setLessonView(value)};gl.requestRender()}
    fun lessonPlay() {gl.queueEvent {renderer.coursePlay()};gl.requestRender()}
    fun lessonPause() {gl.queueEvent {renderer.coursePause()};gl.requestRender()}
    fun lessonReplay() {gl.queueEvent {renderer.courseReplay()};gl.requestRender()}
    fun lessonSnapshot()=renderer.playback
    fun endCourse() {if(!lessonActive)return;lessonActive=false;updateMode(renderer.savedBrowseCamera?.free ?: false);overlay.crosshair=false;overlay.invalidate();gl.queueEvent {renderer.endCourse()};gl.requestRender()}
    fun load(scene: SceneDefinition, mesh: FloatArray, detailed: TexturedMesh? = null,mobile: MobileScene?=null) {
        endCourse();cancelInput();updateMode(false)
        gl.queueEvent { renderer.setScene(scene,mesh);renderer.setDetailed(detailed);renderer.setMobile(mobile) }; gl.requestRender()
    }
    fun show(items: List<Lineup>, selected: Lineup?) {
        selectedId=selected?.id; overlay.selectedId=selectedId
        gl.queueEvent { renderer.setLineups(items,selected) }; gl.requestRender()
    }
    fun focus(point: Vec3) { gl.queueEvent { renderer.focus(point) }; gl.requestRender() }
    fun targets(items: List<TargetMarker>) { gl.queueEvent { renderer.setTargets(items) };gl.requestRender() }
    fun flyTo(c: CameraState) { endCourse();cancelInput();gl.queueEvent { renderer.flyTo(c) };gl.requestRender() }
    fun resetCamera() { endCourse();cancelInput();updateMode(false);gl.queueEvent { renderer.reset() }; gl.requestRender() }
    fun cameraState()=renderer.savedBrowseCamera ?: renderer.camera
    fun restoreCamera(c: CameraState) { cancelInput();controls.speed=c.speed;updateMode(c.free);gl.queueEvent { renderer.restore(c) }; gl.requestRender() }
    private fun updateMode(value: Boolean) {freeMode=value;controls.visibility=if(value)View.VISIBLE else View.GONE;onModeChanged(value)}
    fun toggleFreeMode() {endCourse();cancelInput();updateMode(!freeMode);val value=freeMode;gl.queueEvent {renderer.setFreeMode(value)};gl.requestRender()}
    private fun cancelInput() {roles.clear();pointerPositions.clear();controls.clear();gl.queueEvent {renderer.stopMovement()};gl.requestRender()}
    fun resume() {gl.onResume();gl.queueEvent {renderer.resumeMovement()};gl.requestRender()}
    fun pause() {renderer.pauseMovement();cancelInput();gl.queueEvent {renderer.suspendCourse()};gl.onPause()}
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {super.onWindowFocusChanged(hasWindowFocus);if(!hasWindowFocus)cancelInput()}
    fun dispose() {renderer.shutdownWorkers();gl.queueEvent {renderer.dispose()};gl.requestRender()}
    override fun onInterceptTouchEvent(event: MotionEvent)=true
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if(lessonActive && lessonView==LessonView.AIM)return true
        if(freeMode)return runTouch(event)
        scale.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                moved=false; multitouch=false; downX=event.x; downY=event.y; lastX=event.x; lastY=event.y
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multitouch=true;moved=true
                centroidX=(event.getX(0)+event.getX(1))/2;centroidY=(event.getY(0)+event.getY(1))/2
            }
            MotionEvent.ACTION_MOVE -> {
                if (hypot(event.x-downX,event.y-downY)>slop) moved=true
                if(event.pointerCount>=2) {
                    val cx=(event.getX(0)+event.getX(1))/2;val cy=(event.getY(0)+event.getY(1))/2
                    val dx=(cx-centroidX)/density;val dy=(cy-centroidY)/density
                    gl.queueEvent {renderer.pan(dx,dy)};gl.requestRender();centroidX=cx;centroidY=cy
                } else if (!scale.isInProgress && !multitouch && moved) {
                    val dx=(event.x-lastX)/density; val dy=(event.y-lastY)/density
                    gl.queueEvent { renderer.rotate(dx,dy) }; gl.requestRender()
                }
                lastX=event.x; lastY=event.y
            }
            MotionEvent.ACTION_UP -> {
                if (!moved && !multitouch) {
                    overlay.markers.filter { it.id!=null }.filter {
                        if(it.target) kotlin.math.abs(it.x-event.x)<=56*density && kotlin.math.abs(it.y-event.y)<=24*density
                        else hypot(it.x-event.x,it.y-event.y)<=24*density
                    }.minByOrNull { hypot(it.x-event.x,it.y-event.y) }?.id?.let(onSelect)
                    performClick()
                }
                parent.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    private fun runTouch(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN -> {
                if(event.actionMasked==MotionEvent.ACTION_DOWN)cancelInput()
                parent.requestDisallowInterceptTouchEvent(true)
                val index=event.actionIndex;val id=event.getPointerId(index)
                val x=event.getX(index);val y=event.getY(index)
                val hit=controls.hit(x,y) ?: "look"
                if(hit=="speed") {
                    controls.speed=when(controls.speed) {3f->6f;6f->12f;else->3f};controls.invalidate()
                    val value=controls.speed;gl.queueEvent {renderer.setSpeed(value)}
                    roles[id]="ignored"
                } else {
                    roles[id]=if(hit in roles.values)"ignored" else hit
                    if(roles[id]=="stick")controls.stick(x,y)
                }
                pointerPositions[id]=Pair(x,y)
            }
            MotionEvent.ACTION_MOVE -> for(index in 0 until event.pointerCount) {
                val id=event.getPointerId(index);val x=event.getX(index);val y=event.getY(index)
                when(roles[id]) {
                    "stick"->controls.stick(x,y)
                    "look"->pointerPositions[id]?.let {last ->
                        val dx=(x-last.first)/density;val dy=(y-last.second)/density
                        gl.queueEvent {renderer.rotate(dx,dy)}
                    }
                }
                pointerPositions[id]=Pair(x,y)
            }
            MotionEvent.ACTION_POINTER_UP,MotionEvent.ACTION_UP -> {
                val id=event.getPointerId(event.actionIndex)
                if(roles.remove(id)=="stick")controls.clear()
                pointerPositions.remove(id)
                if(event.actionMasked==MotionEvent.ACTION_UP) {cancelInput();parent.requestDisallowInterceptTouchEvent(false)}
            }
            MotionEvent.ACTION_CANCEL -> {cancelInput();parent.requestDisallowInterceptTouchEvent(false)}
        }
        val x=if("stick" in roles.values)controls.stickX else 0f
        val z=if("stick" in roles.values)-controls.stickY else 0f
        val y=(if("up" in roles.values)1f else 0f)-(if("down" in roles.values)1f else 0f)
        gl.queueEvent {renderer.movement(x,z,y)};gl.requestRender()
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private class MarkerOverlay(context: Context) : View(context) {
        var crosshair=false
        var markers=emptyList<ScreenMarker>(); var selectedId: String?=null
        private val d=resources.displayMetrics.density
        private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            if(crosshair) {
                paint.color=Color.WHITE;paint.strokeWidth=2*d
                val x=width/2f;val y=height/2f
                c.drawLine(x-10*d,y,x-3*d,y,paint);c.drawLine(x+3*d,y,x+10*d,y,paint)
                c.drawLine(x,y-10*d,x,y-3*d,paint);c.drawLine(x,y+3*d,x,y+10*d,paint)
                return
            }
            markers.sortedBy { if(it.id==selectedId) 1 else 0 }.forEach { m ->
                if(m.target) {
                    paint.style=Paint.Style.FILL;paint.color=0xff142a42.toInt()
                    c.drawRoundRect(m.x-55*d,m.y-22*d,m.x+55*d,m.y+22*d,14*d,14*d,paint)
                    paint.style=Paint.Style.STROKE;paint.strokeWidth=2*d;paint.color=m.color
                    c.drawRoundRect(m.x-55*d,m.y-22*d,m.x+55*d,m.y+22*d,14*d,14*d,paint)
                    paint.style=Paint.Style.FILL;paint.color=m.color;paint.textSize=13*d
                    paint.typeface=Typeface.DEFAULT_BOLD;paint.textAlign=Paint.Align.CENTER
                    c.drawText(m.label,m.x,m.y-(paint.ascent()+paint.descent())/2,paint)
                } else if(m.id==null) {
                    paint.color=0xbb10151d.toInt(); paint.style=Paint.Style.FILL
                    paint.textSize=11*d; paint.typeface=Typeface.DEFAULT_BOLD
                    val w=paint.measureText(m.label)/2+6*d
                    c.drawRoundRect(m.x-w,m.y-12*d,m.x+w,m.y+5*d,4*d,4*d,paint)
                    paint.color=m.color; paint.textAlign=Paint.Align.CENTER; c.drawText(m.label,m.x,m.y,paint)
                } else {
                    val r=if(m.landing) 19*d else 15*d
                    paint.style=Paint.Style.FILL; paint.color=0xff10151d.toInt(); c.drawCircle(m.x,m.y,r+2*d,paint)
                    paint.color=m.color; c.drawCircle(m.x,m.y,r,paint)
                    if(m.id==selectedId) {
                        paint.style=Paint.Style.STROKE; paint.strokeWidth=2*d; paint.color=Color.WHITE
                        c.drawCircle(m.x,m.y,r+5*d,paint); paint.style=Paint.Style.FILL
                    }
                    paint.color=0xff10151d.toInt(); paint.textSize=if(m.landing) 11*d else 13*d
                    paint.textAlign=Paint.Align.CENTER; paint.typeface=Typeface.DEFAULT_BOLD
                    c.drawText(m.label,m.x,m.y-(paint.ascent()+paint.descent())/2,paint)
                }
            }
        }
    }
}
