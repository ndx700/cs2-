package com.ali.cs2utility

import com.ali.cs2utility.scene.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

class RunMapTest {
    private val c=CameraState(0f,0f,1f,0f,0f,2f,true,6f)
    @Test fun forwardAndBackCancelWithoutChangingHeight() {
        val next=FreeCamera.advance(c,0f,1f,0f,.1f)
        assertEquals(-.6f,next.z,.0001f);assertEquals(2f,next.y,0f)
        assertEquals(c.z,FreeCamera.advance(next,0f,-1f,0f,.1f).z,.0001f)
    }
    @Test fun rightFollowsYawAndLookDoesNotChangeRunHeight() {
        val next=FreeCamera.advance(c.copy(yaw=90f,pitch=80f),0f,1f,0f,.1f)
        assertEquals(-.6f,next.x,.0001f);assertEquals(2f,next.y,0f)
        assertEquals(.6f,FreeCamera.advance(c,1f,0f,0f,.1f).x,.0001f)
    }
    @Test fun diagonalMovementDoesNotMultiplySpeed() {
        val next=FreeCamera.advance(c,1f,1f,1f,.1f)
        assertEquals(.6f,sqrt(next.x*next.x+next.z*next.z+(next.y-c.y)*(next.y-c.y)),.0001f)
    }
    @Test fun movementUsesElapsedTimeAndCapsResumeGap() {
        var a=c;repeat(30) {a=FreeCamera.advance(a,0f,1f,0f,1f/30)}
        var b=c;repeat(60) {b=FreeCamera.advance(b,0f,1f,0f,1f/60)}
        assertEquals(-6f,a.z,.0001f);assertEquals(a.z,b.z,.0001f)
        assertEquals(-.6f,FreeCamera.advance(c,0f,1f,0f,60f).z,.0001f)
        assertEquals(c,FreeCamera.advance(c,0f,0f,0f,.1f))
        assertEquals(c.copy(free=false),FreeCamera.advance(c.copy(free=false),1f,1f,1f,.1f))
    }
    @Test fun pitchAllowsTunnelsAndCannotFlipCamera() {
        assertEquals(-85f,FreeCamera.look(c,0f,-1000f).pitch,0f)
        assertEquals(85f,FreeCamera.look(c,0f,1000f).pitch,0f)
        assertArrayEquals(floatArrayOf(0f,0f,-1f),FreeCamera.forward(c),.0001f)
        assertTrue(FreeCamera.forward(c.copy(pitch=-45f))[1]>0)
    }
    @Test fun fewerAndManyCorePhonesStayWithinWorkerLimit() {
        assertEquals(2,SceneLoadPolicy.workerCount(1));assertEquals(2,SceneLoadPolicy.workerCount(4))
        assertEquals(6,SceneLoadPolicy.workerCount(8));assertEquals(6,SceneLoadPolicy.workerCount(32))
    }
    @Test fun largeVisibleStructurePrecedesTinyProp() {
        assertTrue(SceneLoadPolicy.priority(400f,15f)<SceneLoadPolicy.priority(100f,.2f))
    }
    @Test fun decodedUploadsRetainBothByteAndJobCapacity() {
        val b=DecodeBudget(2,10)
        assertTrue(b.acquire(6));assertFalse(b.acquire(5));assertTrue(b.acquire(4));assertFalse(b.acquire(1))
        b.release(6);assertTrue(b.acquire(6));b.release(4);b.release(6)
        assertTrue(b.acquire(10));b.release(10);assertFalse(b.acquire(11))
    }
    @Test fun budgetIsSafeForConcurrentCompletionAndCancellation() {
        val b=DecodeBudget(6,600);val workers=Executors.newFixedThreadPool(6)
        repeat(200) {workers.execute {if(b.acquire(100))b.release(100)}}
        workers.shutdown();assertTrue(workers.awaitTermination(5,TimeUnit.SECONDS))
        assertTrue(b.acquire(600));b.release(600)
    }
}
