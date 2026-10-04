package com.ali.cs2utility.scene

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20 as GL
import android.opengl.GLUtils
import android.os.SystemClock
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.LinkedHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

data class MobileMaterial(val base: String,val layer: String,val blend: String,val alpha: String,val cutoff: Float,
    val opacity: Float,val doubleSided: Boolean,val overlay: Boolean,val tint: FloatArray,val uvScale: FloatArray,
    val uvOffset: FloatArray,val uvRotation: Float,val layerScale: FloatArray,val layerOffset: FloatArray,
    val blendScale: FloatArray,val blendMode: Int,val softness: Float,val paint: Boolean,val enabled: Boolean) {
    val texturePaths=listOf(base,layer,blend).filter {it.isNotEmpty()}.distinct()
}
data class MobilePart(val asset: String,val material: Int,val vertices: Int,val indices: Int,val bytes: Int,val center: FloatArray,val radius: Float)
data class MobileScene(val parts: List<MobilePart>,val materials: List<MobileMaterial>,val manifestSha256: String="")

object MobileSceneLoader {
    private fun path(s: String): String {
        require(s.isEmpty() || (s.startsWith("maps/dust2/mobile/") && !s.contains("..") && !s.contains('\\'))) { "Invalid scene asset path" }
        return s
    }
    private fun floats(o: JSONObject,key: String,n: Int): FloatArray {
        val a=o.getJSONArray(key);require(a.length()>=n)
        return FloatArray(n) { a.getDouble(it).toFloat().also { f -> require(f.isFinite()) } }
    }
    fun load(context: Context,asset: String): MobileScene {
        val manifestBytes=context.assets.open(asset).use {it.readBytes()}
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(manifestBytes).joinToString("") {"%02x".format(it.toInt() and 255)}
        val j=JSONObject(manifestBytes.toString(Charsets.UTF_8))
        require(j.getString("schema")=="dust2-mobile-d2m1-v1" && j.getInt("stride")==36)
        val ma=j.getJSONArray("materials");require(ma.length() in 1..1024)
        val materials=(0 until ma.length()).map { i ->
            val m=ma.getJSONObject(i);val alpha=m.getString("alphaMode");require(alpha in listOf("opaque","mask","blend"))
            MobileMaterial(path(m.getString("base")),path(m.getString("layer")),path(m.getString("blend")),alpha,
                m.getDouble("cutoff").toFloat().coerceIn(0f,1f),m.getDouble("opacity").toFloat().coerceIn(0f,1f),
                m.getBoolean("doubleSided"),m.getBoolean("overlay"),floats(m,"tint",3),floats(m,"uvScale",2),
                floats(m,"uvOffset",2),m.getDouble("uvRotation").toFloat(),floats(m,"layerScale",2),
                floats(m,"layerOffset",2),floats(m,"blendScale",2),m.getInt("blendMode"),
                m.getDouble("softness").toFloat().coerceAtLeast(.001f),m.getBoolean("paint"),m.optBoolean("previewEnabled",true))
        }
        val pa=j.getJSONArray("parts");require(pa.length() in 1..16384)
        val parts=(0 until pa.length()).map { i ->
            val p=pa.getJSONObject(i);val nv=p.getInt("vertexCount");val ni=p.getInt("indexCount");val mi=p.getInt("material")
            require(nv in 1..65535 && ni in 3..1500000 && ni%3==0 && mi in materials.indices)
            val bytes=nv*36+ni*2;require(p.getInt("bytes")==bytes && bytes.toLong()*2<=24L*1024*1024)
            val r=p.getDouble("radius").toFloat();require(r.isFinite() && r>=0)
            MobilePart(path(p.getString("asset")),mi,nv,ni,bytes,floats(p,"center",3),r)
        }
        require(parts.sumOf { it.bytes.toLong() }<=192L*1024*1024) { "Scene exceeds mobile geometry budget" }
        return MobileScene(parts,materials,hash)
    }
}

/** GL state stays on the GL thread. Workers only read/decode bounded chunks; no full-map CPU buffers. */
class MobileSceneRenderer(private val context: Context,private val requestFrame: () -> Unit,
    private val status: (String,Boolean) -> Unit) {
    private data class Geometry(val vb: Int,val ib: Int,val bytes: Int)
    private data class Texture(val id: Int,val bytes: Long)
    private data class Upload(val generation: Int,val part: Int=-1,val path: String="",val vb: ByteBuffer?=null,
        val ib: ByteBuffer?=null,val bitmap: Bitmap?=null,val error: String?=null,val cost: Long=1)
    private val workerCount=SceneLoadPolicy.workerCount(Runtime.getRuntime().availableProcessors())
    @Volatile private var closed=false
    private val workers=Executors.newFixedThreadPool(workerCount)
    private val budget=DecodeBudget(workerCount*2,24L*1024*1024)
    private val vertexOffsets=intArrayOf(0,4,8,20,24)
    private var firstVisibleReady=0L;private var waitingSince=0L
    private val generation=AtomicInteger()
    private val picking=AtomicBoolean()
    private val uploads=ConcurrentLinkedQueue<Upload>()
    private val geometry=LinkedHashMap<Int,Geometry>(128,.75f,true)
    private val textures=LinkedHashMap<String,Texture>(128,.75f,true)
    private val pendingGeometry=HashSet<Int>();private val pendingTextures=HashSet<String>()
    private var scene: MobileScene?=null
    private var program=0;private var failed=false;private var lastStatus="";private var lastStatusAt=0L
    private var geometryBytes=0L;private var textureBytes=0L
    var visibleReady=false;private set
    private var position=0;private var normal=0;private var uv=0;private var color=0;private var blend=0
    private val uniforms=HashMap<String,Int>()
    private fun location(s: String)=uniforms.getOrPut(s) { GL.glGetUniformLocation(program,s) }
    private fun msg(s: String,error: Boolean=false) {
        val now=SystemClock.uptimeMillis()
        if(s!=lastStatus && (error || lastStatus.isEmpty() || !s.contains("可见区域") || now-lastStatusAt>=200)) {
            lastStatus=s;lastStatusAt=now;status(s,error)
        }
    }
    fun setScene(value: MobileScene?) {
        visibleReady=false
        generation.incrementAndGet();clearGl();scene=value;failed=false;lastStatus="";lastStatusAt=0L;firstVisibleReady=0L;waitingSince=0L
        if(value!=null) msg("沙二 · 正在读取全图与贴图…")
    }
    private fun drainUploads() {synchronized(uploads) {
        while(true) {val u=uploads.poll() ?: break;u.bitmap?.recycle();budget.release(u.cost)}
    }}
    private fun publish(u: Upload) {synchronized(uploads) {
        if(!closed && u.generation==generation.get())uploads.add(u) else {u.bitmap?.recycle();budget.release(u.cost)}
    };if(!closed)requestFrame()}
    private fun clearGl() {
        geometry.values.forEach { GL.glDeleteBuffers(2,intArrayOf(it.vb,it.ib),0) }
        if(textures.isNotEmpty()) GL.glDeleteTextures(textures.size,textures.values.map { it.id }.toIntArray(),0)
        geometry.clear();textures.clear();pendingGeometry.clear();pendingTextures.clear();geometryBytes=0;textureBytes=0
        drainUploads()
    }
    fun contextCreated() {
        visibleReady=false
        // Old names belonged to the lost context; never delete them in the new one.
        generation.incrementAndGet();geometry.clear();textures.clear();pendingGeometry.clear();pendingTextures.clear()
        geometryBytes=0;textureBytes=0;drainUploads()
        uniforms.clear();failed=false
        fun shader(type: Int,asset: String): Int {
            val s=context.assets.open(asset).bufferedReader().use { it.readText() };val id=GL.glCreateShader(type)
            GL.glShaderSource(id,s);GL.glCompileShader(id);val ok=IntArray(1);GL.glGetShaderiv(id,GL.GL_COMPILE_STATUS,ok,0)
            check(ok[0]!=0) { GL.glGetShaderInfoLog(id) };return id
        }
        try {
            val v=shader(GL.GL_VERTEX_SHADER,"shaders/mobile.vert");val f=shader(GL.GL_FRAGMENT_SHADER,"shaders/mobile.frag")
            program=GL.glCreateProgram();GL.glAttachShader(program,v);GL.glAttachShader(program,f);GL.glLinkProgram(program)
            val ok=IntArray(1);GL.glGetProgramiv(program,GL.GL_LINK_STATUS,ok,0);check(ok[0]!=0) { GL.glGetProgramInfoLog(program) }
            GL.glDeleteShader(v);GL.glDeleteShader(f)
            position=GL.glGetAttribLocation(program,"aPosition");normal=GL.glGetAttribLocation(program,"aNormal")
            uv=GL.glGetAttribLocation(program,"aUV");color=GL.glGetAttribLocation(program,"aColor");blend=GL.glGetAttribLocation(program,"aBlend")
        } catch(e: Exception) { failed=true;msg("渲染初始化失败：${e.message}",true) }
    }
    private fun submit(cost: Long,work: () -> Unit): Boolean {
        try {workers.execute {work()};return true}
        catch(e: RejectedExecutionException) {budget.release(cost);return false}
    }
    private fun schedulePart(id: Int,p: MobilePart) {
        val cost=p.bytes.toLong()*2
        if(closed || id in pendingGeometry || geometry.containsKey(id) || !budget.acquire(cost)) return
        pendingGeometry.add(id);val token=generation.get()
        if(!submit(cost) {
            var result: Upload
            try {
                require(ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN)
                val (v,i)=SceneChunkInput.open(p.asset) {context.assets.open(it)}.use { f ->
                    require(f.readInt()==0x44324d31 && f.readInt()==p.vertices && f.readInt()==p.indices && f.readInt()==36)
                    val v=ByteArray(p.vertices*36);val i=ByteArray(p.indices*2);f.readFully(v);f.readFully(i);require(f.read()==-1)
                    val checked=ByteBuffer.wrap(v).order(ByteOrder.LITTLE_ENDIAN)
                    for(n in 0 until p.vertices) for(offset in vertexOffsets) require(checked.getFloat(n*36+offset).isFinite())
                    val ind=ByteBuffer.wrap(i).order(ByteOrder.LITTLE_ENDIAN)
                    while(ind.hasRemaining()) require((ind.short.toInt() and 65535)<p.vertices)
                    Pair(ByteBuffer.allocateDirect(v.size).apply { put(v);position(0) },ByteBuffer.allocateDirect(i.size).apply { put(i);position(0) })
                }
                result=Upload(token,part=id,vb=v,ib=i,cost=cost)
            } catch(e: Exception) { result=Upload(token,part=id,error="${p.asset}: ${e.message}",cost=cost) }
            publish(result)
        })pendingGeometry.remove(id)
    }
    private fun scheduleTexture(path: String) {
        val cost=2L*1024*1024
        if(closed || path.isEmpty() || path in pendingTextures || textures.containsKey(path) || !budget.acquire(cost)) return
        pendingTextures.add(path);val token=generation.get()
        if(!submit(cost) {
            val result=try {
                val options=BitmapFactory.Options().apply { inPreferredConfig=Bitmap.Config.ARGB_8888;inScaled=false;inPremultiplied=false }
                val b=context.assets.open(path).use { BitmapFactory.decodeStream(it,null,options) }
                    ?: error("PNG decode failed")
                require(b.width<=512 && b.height<=512)
                Upload(token,path=path,bitmap=b,cost=cost)
            } catch(e: Exception) { Upload(token,path=path,error="$path: ${e.message}",cost=cost) }
            publish(result)
        })pendingTextures.remove(path)
    }
    /** Development-only one-shot query on the same bounded workers/budget, one chunk at a time. */
    fun pick(ray: SceneRay,done: (SurfacePick?,String?) -> Unit) {
        val s=scene ?: run {done(null,"移动地图尚未就绪");return}
        if(closed || failed) {done(null,"地图已关闭或读取失败");return}
        val candidates=s.parts.indices.filter {s.materials[s.parts[it].material].enabled}
            .mapNotNull {id->SceneRayQuery.sphereEntry(ray,s.parts[id].center,s.parts[id].radius)?.let {Pair(id,it)}}.sortedBy {it.second}
        if(candidates.isEmpty()) {done(null,"射线没有经过显示分块");return}
        val cost=candidates.maxOf {s.parts[it.first].bytes.toLong()*2}
        if(!picking.compareAndSet(false,true)) {done(null,"已有采集查询，请稍候");return}
        if(!budget.acquire(cost)) {picking.set(false);done(null,"地图解码队列忙，请就绪后重试采集");return}
        val token=generation.get()
        if(!submit(cost) {
            var hit: SurfacePick?=null;var error: String?=null
            try {
                for((id,entry) in candidates) {
                    check(!closed && token==generation.get()) {"地图已切换，采集作废"}
                    if(hit!=null && entry>hit.distance)break
                    val part=s.parts[id]
                    val next=SceneChunkInput.open(part.asset) {context.assets.open(it)}.use {f->
                        require(f.readInt()==0x44324d31 && f.readInt()==part.vertices && f.readInt()==part.indices && f.readInt()==36)
                        val v=ByteArray(part.vertices*36);val i=ByteArray(part.indices*2);f.readFully(v);f.readFully(i);require(f.read()==-1)
                        SceneRayQuery.chunk(ray,v,i,part)
                    }
                    if(next!=null && (hit==null || next.distance<hit.distance))hit=next
                }
                check(!closed && token==generation.get()) {"地图已切换，采集作废"}
                if(hit==null)error="未命中显示三角形"
            } catch(e: Exception) {hit=null;error=e.message ?: "查询失败"}
            finally {picking.set(false);budget.release(cost)}
            done(hit,error)
        }) {picking.set(false);done(null,"地图查询已停止")}
    }
    private fun takeUploads(moving: Boolean) {
        val deadline=SystemClock.uptimeMillis()+(if(moving)4 else 10);var n=0
        while(n<24 && SystemClock.uptimeMillis()<=deadline) {
            val u=uploads.poll() ?: break;n++
            try {
            if(u.generation!=generation.get()) {u.bitmap?.recycle();continue}
            pendingGeometry.remove(u.part);pendingTextures.remove(u.path)
            if(u.error!=null) {failed=true;u.bitmap?.recycle();msg("地图资源加载失败：${u.error}",true);continue}
            if(failed) {u.bitmap?.recycle();continue}
            try {
                if(u.part>=0) {
                    val p=scene!!.parts[u.part];val ids=IntArray(2);GL.glGenBuffers(2,ids,0)
                    GL.glBindBuffer(GL.GL_ARRAY_BUFFER,ids[0]);GL.glBufferData(GL.GL_ARRAY_BUFFER,u.vb!!.capacity(),u.vb,GL.GL_STATIC_DRAW)
                    GL.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER,ids[1]);GL.glBufferData(GL.GL_ELEMENT_ARRAY_BUFFER,u.ib!!.capacity(),u.ib,GL.GL_STATIC_DRAW)
                    geometry[u.part]=Geometry(ids[0],ids[1],p.bytes);geometryBytes+=p.bytes
                } else {
                    val b=u.bitmap!!;val bytes=b.width.toLong()*b.height*4*4/3
                    check(textureBytes+bytes<=96L*1024*1024) { "Texture GPU budget exceeded" }
                    val ids=IntArray(1);GL.glGenTextures(1,ids,0);GL.glBindTexture(GL.GL_TEXTURE_2D,ids[0])
                    GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MIN_FILTER,GL.GL_LINEAR_MIPMAP_LINEAR)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_MAG_FILTER,GL.GL_LINEAR)
                    GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_S,GL.GL_REPEAT);GL.glTexParameteri(GL.GL_TEXTURE_2D,GL.GL_TEXTURE_WRAP_T,GL.GL_REPEAT)
                    GLUtils.texImage2D(GL.GL_TEXTURE_2D,0,b,0);GL.glGenerateMipmap(GL.GL_TEXTURE_2D);b.recycle()
                    textures[u.path]=Texture(ids[0],bytes);textureBytes+=bytes
                }
                val e=GL.glGetError();check(e==GL.GL_NO_ERROR) { "OpenGL upload error 0x${e.toString(16)}" }
            } catch(e: Exception) { failed=true;if(u.bitmap?.isRecycled==false) u.bitmap.recycle();msg("地图显存上传失败：${e.message}",true) }
            } finally {budget.release(u.cost)}
        }
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER,0);GL.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER,0)
    }
    private fun bindTexture(unit: Int,path: String) {
        GL.glActiveTexture(GL.GL_TEXTURE0+unit);GL.glBindTexture(GL.GL_TEXTURE_2D,textures[path]?.id ?: 0)
    }
    fun draw(vp: FloatArray,frustum: Array<FloatArray>,eye: FloatArray,moving: Boolean=false,upload: Boolean=true) {
        val s=scene ?: return;if(failed || program==0) {visibleReady=false;return}
        if(upload)takeUploads(moving);if(failed) {visibleReady=false;return}
        val visible=s.parts.indices.filter { i -> val p=s.parts[i];s.materials[p.material].enabled && frustum.none { f -> f[0]*p.center[0]+f[1]*p.center[1]+f[2]*p.center[2]+f[3]<-p.radius } }
        fun dist(i: Int): Float {val p=s.parts[i];val x=p.center[0]-eye[0];val y=p.center[1]-eye[1];val z=p.center[2]-eye[2];return x*x+y*y+z*z}
        val priority=visible.sortedBy {SceneLoadPolicy.priority(dist(it),s.parts[it].radius)}
        for(id in priority) {
            val m=s.materials[s.parts[id].material]
            m.texturePaths.forEach(::scheduleTexture);schedulePart(id,s.parts[id])
            if(!budget.hasCapacity())break
        }
        // Fill spare decode slots near the eye so turning a corner does not start from zero.
        if(budget.hasCapacity() && priority.all {it in pendingGeometry || geometry.containsKey(it)}) {
            val nearby=s.parts.indices.filter {i -> s.materials[s.parts[i].material].enabled &&
                !geometry.containsKey(i) && i !in pendingGeometry && sqrt(dist(i))-s.parts[i].radius<18f}.sortedBy(::dist)
            for(id in nearby) {val m=s.materials[s.parts[id].material]
                m.texturePaths.forEach(::scheduleTexture);schedulePart(id,s.parts[id])
                if(!budget.hasCapacity())break
            }
        }
        GL.glUseProgram(program);GL.glUniformMatrix4fv(location("uVP"),1,false,vp,0)
        GL.glUniform1i(location("uBase"),0);GL.glUniform1i(location("uLayer"),1);GL.glUniform1i(location("uBlendMap"),2)
        for(a in intArrayOf(position,normal,uv,color,blend)) GL.glEnableVertexAttribArray(a)
        val opaque=visible.filter { s.materials[s.parts[it].material].alpha!="blend" }.sortedBy { s.parts[it].material }
        val transparent=visible.filter { s.materials[s.parts[it].material].alpha=="blend" }.sortedByDescending(::dist)
        var drawn=0;var waiting=0;var lastMaterial=-1
        for(id in opaque+transparent) {
            val p=s.parts[id];val m=s.materials[p.material];val g=geometry[id]
            if(g==null || m.texturePaths.any { !textures.containsKey(it) }) {waiting++;continue}
            if(p.material!=lastMaterial) {
            lastMaterial=p.material
            val blending=m.alpha=="blend"
            if(blending) {GL.glEnable(GL.GL_BLEND);GL.glBlendFunc(GL.GL_SRC_ALPHA,GL.GL_ONE_MINUS_SRC_ALPHA)} else GL.glDisable(GL.GL_BLEND)
            GL.glDepthMask(!blending)
            if(m.doubleSided) GL.glDisable(GL.GL_CULL_FACE) else {GL.glEnable(GL.GL_CULL_FACE);GL.glCullFace(GL.GL_BACK)}
            if(m.overlay) {GL.glEnable(GL.GL_POLYGON_OFFSET_FILL);GL.glPolygonOffset(-1f,-1f)} else GL.glDisable(GL.GL_POLYGON_OFFSET_FILL)
            bindTexture(0,m.base);bindTexture(1,m.layer);bindTexture(2,m.blend)
            GL.glUniform1i(location("uHasBase"),if(m.base.isEmpty())0 else 1)
            GL.glUniform1i(location("uHasLayer"),if(m.layer.isEmpty())0 else 1)
            GL.glUniform1i(location("uHasBlend"),if(m.blend.isEmpty())0 else 1)
            GL.glUniform1i(location("uMode"),when(m.alpha) {"mask"->1;"blend"->2;else->0})
            GL.glUniform1i(location("uBlendMode"),m.blendMode);GL.glUniform1i(location("uPaint"),if(m.paint)1 else 0)
            GL.glUniform1f(location("uCutoff"),m.cutoff);GL.glUniform1f(location("uOpacity"),m.opacity);GL.glUniform1f(location("uSoftness"),m.softness)
            GL.glUniform3fv(location("uTint"),1,m.tint,0);GL.glUniform2fv(location("uUVScale"),1,m.uvScale,0)
            GL.glUniform2fv(location("uUVOffset"),1,m.uvOffset,0);GL.glUniform1f(location("uUVRotation"),m.uvRotation)
            GL.glUniform2fv(location("uLayerScale"),1,m.layerScale,0);GL.glUniform2fv(location("uLayerOffset"),1,m.layerOffset,0)
            GL.glUniform2fv(location("uBlendScale"),1,m.blendScale,0)
            }
            GL.glBindBuffer(GL.GL_ARRAY_BUFFER,g.vb);GL.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER,g.ib)
            GL.glVertexAttribPointer(position,3,GL.GL_FLOAT,false,36,0);GL.glVertexAttribPointer(normal,3,GL.GL_SHORT,true,36,12)
            GL.glVertexAttribPointer(uv,2,GL.GL_FLOAT,false,36,20);GL.glVertexAttribPointer(color,4,GL.GL_UNSIGNED_BYTE,true,36,28)
            GL.glVertexAttribPointer(blend,4,GL.GL_UNSIGNED_BYTE,true,36,32)
            GL.glDrawElements(GL.GL_TRIANGLES,p.indices,GL.GL_UNSIGNED_SHORT,0);drawn++
        }
        for(a in intArrayOf(position,normal,uv,color,blend)) GL.glDisableVertexAttribArray(a)
        if(upload)visibleReady=waiting==0 && drawn>0
        GL.glBindBuffer(GL.GL_ARRAY_BUFFER,0);GL.glBindBuffer(GL.GL_ELEMENT_ARRAY_BUFFER,0);GL.glActiveTexture(GL.GL_TEXTURE0)
        GL.glDisable(GL.GL_CULL_FACE);GL.glDisable(GL.GL_BLEND);GL.glDisable(GL.GL_POLYGON_OFFSET_FILL);GL.glDepthMask(true)
        if(waiting>0) {if(waitingSince==0L)waitingSince=SystemClock.uptimeMillis();msg("沙二 · 可见区域 $drawn/${visible.size} · ${workerCount} 线程")}
        else {
            if(waitingSince!=0L) {firstVisibleReady=SystemClock.uptimeMillis()-waitingSince;waitingSince=0L}
            msg("沙二 · 区域就绪 ${firstVisibleReady/1000f}s · ${workerCount} 线程 · ${geometryBytes/1048576}/${textureBytes/1048576} MiB")
        }
        // Worker completion wakes the GL thread; avoid spinning while reads are outstanding.
        if(uploads.isNotEmpty())requestFrame()
    }
    fun shutdownWorkers() {
        closed=true;generation.incrementAndGet();workers.shutdown()
        drainUploads()
    }
    fun dispose() {shutdownWorkers();clearGl();scene=null}
}
