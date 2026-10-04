package com.ali.cs2utility

import com.ali.cs2utility.domain.Vec3
import com.ali.cs2utility.scene.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class SceneRayQueryTest {
    private val down=SceneRay(Vec3(.2f,2f,.2f),Vec3(0f,-1f,0f),10f)
    private val a=Vec3(0f,0f,0f);private val b=Vec3(1f,0f,0f);private val c=Vec3(0f,0f,1f)
    @Test fun downwardHitHasPositionBarycentricsAndSupportCandidate() {
        val h=requireNotNull(SceneRayQuery.triangle(down,a,b,c));assertEquals(2f,h.distance,.0001f);assertEquals(0f,h.position.y,0f)
        assertEquals(1f,h.barycentric.x+h.barycentric.y+h.barycentric.z,.0001f);assertTrue(h.supportCandidate(down))
    }
    @Test fun backFacesAreQueryableButDoNotCertifyFloor() {
        val ray=SceneRay(Vec3(.2f,-2f,.2f),Vec3(0f,1f,0f),10f)
        val h=requireNotNull(SceneRayQuery.triangle(ray,a,b,c));assertFalse(h.supportCandidate(ray));assertEquals(-1f,h.normal.y,.0001f)
    }
    @Test fun missesOutsideParallelDegenerateAndBeyondSegment() {
        assertNull(SceneRayQuery.triangle(down.copy(origin=Vec3(2f,2f,2f)),a,b,c))
        assertNull(SceneRayQuery.triangle(down.copy(direction=Vec3(1f,0f,0f)),a,b,c))
        assertNull(SceneRayQuery.triangle(down,a,a,a));assertNull(SceneRayQuery.triangle(down.copy(maxDistance=1f),a,b,c))
    }
    @Test fun sphereKeepsInsideAndRejectsBehindAndOffRay() {
        assertEquals(0f,SceneRayQuery.sphereEntry(down,floatArrayOf(.2f,2f,.2f),1f)!!,0f)
        assertNull(SceneRayQuery.sphereEntry(down,floatArrayOf(5f,0f,0f),1f))
        assertNull(SceneRayQuery.sphereEntry(down,floatArrayOf(.2f,8f,.2f),1f))
    }
    @Test fun chunkReturnsNearestTriangleAndIdentity() {
        val points=listOf(a,b,c,a.copy(y=1f),b.copy(y=1f),c.copy(y=1f))
        val vb=ByteBuffer.allocate(6*36).order(ByteOrder.LITTLE_ENDIAN)
        points.forEachIndexed {i,p->vb.putFloat(i*36,p.x);vb.putFloat(i*36+4,p.y);vb.putFloat(i*36+8,p.z)}
        val ib=ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);(0..5).forEach {ib.putShort(it.toShort())}
        val p=MobilePart("test.d2m.gz",7,6,6,6*36+12,floatArrayOf(0f,0f,0f),2f)
        val h=requireNotNull(SceneRayQuery.chunk(down,vb.array(),ib.array(),p));assertEquals(1,h.triangle);assertEquals(7,h.material);assertEquals(p.asset,h.partAsset);assertEquals(1f,h.distance,.0001f)
    }
    @Test(expected=IllegalArgumentException::class) fun invalidDirectionRejected() {SceneRay(down.origin,Vec3(0f,-2f,0f),10f)}
    @Test(expected=IllegalArgumentException::class) fun nonFiniteRejected() {SceneRay(Vec3(Float.NaN,0f,0f),Vec3(0f,1f,0f),10f)}
    @Test(expected=IllegalArgumentException::class) fun corruptChunkLengthRejected() {
        SceneRayQuery.chunk(down,ByteArray(2),ByteArray(2),MobilePart("bad",0,3,3,114,floatArrayOf(0f,0f,0f),1f))
    }
    @Test fun exportDistinguishesOrbitTargetFromEyeAndKeepsUnknownNull() {
        val camera=CameraState(38f,55f,35f,1f,3f,2f)
        val frame=CalibrationFrame(camera,Vec3(10f,20f,30f),45f,1200,800,"a".repeat(64),down,null)
        val j=CalibrationCapture.json("eye",CalibrationSample(frame,null,null),null)
        assertEquals("orbit-target",j.getString("cameraPositionMeaning"));assertEquals(10.0,j.getJSONArray("value").getDouble(0),0.0)
        assertEquals(1.0,j.getJSONObject("camera").getJSONArray("position").getDouble(0),0.0)
        assertFalse(j.getBoolean("importable"));assertTrue(j.isNull("surface"));assertTrue(j.isNull("screenshot"));assertEquals("DEVELOPMENT",j.getString("status"))
    }
    @Test fun missedAimNeverDefaultsToEyeOrOrigin() {
        val f=CalibrationFrame(CameraState(0f,0f,1f,0f,0f,0f,true),down.origin,70f,1200,800,"a".repeat(64),down,null)
        assertTrue(CalibrationCapture.json("aim",CalibrationSample(f,null,"miss"),null).isNull("value"))
    }
}
