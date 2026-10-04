package com.ali.cs2utility

import com.ali.cs2utility.tutorial.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AimAcceptanceTest {
    // Synthetic test evidence only, never shipped as a calibrated course.
    private fun evidence()=AimAcceptance(DevelopmentCourses.MAP_VERSION,"test-build","test-door-edge",
        "test-cs2-frame","test-visible-frame","test-hidden-frame","test-audit","test-device",1080,1920,70f,true,true,true)
    private fun course()=DevelopmentCourses.c014("SMOKE").let {it.copy(evidence=SourceEvidence("test-source","test-game",1.0,true),aimAcceptance=evidence())}
    @Test fun structurallyCompleteEvidencePassesValidationOnly() {val c=course();c.validate(c.mapVersion)}
    @Test(expected=IllegalArgumentException::class) fun calibratedCourseWithoutAimEvidenceRejected() {val c=course();c.copy(aimAcceptance=null).validate(c.mapVersion)}
    @Test(expected=IllegalArgumentException::class) fun staleResourceEvidenceRejected() {val c=course();c.copy(aimAcceptance=evidence().copy(mapVersion="old-map")).validate(c.mapVersion)}
    @Test(expected=IllegalArgumentException::class) fun hiddenPhoneCheckNotPassedRejected() {val c=course();c.copy(aimAcceptance=evidence().copy(phoneReadableWithoutHint=false)).validate(c.mapVersion)}
    @Test(expected=IllegalArgumentException::class) fun mismatchedFovEvidenceRejected() {val c=course();c.copy(aimAcceptance=evidence().copy(verticalFov=90f)).validate(c.mapVersion)}
    @Test(expected=IllegalArgumentException::class) fun sameVisibleHiddenFrameRejected() {val c=course();c.copy(aimAcceptance=evidence().copy(appHintHiddenFrame="test-visible-frame")).validate(c.mapVersion)}
    @Test(expected=IllegalArgumentException::class) fun importerRejectsCalibrationClaimWithoutNewEvidence() {
        val f=listOf(File("docs/contracts/C013-development-smoke-he.json"),File("../docs/contracts/C013-development-smoke-he.json")).first {it.isFile}
        val j=JSONObject(f.readText());j.put("status","CALIBRATED")
        j.getJSONObject("evidence").put("url","test-source").put("gameBuild","test-game").put("releaseVideoSeconds",1.0)
        CourseJson.parse(j.toString(),DevelopmentCourses.MAP_VERSION)
    }
    @Test fun developmentStillImportsWithoutAcceptance() {
        val c=DevelopmentCourses.make();c.validate(c.mapVersion);assertNull(c.aimAcceptance)
    }
}
