package com.ali.cs2utility

import com.ali.cs2utility.domain.*
import com.ali.cs2utility.scene.Geometry
import com.ali.cs2utility.scene.TexturedMeshLoader
import com.ali.cs2utility.scene.CameraFlight
import com.ali.cs2utility.scene.CameraState
import com.ali.cs2utility.presentation.ExplorerState
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class DomainTest {
    private fun lineup(id: String,type: UtilityType)=Lineup(id,"mirage","A 点 ${type.label}",type,"T","A",
        "T 出生点","CT 入口",Vec3(0f,0f,0f),Vec3(2f,0f,3f),"待核验","待核验",emptyList(),
        Verification.DEMO,"","","",VideoSource(VideoKind.DEMO,"","原创样片"))
    @Test fun combinedFilterPreservesMapListIdentity() {
        val all=listOf(lineup("s",UtilityType.SMOKE),lineup("f",UtilityType.FLASH))
        assertEquals(listOf(all[0]),LineupFilter.apply(all,Filter("ct",UtilityType.SMOKE,true),setOf("s")))
        assertTrue(LineupFilter.apply(all,Filter("B 区"),emptySet()).isEmpty())
        assertEquals(2,LineupFilter.apply(all,Filter(),emptySet()).size)
    }
    @Test fun objTriangulatesNegativeSlashIndices() {
        val obj="v 0 0 0\nv 1 0 0\nv 1 0 1\nv 0 0 1\nf -4/1 -3/2 -2/3 -1/4"
        assertEquals(6*6,Geometry.obj(obj.reader()).size)
    }
    @Test(expected=IllegalArgumentException::class) fun objRejectsMissingVertex() {
        Geometry.obj("v 0 0 0\nf 1 2 3".reader())
    }
    @Test fun boxesHaveTwelveTriangles() {
        val scene=SceneDefinition(28f,listOf(Box(Vec3(0f,0f,0f),Vec3(1f,2f,3f),floatArrayOf(1f,.5f,0f))),emptyList(),null,true)
        val mesh=Geometry.boxes(scene)
        assertEquals(12*3*6,mesh.size)
        assertTrue(mesh.all { it.isFinite() })
    }
    @Test fun targetGroupExpandsFiveSpawnChoicesInNumericOrder() {
        val items=(5 downTo 1).map { n -> lineup("spawn-$n",UtilityType.SMOKE).copy(groupId="vip",groupTitle="VIP 快烟",spawnNumber=n) }
        val groups=TargetGroups.from(items)
        assertEquals(1,groups.size)
        assertEquals("VIP 快烟",groups[0].title)
        assertEquals(listOf(1,2,3,4,5),groups[0].items.map { it.spawnNumber })
    }
    @Test fun selectedSpawnKeepsParentGroupAfterReturning() {
        val state=ExplorerState(emptyList())
        state.lineups=listOf(lineup("s",UtilityType.SMOKE).copy(groupId="vip",groupTitle="VIP 快烟",spawnNumber=1))
        state.select("s");assertEquals("vip",state.selectedGroupId)
        state.select(null);assertEquals("vip",state.selectedGroup?.id)
        state.filter=Filter(query="does not exist");state.reconcileSelection()
        assertNull(state.selectedGroupId)
    }
    private fun meshBytes(invalidIndex: Boolean): ByteArray {
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(0x43324d32);out.writeInt(1);out.writeUTF("")
            out.writeInt(3);out.writeInt(3);repeat(3) { out.writeFloat(1f) }
            val vertices=ByteBuffer.allocate(3*32).order(ByteOrder.LITTLE_ENDIAN)
            repeat(3) { n -> vertices.putFloat(n.toFloat());repeat(7) { vertices.putFloat(0f) } }
            out.write(vertices.array())
            val indices=ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN)
            indices.putShort(0);indices.putShort(1);indices.putShort(if(invalidIndex) 9 else 2)
            out.write(indices.array())
        }
        return bytes.toByteArray()
    }
    @Test fun detailedMeshLoadsLittleEndianVerticesAndIndices() {
        val mesh=TexturedMeshLoader.load(meshBytes(false).inputStream())
        assertEquals(1,mesh.parts.size);assertEquals(3,mesh.parts[0].indexCount)
        assertEquals(2f,mesh.parts[0].vertices.get(16),0f)
    }
    @Test(expected=IllegalArgumentException::class) fun detailedMeshRejectsOutOfBoundsIndices() {
        TexturedMeshLoader.load(meshBytes(true).inputStream())
    }
    @Test fun targetFlightTakesShortYawArcAndArrivesAtSpawnCamera() {
        val from=CameraState(350f,65f,248f,-20f,20f,1f)
        val to=CameraState(10f,80f,13f,31.7f,4f,-4.25f)
        val flight=CameraFlight(from,to,1000,850)
        assertEquals(360f,flight.at(1425).yaw,0.01f)
        assertEquals(from,flight.at(900))
        assertEquals(to,flight.at(1850))
        assertTrue(flight.finished(1850))
    }
    @Test fun detailedMeshProvidesBoundsForCloseUpCulling() {
        val part=TexturedMeshLoader.load(meshBytes(false).inputStream()).parts.single()
        assertArrayEquals(floatArrayOf(1f,0f,0f),part.center,0.001f)
        assertEquals(1f,part.radius,0.001f)
    }
}
