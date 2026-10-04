package com.ali.cs2utility.scene

import com.ali.cs2utility.domain.Box
import com.ali.cs2utility.domain.SceneDefinition
import java.io.Reader

/** Interleaved xyz + rgb. Face colors give the untextured low-poly scene depth. */
object Geometry {
    fun boxes(scene: SceneDefinition): FloatArray {
        val output = ArrayList<Float>()
        scene.boxes.forEach { box(output, it) }
        return output.toFloatArray()
    }
    private fun box(out: MutableList<Float>, box: Box) {
        val c = box.center; val s = box.size
        val v = arrayOf(
            floatArrayOf(c.x-s.x/2, c.y-s.y/2, c.z-s.z/2), floatArrayOf(c.x+s.x/2, c.y-s.y/2, c.z-s.z/2),
            floatArrayOf(c.x+s.x/2, c.y+s.y/2, c.z-s.z/2), floatArrayOf(c.x-s.x/2, c.y+s.y/2, c.z-s.z/2),
            floatArrayOf(c.x-s.x/2, c.y-s.y/2, c.z+s.z/2), floatArrayOf(c.x+s.x/2, c.y-s.y/2, c.z+s.z/2),
            floatArrayOf(c.x+s.x/2, c.y+s.y/2, c.z+s.z/2), floatArrayOf(c.x-s.x/2, c.y+s.y/2, c.z+s.z/2))
        val faces = arrayOf(intArrayOf(0,1,2,3), intArrayOf(5,4,7,6), intArrayOf(3,2,6,7),
            intArrayOf(4,5,1,0), intArrayOf(4,0,3,7), intArrayOf(1,5,6,2))
        val shades = floatArrayOf(.68f,.82f,1f,.5f,.72f,.9f)
        faces.forEachIndexed { i, face ->
            intArrayOf(0,1,2,0,2,3).forEach { j ->
                v[face[j]].forEach { out.add(it) }
                box.color.forEach { out.add(it * shades[i]) }
            }
        }
    }
    /** Geometry-only OBJ v/f loader; accepts triangles, polygons, slash and negative indices.
     * Model coordinates MUST already match the map JSON. No materials/textures in v0.1. */
    fun obj(reader: Reader): FloatArray {
        val vertices = mutableListOf<FloatArray>()
        val output = ArrayList<Float>()
        reader.buffered().useLines { lines -> lines.forEach { raw ->
            val p = raw.substringBefore('#').trim().split(Regex("\\s+"))
            when (p[0]) {
                "v" -> {
                    require(p.size >= 4)
                    vertices.add(floatArrayOf(p[1].toFloat(), p[2].toFloat(), p[3].toFloat()).also {
                        require(it.all { n -> n.isFinite() })
                    })
                }
                "f" -> {
                    require(p.size >= 4)
                    val indices = p.drop(1).map {
                        val n = it.substringBefore('/').toInt()
                        val index = if (n < 0) vertices.size + n else n - 1
                        require(n != 0 && index in vertices.indices)
                        index
                    }
                    for (i in 1 until indices.size-1) {
                        intArrayOf(indices[0],indices[i],indices[i+1]).forEach { index ->
                            vertices[index].forEach { output.add(it) }
                            output.addAll(listOf(.68f,.59f,.46f))
                        }
                    }
                }
            }
        } }
        require(output.isNotEmpty()) { "OBJ has no faces" }
        return output.toFloatArray()
    }
}
