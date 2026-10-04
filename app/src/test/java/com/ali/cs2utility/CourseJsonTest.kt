package com.ali.cs2utility

import com.ali.cs2utility.tutorial.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class CourseJsonTest {
    private fun file(name: String): String {
        val f=listOf(File("docs/contracts/$name"),File("../docs/contracts/$name")).first {it.isFile}
        return f.readText()
    }
    @Test fun importDevelopmentExampleAndBindActualMap() {
        val c=CourseJson.parse(file("C013-development-smoke-he.json"),DevelopmentCourses.MAP_VERSION)
        assertFalse(c.evidence.calibrated);assertEquals(listOf("D2-001","D2-014"),c.relatedIds)
        assertEquals(2.0,c.path.last().seconds,0.0)
        assertEquals(0f,CourseEffects.density(c,c.smoke.first(),c.he.first().center,7.5),0f)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectUncalibratedNullDraft() {
        CourseJson.parse(file("C013-D2-001-pending.json"),DevelopmentCourses.MAP_VERSION)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectVideoProgressTimeOrigin() {
        val j=JSONObject(file("C013-development-smoke-he.json"));j.put("timeOrigin","video-progress")
        CourseJson.parse(j.toString(),DevelopmentCourses.MAP_VERSION)
    }
    @Test(expected=IllegalArgumentException::class) fun rejectClaimOfCalibrationWithoutSource() {
        val j=JSONObject(file("C013-development-smoke-he.json"));j.put("status","CALIBRATED")
        CourseJson.parse(j.toString(),DevelopmentCourses.MAP_VERSION)
    }
}
