package com.ali.cs2utility.scene

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

data class MeshPart(val vertices: FloatBuffer, val indices: ShortBuffer, val indexCount: Int,
                    val texture: String, val color: FloatArray,val center: FloatArray = floatArrayOf(0f,0f,0f),val radius: Float = Float.MAX_VALUE)
data class TexturedMesh(val parts: List<MeshPart>)
object TexturedMeshLoader {
    /** C2M2: big-endian record headers; little-endian xyz/normal/uv and uint16 indices. */
    fun load(input: InputStream): TexturedMesh = DataInputStream(input.buffered()).use { file ->
        require(file.readInt()==0x43324d32) { "Unknown mesh format" }
        val count=file.readInt(); require(count in 1..4096)
        val parts=(0 until count).map {
            val texture=file.readUTF(); require(texture.isBlank() || (texture.startsWith("maps/") && !texture.contains("..")))
            val nv=file.readInt(); val ni=file.readInt()
            require(nv in 1..65535 && ni in 3..600000 && ni%3==0)
            val color=FloatArray(3) { file.readFloat().also { require(it.isFinite() && it in 0f..1f) } }
            fun bytes(n: Int): ByteBuffer {
                val data=ByteArray(n);file.readFully(data)
                return ByteBuffer.allocateDirect(n).order(ByteOrder.nativeOrder()).apply {
                    val little=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
                    if(ByteOrder.nativeOrder()==ByteOrder.LITTLE_ENDIAN) put(data) else {
                        if(n==ni*2) while(little.hasRemaining()) putShort(little.short)
                        else while(little.hasRemaining()) putFloat(little.float)
                    }
                    position(0)
                }
            }
            val vertices=bytes(nv*32).asFloatBuffer()
            val indices=bytes(ni*2).asShortBuffer()
            for(i in 0 until vertices.limit()) require(vertices.get(i).isFinite())
            for(i in 0 until indices.limit()) require((indices.get(i).toInt() and 65535)<nv)
            val low=FloatArray(3) { Float.POSITIVE_INFINITY };val high=FloatArray(3) { Float.NEGATIVE_INFINITY }
            for(v in 0 until nv) for(a in 0..2) { val n=vertices.get(v*8+a);low[a]=minOf(low[a],n);high[a]=maxOf(high[a],n) }
            val center=FloatArray(3) { (low[it]+high[it])/2f }
            val radius=kotlin.math.sqrt((0..2).sumOf { ((high[it]-low[it])/2f).toDouble().let { d -> d*d } }).toFloat()
            MeshPart(vertices,indices,ni,texture,color,center,radius)
        }
        require(file.read()==-1) { "Unexpected trailing mesh data" }
        TexturedMesh(parts)
    }
}
