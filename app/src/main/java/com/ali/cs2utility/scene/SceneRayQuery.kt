package com.ali.cs2utility.scene

import com.ali.cs2utility.domain.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.*

data class SceneRay(val origin: Vec3,val direction: Vec3,val maxDistance: Float) {
    init {require(listOf(origin.x,origin.y,origin.z,direction.x,direction.y,direction.z,maxDistance).all {it.isFinite()});require(maxDistance>0);require(abs(direction.x*direction.x+direction.y*direction.y+direction.z*direction.z-1)<.001f)}
}
data class SurfacePick(val position: Vec3,val normal: Vec3,val distance: Float,val triangle: Int,
    val partAsset: String,val material: Int,val barycentric: Vec3) {
    // Display geometry only. A facing surface does not certify feet placement or CS2 collision.
    fun supportCandidate(ray: SceneRay)=ray.direction.y<0 && normal.y>=.65f
}

/** Two-sided display triangle query. No clip/entity/physics semantics are implied. */
object SceneRayQuery {
    fun sphereEntry(ray: SceneRay,center: FloatArray,radius: Float): Float? {
        val x=ray.origin.x-center[0];val y=ray.origin.y-center[1];val z=ray.origin.z-center[2]
        val b=x*ray.direction.x+y*ray.direction.y+z*ray.direction.z
        val c=x*x+y*y+z*z-radius*radius;val d=b*b-c
        if(d<0)return null
        val exit=-b+sqrt(d);if(exit<0)return null
        val entry=maxOf(0f,-b-sqrt(d));return entry.takeIf {it<=ray.maxDistance}
    }
    fun triangle(ray: SceneRay,a: Vec3,b: Vec3,c: Vec3): SurfacePick? {
        val e1=Vec3(b.x-a.x,b.y-a.y,b.z-a.z);val e2=Vec3(c.x-a.x,c.y-a.y,c.z-a.z)
        fun cross(x: Vec3,y: Vec3)=Vec3(x.y*y.z-x.z*y.y,x.z*y.x-x.x*y.z,x.x*y.y-x.y*y.x)
        fun dot(x: Vec3,y: Vec3)=x.x*y.x+x.y*y.y+x.z*y.z
        val p=cross(ray.direction,e2);val det=dot(e1,p);if(abs(det)<1e-7f)return null
        val t=Vec3(ray.origin.x-a.x,ray.origin.y-a.y,ray.origin.z-a.z)
        val u=dot(t,p)/det;if(u<0 || u>1)return null
        val q=cross(t,e1);val v=dot(ray.direction,q)/det;if(v<0 || u+v>1)return null
        val distance=dot(e2,q)/det;if(distance<.0001f || distance>ray.maxDistance)return null
        var n=cross(e1,e2);val length=sqrt(dot(n,n));if(length<1e-8f)return null
        val sign=if(dot(n,ray.direction)>0)-1f else 1f;n=Vec3(n.x/length*sign,n.y/length*sign,n.z/length*sign)
        return SurfacePick(Vec3(ray.origin.x+distance*ray.direction.x,ray.origin.y+distance*ray.direction.y,ray.origin.z+distance*ray.direction.z),n,distance,-1,"",-1,Vec3(1-u-v,u,v))
    }
    fun chunk(ray: SceneRay,vertices: ByteArray,indices: ByteArray,part: MobilePart): SurfacePick? {
        require(vertices.size==part.vertices*36 && indices.size==part.indices*2)
        val vb=ByteBuffer.wrap(vertices).order(ByteOrder.LITTLE_ENDIAN);val ib=ByteBuffer.wrap(indices).order(ByteOrder.LITTLE_ENDIAN)
        fun point(i: Int): Vec3 {require(i in 0 until part.vertices);val o=i*36;return Vec3(vb.getFloat(o),vb.getFloat(o+4),vb.getFloat(o+8)).also {require(listOf(it.x,it.y,it.z).all(Float::isFinite))}}
        var best: SurfacePick?=null
        for(i in 0 until part.indices/3) {
            val a=point(ib.short.toInt() and 65535);val b=point(ib.short.toInt() and 65535);val c=point(ib.short.toInt() and 65535)
            val hit=triangle(ray,a,b,c) ?: continue
            if(best==null || hit.distance<best.distance)best=hit.copy(triangle=i,partAsset=part.asset,material=part.material)
        }
        return best
    }
}
