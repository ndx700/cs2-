package com.ali.cs2utility.scene

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLUtils
import android.opengl.GLES20 as GL
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.ali.cs2utility.domain.*
import com.ali.cs2utility.tutorial.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

data class ScreenMarker(val id: String?, val label: String, val x: Float, val y: Float, val color: Int, val landing: Boolean, val target: Boolean = false)

/** Every mutable field below belongs to the GL thread; callers use GLSurfaceView.queueEvent. */
class MapRenderer(private val context: Context, private val requestFrame: () -> Unit, private val onProjection: (List<ScreenMarker>) -> Unit,
    private val onStatus: (String,Boolean) -> Unit = { _,_ -> },
    private val onAimHint: (ProjectedAimHint?) -> Unit = {}) : GLSurfaceView.Renderer {
    private val coursePlayer=CoursePlayer()
    private val courseFx=CourseFxRenderer()
    // Mannequin plus <=256 path keys: reuse storage instead of allocating a direct buffer per frame.
    private val courseLines=ByteBuffer.allocateDirect(640*6*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    @Volatile var savedBrowseCamera: CameraState?=null
        private set
    @Volatile var playback=coursePlayer.snapshot()
        private set
    fun startCourse(value: Course,saved: PlaybackSnapshot?=null) {
        value.validate(loadedMapVersion)
        if(savedBrowseCamera==null)savedBrowseCamera=camera
        coursePlayer.load(value,loadedMapVersion)
        saved?.takeIf {it.lessonId==value.lessonId}?.let(coursePlayer::restore)
        stopMovement();flight=null;setLessonView(coursePlayer.view)
        playback=coursePlayer.snapshot()
    }
    fun setLessonView(value: LessonView) {coursePlayer.setView(value);coursePlayer.course?.let {restore(it.camera(value))};playback=coursePlayer.snapshot()}
    fun coursePlay() {coursePlayer.play();playback=coursePlayer.snapshot()}
    fun coursePause() {coursePlayer.tick(android.os.SystemClock.uptimeMillis());coursePlayer.pause();playback=coursePlayer.snapshot()}
    fun courseReplay() {coursePlayer.replay();playback=coursePlayer.snapshot()}
    fun courseAimHint(value: Boolean) {coursePlayer.setAimHint(value);playback=coursePlayer.snapshot()}
    fun endCourse() {coursePlayer.clear();playback=coursePlayer.snapshot();savedBrowseCamera?.let(::restore);savedBrowseCamera=null}
    private val mobile=MobileSceneRenderer(context,requestFrame,onStatus)
    private var hasMobile=false
    private var loadedMapVersion=""
    private var program = 0
    private var position = 0; private var color = 0; private var uniform = 0
    private var mesh: FloatBuffer? = null; private var count = 0
    private var path: FloatBuffer? = null; private var pathCount = 0
    private var scene: SceneDefinition? = null
    private var detailed: TexturedMesh? = null
    private var textureProgram=0
    private var texturePosition=0; private var textureNormal=0; private var textureUv=0
    private var textureVp=0; private var textureSampler=0; private var textureEnabled=0; private var textureColor=0
    private var texturesDirty=false
    private val textures=HashMap<String,Int>()
    private var lineups = emptyList<Lineup>(); private var selected: Lineup? = null
    private var targets=emptyList<TargetMarker>()
    private var flight: CameraFlight?=null
    private var overviewCamera=true
    private var free=false;private var speed=6f
    private var strafe=0f;private var forward=0f;private var vertical=0f;private var lastFrame=0L
    @Volatile private var paused=false
    private var width = 1; private var height = 1
    private val projection = FloatArray(16); private val view = FloatArray(16); private val vp = FloatArray(16)
    private val frustum=Array(6) { FloatArray(4) }
    private var yaw = 38f; private var pitch = 54f; private var distance = 42f
    private var targetX = 0f; private var targetZ = 0f; private var targetY=0f
    @Volatile var camera = CameraState(yaw,pitch,distance,targetX,targetZ)
        private set
    private fun buffer(data: FloatArray) = ByteBuffer.allocateDirect(data.size*4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(data); position(0) }
    fun setScene(definition: SceneDefinition, vertices: FloatArray) {
        endCourse(); scene = definition; mesh = buffer(vertices); count = vertices.size/6
        reset()
    }
    fun setDetailed(value: TexturedMesh?) {
        if(textures.isNotEmpty()) GL.glDeleteTextures(textures.size,textures.values.toIntArray(),0)
        textures.clear();detailed=value;texturesDirty=true
    }
    fun setMobile(value: MobileScene?) { hasMobile=value!=null;loadedMapVersion=value?.manifestSha256.orEmpty();mobile.setScene(value) }
    fun dispose() {endCourse();courseFx.dispose();mobile.dispose();setDetailed(null)}
    fun shutdownWorkers() {mobile.shutdownWorkers()}
    fun pan(dx: Float,dy: Float) {
        if(free)return
        overviewCamera=false;flight=null
        val r=Math.toRadians(yaw.toDouble());val k=distance*.0015f
        targetX+=(-dx*cos(r)+dy*sin(r)).toFloat()*k
        targetZ+=(dx*sin(r)+dy*cos(r)).toFloat()*k
    }
    fun setTargets(value: List<TargetMarker>) { targets=value }
    fun flyTo(c: CameraState) {
        stopMovement();overviewCamera=false
        if(free) {flight=null;targetX=c.x;targetY=c.y+1.65f;targetZ=c.z;yaw=c.yaw;pitch=0f}
        else flight=CameraFlight(CameraState(yaw,pitch,distance,targetX,targetZ,targetY),c,android.os.SystemClock.uptimeMillis())
        publishCamera()
    }
    private fun publishCamera() {camera=CameraState(yaw,pitch,distance,targetX,targetZ,targetY,free,speed)}
    fun setFreeMode(value: Boolean) {
        if(value==free)return
        stopMovement();flight=null
        if(value) {
            if(overviewCamera) {val p=scene?.labels?.firstOrNull {it.name=="匪家"}?.position ?: scene?.cameraTarget
                p?.let {targetX=it.x;targetY=it.y;targetZ=it.z}}
            targetY+=1.65f;pitch=0f
        } else {pitch=55f;distance=35f;targetY-=1.65f}
        free=value;overviewCamera=false;publishCamera()
    }
    fun movement(x: Float,z: Float,y: Float) {strafe=x;forward=z;vertical=y}
    fun stopMovement() {strafe=0f;forward=0f;vertical=0f;lastFrame=0L}
    fun setSpeed(value: Float) {speed=value.coerceIn(3f,12f);publishCamera()}
    fun pauseMovement() {paused=true}
    fun resumeMovement() {paused=false;lastFrame=0L;stopMovement();coursePlayer.resume()}
    fun suspendCourse() {coursePlayer.suspend();playback=coursePlayer.snapshot()}
    fun setLineups(items: List<Lineup>, item: Lineup?) {
        lineups = items; selected = item
        path = item?.takeIf { it.landing!=null }?.let { lineup ->
            val landing=requireNotNull(lineup.landing)
            val data = ArrayList<Float>()
            for (i in 0..48) {
                val t = i/48f
                data.add(lineup.stand.x*(1-t)+landing.x*t)
                data.add(lineup.stand.y*(1-t)+landing.y*t + 4f*t*(1-t)*5f + .1f)
                data.add(lineup.stand.z*(1-t)+landing.z*t)
                data.addAll(listOf(.94f,.75f,.47f))
            }
            pathCount = 49; buffer(data.toFloatArray())
        }
    }
    fun rotate(dx: Float, dy: Float) {
        overviewCamera=false;flight=null
        if(free) {val c=FreeCamera.look(camera.copy(yaw=yaw,pitch=pitch),dx,dy);yaw=c.yaw;pitch=c.pitch}
        else {yaw+=dx*.35f;pitch=(pitch+dy*.25f).coerceIn(20f,85f)}
    }
    fun zoom(factor: Float) { if(free)return;overviewCamera=false;flight=null;val e = scene?.extent ?: 28f; distance = (distance/factor).coerceIn(scene?.minimumDistance ?: 6f, e*3.2f) }
    fun focus(point: Vec3) { overviewCamera=false;targetX=point.x; targetZ=point.z; targetY=point.y; distance=(scene?.extent ?: 28f)*.65f }
    private fun overviewDistance(): Float {
        val e=scene?.extent ?: 28f
        return (e*1.55f/(width.toFloat()/height).coerceIn(.5f,1f)).coerceAtMost(e*3.2f)
    }
    fun reset() {
        flight=null;overviewCamera=true;free=false;stopMovement()
        yaw=38f; pitch=65f; distance=overviewDistance()
        val center=scene?.cameraTarget ?: Vec3(0f,0f,0f)
        targetX=center.x; targetY=center.y; targetZ=center.z;publishCamera()
    }
    fun restore(c: CameraState) {
        flight=null;overviewCamera=false
        if (listOf(c.yaw,c.pitch,c.distance,c.x,c.z,c.y,c.speed).all { it.isFinite() }) {
            free=c.free;speed=c.speed.coerceIn(3f,12f);stopMovement();yaw=c.yaw; pitch=c.pitch.coerceIn(if(free)-85f else 20f,85f); distance=c.distance.coerceAtLeast(1f); targetX=c.x; targetZ=c.z; targetY=c.y;publishCamera()
        }
    }
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val v = shader(GL.GL_VERTEX_SHADER, "attribute vec3 aPosition; attribute vec3 aColor; uniform mat4 uVP; varying vec3 vColor; void main(){ gl_Position=uVP*vec4(aPosition,1.0); vColor=aColor; }")
        val f = shader(GL.GL_FRAGMENT_SHADER, "precision mediump float; varying vec3 vColor; void main(){ gl_FragColor=vec4(vColor,1.0); }")
        program=GL.glCreateProgram(); GL.glAttachShader(program,v); GL.glAttachShader(program,f); GL.glLinkProgram(program)
        val ok=IntArray(1); GL.glGetProgramiv(program,GL.GL_LINK_STATUS,ok,0)
        check(ok[0]!=0) { GL.glGetProgramInfoLog(program) }
        GL.glDeleteShader(v); GL.glDeleteShader(f)
        position=GL.glGetAttribLocation(program,"aPosition"); color=GL.glGetAttribLocation(program,"aColor"); uniform=GL.glGetUniformLocation(program,"uVP")
        GL.glEnable(GL.GL_DEPTH_TEST); GL.glClearColor(.071f,.095f,.13f,1f)
        val tv=shader(GL.GL_VERTEX_SHADER,"attribute vec3 aPosition; attribute vec3 aNormal; attribute vec2 aUV; uniform mat4 uVP; varying vec2 vUV; varying float vLight; void main(){gl_Position=uVP*vec4(aPosition,1.0);vUV=aUV;vLight=0.65+0.35*abs(dot(normalize(aNormal),normalize(vec3(0.3,0.8,0.5))));}")
        val tf=shader(GL.GL_FRAGMENT_SHADER,"precision mediump float; uniform sampler2D uTexture; uniform int uTextured; uniform vec3 uColor; varying vec2 vUV; varying float vLight; void main(){vec4 c=uTextured==1?texture2D(uTexture,vUV):vec4(uColor,1.0);if(c.a<0.4)discard;gl_FragColor=vec4(c.rgb*vLight,1.0);}")
        textureProgram=GL.glCreateProgram();GL.glAttachShader(textureProgram,tv);GL.glAttachShader(textureProgram,tf);GL.glLinkProgram(textureProgram)
        GL.glGetProgramiv(textureProgram,GL.GL_LINK_STATUS,ok,0);check(ok[0]!=0) { GL.glGetProgramInfoLog(textureProgram) }
        texturePosition=GL.glGetAttribLocation(textureProgram,"aPosition")
        textureNormal=GL.glGetAttribLocation(textureProgram,"aNormal")
        textureUv=GL.glGetAttribLocation(textureProgram,"aUV")
        textureVp=GL.glGetUniformLocation(textureProgram,"uVP")
        textureSampler=GL.glGetUniformLocation(textureProgram,"uTexture")
        textureEnabled=GL.glGetUniformLocation(textureProgram,"uTextured")
        textureColor=GL.glGetUniformLocation(textureProgram,"uColor")
        GL.glDeleteShader(tv);GL.glDeleteShader(tf);textures.clear();texturesDirty=true
        mobile.contextCreated();courseFx.contextCreated()
    }
    private fun shader(type: Int, source: String): Int {
        val id=GL.glCreateShader(type); GL.glShaderSource(id,source); GL.glCompileShader(id)
        val ok=IntArray(1); GL.glGetShaderiv(id,GL.GL_COMPILE_STATUS,ok,0)
        check(ok[0]!=0) { GL.glGetShaderInfoLog(id) }; return id
    }
    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width=w.coerceAtLeast(1); height=h.coerceAtLeast(1)
        if(overviewCamera) distance=overviewDistance()
        GL.glViewport(0,0,width,height)
    }
    override fun onDrawFrame(gl: GL10?) {
        val tick=android.os.SystemClock.uptimeMillis()
        if(paused) {stopMovement();coursePlayer.suspend()}
        coursePlayer.tick(tick);playback=coursePlayer.snapshot()
        if(free && lastFrame!=0L && !paused) {
            val c=FreeCamera.advance(CameraState(yaw,pitch,distance,targetX,targetZ,targetY,true,speed),strafe,forward,vertical,(tick-lastFrame)/1000f)
            targetX=c.x;targetY=c.y;targetZ=c.z
        }
        lastFrame=tick
        flight?.let { f ->
            val now=android.os.SystemClock.uptimeMillis();val c=f.at(now)
            yaw=c.yaw;pitch=c.pitch;distance=c.distance;targetX=c.x;targetY=c.y;targetZ=c.z
            if(f.finished(now)) flight=null
        }
        GL.glClear(GL.GL_COLOR_BUFFER_BIT or GL.GL_DEPTH_BUFFER_BIT)
        val e=scene?.extent ?: 28f
        val fov=if(coursePlayer.course!=null && coursePlayer.view==LessonView.AIM)coursePlayer.course!!.aimFov else if(free)70f else 45f
        Matrix.perspectiveM(projection,0,fov,width.toFloat()/height,if(free).05f else .1f,e*10f)
        val y=Math.toRadians(yaw.toDouble()); val p=Math.toRadians(pitch.toDouble())
        val eye=if(free)floatArrayOf(targetX,targetY,targetZ) else floatArrayOf(targetX+(distance*cos(p)*sin(y)).toFloat(),targetY+(distance*sin(p)).toFloat(),targetZ+(distance*cos(p)*cos(y)).toFloat())
        val look=if(free)FreeCamera.forward(CameraState(yaw,pitch,distance,targetX,targetZ,targetY,true)) else floatArrayOf(targetX-eye[0],targetY-eye[1],targetZ-eye[2])
        Matrix.setLookAtM(view,0,eye[0],eye[1],eye[2],eye[0]+look[0],eye[1]+look[1],eye[2]+look[2],0f,1f,0f)
        Matrix.multiplyMM(vp,0,projection,0,view,0)
        for(axis in 0..2) for(signIndex in 0..1) {
            val sign=if(signIndex==0) 1f else -1f;val plane=frustum[axis*2+signIndex]
            for(i in 0..3) plane[i]=vp[i*4+3]+sign*vp[i*4+axis]
            val length=sqrt(plane[0]*plane[0]+plane[1]*plane[1]+plane[2]*plane[2])
            if(length>0f) for(i in 0..3) plane[i]/=length
        }
        GL.glUseProgram(program); GL.glUniformMatrix4fv(uniform,1,false,vp,0)
        if(hasMobile) mobile.draw(vp,frustum,eye,flight!=null || (free && (strafe!=0f || forward!=0f || vertical!=0f)))
        else {
            mesh?.let { draw(it,count,GL.GL_TRIANGLES) }
            detailed?.let { drawDetailed(it) }
        }
        GL.glUseProgram(program); GL.glUniformMatrix4fv(uniform,1,false,vp,0)
        if(coursePlayer.course==null)path?.let { GL.glLineWidth(2f); draw(it,pathCount,GL.GL_LINE_STRIP) }
        coursePlayer.course?.let {c ->
            // Simple teaching mannequin/foot circle; resource-side rig and final poses remain pending.
            val lines=ArrayList<Float>()
            fun vertex(p: Vec3) {lines.addAll(listOf(p.x,p.y,p.z,.3f,.9f,.8f))}
            fun at(x: Float,y: Float,z: Float=0f): Vec3 {
                val r=Math.toRadians(c.bodyYaw.toDouble())
                return Vec3(c.foot.x+(x*cos(r)-z*sin(r)).toFloat(),c.foot.y+y,c.foot.z+(x*sin(r)+z*cos(r)).toFloat())
            }
            fun line(a: Vec3,b: Vec3) {vertex(a);vertex(b)}
            if(coursePlayer.view!=LessonView.AIM) {
                line(at(0f,.1f),at(0f,1.75f));line(at(0f,.8f),at(-.25f,0f));line(at(0f,.8f),at(.25f,0f))
                val arm=if(coursePlayer.seconds in c.throwAt..0.0)1.7f else 1.1f
                line(at(0f,1.4f),at(.4f,arm));line(at(0f,1.4f),at(-.4f,1.1f))
                for(i in 0..31) {val a=i*2*PI/32;val b=(i+1)*2*PI/32;line(at(cos(a).toFloat()*.35f,.03f,sin(a).toFloat()*.35f),at(cos(b).toFloat()*.35f,.03f,sin(b).toFloat()*.35f))}
            }
            if(coursePlayer.seconds>=0) {
                val trail=c.path.filter {it.seconds<=coursePlayer.seconds}.map {it.position}+listOfNotNull(c.grenade(coursePlayer.seconds))
                trail.zipWithNext().forEach {(a,b)->line(a,b)}
            }
            if(lines.isNotEmpty()) {
                check(lines.size<=courseLines.capacity());courseLines.clear();courseLines.put(lines.toFloatArray());courseLines.flip()
                GL.glLineWidth(2f);draw(courseLines,lines.size/6,GL.GL_LINES)
            }
            courseFx.draw(c,coursePlayer.seconds,vp,eye,height,fov)
        }
        val markers=ArrayList<ScreenMarker>()
        scene?.labels?.forEach { project(it.position)?.let { xy -> markers.add(ScreenMarker(null,it.name,xy[0],xy[1],0xffe5dac8.toInt(),false)) } }
        targets.forEach { target ->
            project(target.position)?.let { xy -> markers.add(ScreenMarker("target:${target.id}",target.label,xy[0],xy[1],0xff85baff.toInt(),false,true)) }
        }
        lineups.forEach { lineup ->
            lineup.modelStand?.let(::project)?.let { xy -> markers.add(ScreenMarker(lineup.id,"${lineup.spawnNumber}",xy[0],xy[1],lineup.type.color,false)) }
        }
        selected?.let { lineup -> lineup.landing?.let(::project)?.let { xy -> markers.add(ScreenMarker(lineup.id,"落点",xy[0],xy[1],0xffff9173.toInt(),true)) } }
        publishCamera()
        onProjection(markers)
        onAimHint(coursePlayer.course?.let {c->AimGuidance.project(c.lessonId,c.aim,vp,width,height,coursePlayer.aimHintAlpha())})
        if((coursePlayer.course!=null && coursePlayer.playing && !paused) || flight!=null || (free && !paused && (strafe!=0f || forward!=0f || vertical!=0f))) requestFrame()
    }
    private fun drawDetailed(value: TexturedMesh) {
        if(texturesDirty) {
            if(textures.isNotEmpty()) GL.glDeleteTextures(textures.size,textures.values.toIntArray(),0)
            textures.clear()
            value.parts.map { it.texture }.filter { it.isNotBlank() }.distinct().forEach { path ->
                val bitmap=context.assets.open(path).use { BitmapFactory.decodeStream(it) }
                if(bitmap!=null) {
                    val ids=IntArray(1);GL.glGenTextures(1,ids,0);GL.glBindTexture(GL.GL_TEXTURE_2D,ids[0])
                    GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_LINEAR_MIPMAP_LINEAR)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_LINEAR)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,GL.GL_REPEAT)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_REPEAT)
                    GLUtils.texImage2D(GL.GL_TEXTURE_2D,0,bitmap,0);GL.glGenerateMipmap(GL.GL_TEXTURE_2D)
                    bitmap.recycle();textures[path]=ids[0]
                }
            }
            texturesDirty=false
        }
        GL.glUseProgram(textureProgram)
        GL.glUniformMatrix4fv(textureVp,1,false,vp,0)
        GL.glUniform1i(textureSampler,0)
        val pos=texturePosition
        val norm=textureNormal
        val uv=textureUv
        value.parts.forEach { part ->
            if(frustum.any { p -> p[0]*part.center[0]+p[1]*part.center[1]+p[2]*part.center[2]+p[3] < -part.radius }) return@forEach
            part.vertices.position(0);GL.glVertexAttribPointer(pos,3,GL.GL_FLOAT,false,32,part.vertices);GL.glEnableVertexAttribArray(pos)
            part.vertices.position(3);GL.glVertexAttribPointer(norm,3,GL.GL_FLOAT,false,32,part.vertices);GL.glEnableVertexAttribArray(norm)
            part.vertices.position(6);GL.glVertexAttribPointer(uv,2,GL.GL_FLOAT,false,32,part.vertices);GL.glEnableVertexAttribArray(uv)
            val texture=textures[part.texture] ?: 0
            GL.glActiveTexture(GL.GL_TEXTURE0);GL.glBindTexture(GL.GL_TEXTURE_2D,texture)
            GL.glUniform1i(textureEnabled,if(texture==0) 0 else 1)
            GL.glUniform3fv(textureColor,1,part.color,0)
            part.indices.position(0);GL.glDrawElements(GL.GL_TRIANGLES,part.indexCount,GL.GL_UNSIGNED_SHORT,part.indices)
        }
        GL.glDisableVertexAttribArray(pos);GL.glDisableVertexAttribArray(norm);GL.glDisableVertexAttribArray(uv)
    }
    private fun draw(buffer: FloatBuffer, n: Int, mode: Int) {
        if(n<=0) return
        buffer.position(0); GL.glVertexAttribPointer(position,3,GL.GL_FLOAT,false,24,buffer); GL.glEnableVertexAttribArray(position)
        buffer.position(3); GL.glVertexAttribPointer(color,3,GL.GL_FLOAT,false,24,buffer); GL.glEnableVertexAttribArray(color)
        GL.glDrawArrays(mode,0,n)
    }
    private fun project(point: Vec3): FloatArray? {
        val clip=FloatArray(4); Matrix.multiplyMV(clip,0,vp,0,floatArrayOf(point.x,point.y+.3f,point.z,1f),0)
        if (clip[3]<=0f) return null
        val x=clip[0]/clip[3]; val y=clip[1]/clip[3]; val z=clip[2]/clip[3]
        if (z !in -1f..1f || x !in -1.15f..1.15f || y !in -1.15f..1.15f) return null
        return floatArrayOf((x+1)*width/2,(1-y)*height/2)
    }
}
