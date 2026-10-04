package com.ali.cs2utility

import com.ali.cs2utility.tutorial.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.FloatBuffer

class CourseFxGeometryTest {
    private fun mesh(kind: String,t: Double): FloatArray {
        val c=DevelopmentCourses.c014(kind);val b=FloatBuffer.allocate(CourseFxGeometry.CAPACITY)
        val view=FloatArray(16) {if(it%5==0)1f else 0f}
        val n=CourseFxGeometry.fill(c,t,floatArrayOf(0f,20f,20f),view,b)
        assertEquals(n*9,b.remaining());assertTrue(n<=1024*6)
        return FloatArray(b.remaining()).also {b.get(it)}
    }
    @Test fun smokeProducesWorldTrianglesAndDisappearsAtEnd() {
        val m=mesh("SMOKE",6.0);assertTrue(m.isNotEmpty());assertTrue(m.all {it.isFinite()})
        assertEquals(0,mesh("SMOKE",17.0).size)
        // First quad edges have world extent; no hardware gl_PointSize limit is used.
        assertTrue(kotlin.math.abs(m[0]-m[9])>.5f)
    }
    @Test fun seekingAndReplayingUsesIdenticalMeshAtSameTime() {assertArrayEquals(mesh("SMOKE",6.0),mesh("SMOKE",6.0),0f)}
    @Test fun laterFireZonesAppearAndExtinguishInActualMesh() {
        assertTrue(mesh("FIRE",6.5).size>mesh("FIRE",2.5).size)
        assertEquals(0,mesh("FIRE",17.0).size)
    }
    @Test fun heExplosionAddsGeometryThenDecays() {
        assertTrue(mesh("HE",2.1).size>mesh("HE",5.0).size)
        assertEquals(0,mesh("HE",17.0).size)
    }
}
