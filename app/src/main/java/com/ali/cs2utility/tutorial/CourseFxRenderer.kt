package com.ali.cs2utility.tutorial

import android.opengl.GLES20 as GL
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Bounded GLES2 procedural billboard puffs, flames and HE dust. No bitmap decode or asset/GPU worker. */
class CourseFxRenderer {
    private var program=0
    private var position=0;private var rgba=0;private var uv=0;private var matrix=0
    private val data=ByteBuffer.allocateDirect(CourseFxGeometry.CAPACITY*4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    fun contextCreated() {
        // Previous handles belong to a lost EGL context. Do not delete them in the new context.
        program=0
        fun shader(type: Int,source: String): Int {
            val id=GL.glCreateShader(type);GL.glShaderSource(id,source);GL.glCompileShader(id)
            val ok=IntArray(1);GL.glGetShaderiv(id,GL.GL_COMPILE_STATUS,ok,0)
            check(ok[0]!=0) {GL.glGetShaderInfoLog(id)};return id
        }
        val vs=shader(GL.GL_VERTEX_SHADER,"attribute vec3 aP;attribute vec4 aC;attribute vec2 aUV;uniform mat4 uVP;varying vec4 vC;varying vec2 vQ;void main(){gl_Position=uVP*vec4(aP,1.0);vC=aC;vQ=aUV;}")
        val fs=shader(GL.GL_FRAGMENT_SHADER,"precision mediump float;varying vec4 vC;varying vec2 vQ;void main(){float r=dot(vQ,vQ);if(r>1.0)discard;gl_FragColor=vec4(vC.rgb,vC.a*(1.0-r)*(1.0-r));}")
        program=GL.glCreateProgram();GL.glAttachShader(program,vs);GL.glAttachShader(program,fs);GL.glLinkProgram(program)
        val ok=IntArray(1);GL.glGetProgramiv(program,GL.GL_LINK_STATUS,ok,0);check(ok[0]!=0){GL.glGetProgramInfoLog(program)}
        GL.glDeleteShader(vs);GL.glDeleteShader(fs)
        position=GL.glGetAttribLocation(program,"aP");rgba=GL.glGetAttribLocation(program,"aC");uv=GL.glGetAttribLocation(program,"aUV")
        matrix=GL.glGetUniformLocation(program,"uVP")
    }
    fun dispose() {if(program!=0)GL.glDeleteProgram(program);program=0}
    fun draw(c: Course,t: Double,vp: FloatArray,eye: FloatArray,view: FloatArray) {
        if(program==0)return
        val vertices=CourseFxGeometry.fill(c,t,eye,view,data)
        if(vertices==0)return
        GL.glUseProgram(program);GL.glUniformMatrix4fv(matrix,1,false,vp,0)
        GL.glEnable(GL.GL_BLEND);GL.glBlendFunc(GL.GL_SRC_ALPHA,GL.GL_ONE_MINUS_SRC_ALPHA);GL.glDepthMask(false)
        GL.glEnableVertexAttribArray(position);GL.glEnableVertexAttribArray(rgba);GL.glEnableVertexAttribArray(uv)
        data.position(0);GL.glVertexAttribPointer(position,3,GL.GL_FLOAT,false,36,data)
        data.position(3);GL.glVertexAttribPointer(rgba,4,GL.GL_FLOAT,false,36,data)
        data.position(7);GL.glVertexAttribPointer(uv,2,GL.GL_FLOAT,false,36,data)
        GL.glDrawArrays(GL.GL_TRIANGLES,0,vertices)
        GL.glDisableVertexAttribArray(position);GL.glDisableVertexAttribArray(rgba);GL.glDisableVertexAttribArray(uv)
        GL.glDepthMask(true);GL.glDisable(GL.GL_BLEND)
    }
}

