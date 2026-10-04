package com.ali.cs2utility.tutorial

import android.opengl.GLES20 as GL
import com.ali.cs2utility.domain.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

/** Bounded GLES2 procedural puffs, flames and HE dust. No bitmap decode or asset/GPU worker. */
class CourseFxRenderer {
    private var program=0
    private var position=0;private var rgba=0;private var size=0;private var matrix=0;private var scale=0
    private val data=ByteBuffer.allocateDirect(1024*8*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var maxPointSize=64f
    private data class Puff(val p: Vec3,val r: Float,val red: Float,val green: Float,val blue: Float,val alpha: Float)
    fun contextCreated() {
        // Previous handles belong to a lost EGL context. Do not delete them in the new context.
        program=0
        fun shader(type: Int,source: String): Int {
            val id=GL.glCreateShader(type);GL.glShaderSource(id,source);GL.glCompileShader(id)
            val ok=IntArray(1);GL.glGetShaderiv(id,GL.GL_COMPILE_STATUS,ok,0)
            check(ok[0]!=0) {GL.glGetShaderInfoLog(id)};return id
        }
        val vs=shader(GL.GL_VERTEX_SHADER,"attribute vec3 aP;attribute vec4 aC;attribute float aS;uniform mat4 uVP;uniform vec2 uScale;varying vec4 vC;void main(){vec4 c=uVP*vec4(aP,1.0);gl_Position=c;gl_PointSize=clamp(aS*uScale.x/max(c.w,0.05),1.0,uScale.y);vC=aC;}")
        val fs=shader(GL.GL_FRAGMENT_SHADER,"precision mediump float;varying vec4 vC;void main(){vec2 q=gl_PointCoord*2.0-1.0;float r=dot(q,q);if(r>1.0)discard;gl_FragColor=vec4(vC.rgb,vC.a*(1.0-r)*(1.0-r));}")
        program=GL.glCreateProgram();GL.glAttachShader(program,vs);GL.glAttachShader(program,fs);GL.glLinkProgram(program)
        val ok=IntArray(1);GL.glGetProgramiv(program,GL.GL_LINK_STATUS,ok,0);check(ok[0]!=0){GL.glGetProgramInfoLog(program)}
        GL.glDeleteShader(vs);GL.glDeleteShader(fs)
        position=GL.glGetAttribLocation(program,"aP");rgba=GL.glGetAttribLocation(program,"aC");size=GL.glGetAttribLocation(program,"aS")
        matrix=GL.glGetUniformLocation(program,"uVP");scale=GL.glGetUniformLocation(program,"uScale")
        val limits=FloatArray(2);GL.glGetFloatv(GL.GL_ALIASED_POINT_SIZE_RANGE,limits,0);maxPointSize=limits[1].coerceAtLeast(1f)
    }
    fun dispose() {if(program!=0)GL.glDeleteProgram(program);program=0}
    fun draw(c: Course,t: Double,vp: FloatArray,eye: FloatArray,height: Int,fov: Float) {
        if(program==0)return
        val puffs=ArrayList<Puff>(1024)
        c.smoke.forEach {s->
            val a=CourseEffects.smokeAmount(s,t);val growth=sqrt(a)
            if(a>0)for(i in 0 until 96) {
                // Fixed lattice with time-varying micro-motion; replay/seek are deterministic.
                val u=(i%6-2.5f)/3;val v=(i/6%4-1.5f)/2;val w=(i/24-1.5f)/2
                if(u*u+v*v+w*w>1)continue
                val p=Vec3(s.center.x+(u+.03f*sin((t+i)*2).toFloat())*s.radius.x*growth,
                    s.center.y+v*s.radius.y*growth,s.center.z+w*s.radius.z*growth)
                val density=CourseEffects.density(c,s,p,t)
                if(density>.001f)puffs.add(Puff(p,.75f*growth,.62f,.67f,.7f,density*.7f))
            }
        }
        c.fire.forEachIndexed {i,f->
            val a=CourseEffects.fireAmount(f,t)
            if(a>0)for(j in 0..5) {
                val angle=j*PI/3;val flicker=.7f+.3f*sin((t*13+i*3+j)).toFloat()
                puffs.add(Puff(Vec3(f.center.x+cos(angle).toFloat()*f.radius*.45f,
                    f.center.y+.15f+j*.08f*flicker,f.center.z+sin(angle).toFloat()*f.radius*.45f),
                    f.radius*(.65f+.3f*flicker),1f,.22f+j*.08f,.025f,a*.9f))
            }
        }
        c.he.forEach {h->
            val age=t-h.start
            if(age in 0.0..1.2)for(i in 0..31) {
                val angle=i*2.399963;val radius=h.radius*(age.toFloat()+.1f)
                val p=Vec3(h.center.x+cos(angle).toFloat()*radius,h.center.y+sin(i*1.31).toFloat()*radius*.5f,h.center.z+sin(angle).toFloat()*radius)
                val flash=age<.15
                puffs.add(Puff(p,if(flash).7f else .4f+age.toFloat()*.4f,if(flash)1f else .45f,
                    if(flash).7f else .4f,if(flash).2f else .35f,(1-age/1.2).toFloat()*.8f))
            }
        }
        c.grenade(t)?.let {puffs.add(Puff(it,.12f,.4f,.9f,.5f,1f))}
        if(puffs.isEmpty())return
        check(puffs.size<=1024)
        fun distance(p: Vec3)=(p.x-eye[0]).pow(2)+(p.y-eye[1]).pow(2)+(p.z-eye[2]).pow(2)
        data.clear()
        puffs.sortedByDescending {distance(it.p)}.forEach {p->data.put(floatArrayOf(p.p.x,p.p.y,p.p.z,p.red,p.green,p.blue,p.alpha,p.r*2))}
        data.flip()
        GL.glUseProgram(program);GL.glUniformMatrix4fv(matrix,1,false,vp,0)
        GL.glUniform2f(scale,height/(2*tan(Math.toRadians(fov/2.0))).toFloat(),maxPointSize)
        GL.glEnable(GL.GL_BLEND);GL.glBlendFunc(GL.GL_SRC_ALPHA,GL.GL_ONE_MINUS_SRC_ALPHA);GL.glDepthMask(false)
        GL.glEnableVertexAttribArray(position);GL.glEnableVertexAttribArray(rgba);GL.glEnableVertexAttribArray(size)
        data.position(0);GL.glVertexAttribPointer(position,3,GL.GL_FLOAT,false,32,data)
        data.position(3);GL.glVertexAttribPointer(rgba,4,GL.GL_FLOAT,false,32,data)
        data.position(7);GL.glVertexAttribPointer(size,1,GL.GL_FLOAT,false,32,data)
        GL.glDrawArrays(GL.GL_POINTS,0,puffs.size)
        GL.glDisableVertexAttribArray(position);GL.glDisableVertexAttribArray(rgba);GL.glDisableVertexAttribArray(size)
        GL.glDepthMask(true);GL.glDisable(GL.GL_BLEND)
    }
}
