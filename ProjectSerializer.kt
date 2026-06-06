package com.socreate.persistence

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.socreate.model.*
import java.io.*
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

object ProjectSerializer {
    private const val MAGIC = 0x534F4352 // "SOCr"
    private const val VERSION_MAJOR = 1
    private const val VERSION_MINOR = 0
    private const val TILE_SIZE = 512

    fun save(project: Project, outputStream: OutputStream) {
        DataOutputStream(outputStream).use { dos ->
            dos.writeInt(MAGIC)
            dos.writeShort(VERSION_MAJOR)
            dos.writeShort(VERSION_MINOR)
            dos.writeUTF(project.name)
            dos.writeInt(project.canvasWidth)
            dos.writeInt(project.canvasHeight)
            dos.writeInt(project.fps)
            dos.writeInt(project.frames.size)
            project.frames.forEach { frame ->
                dos.writeInt(frame.durationMs)
                dos.writeByte(frame.layers.size)
                frame.layers.forEach { layer ->
                    dos.writeFloat(layer.opacity)
                    dos.writeByte(layer.blendMode.ordinal)
                    val tiles = layer.tiles.filter { it.value.isNotEmpty() }
                    dos.writeInt(tiles.size)
                    tiles.forEach { (coord, pixels) ->
                        dos.writeInt(coord.first)
                        dos.writeInt(coord.second)
                        val compressed = compressTile(pixels)
                        dos.writeInt(compressed.size)
                        dos.write(compressed)
                    }
                }
            }
        }
    }

    fun load(inputStream: InputStream): Project {
        DataInputStream(inputStream).use { dis ->
            val magic = dis.readInt()
            if (magic != MAGIC) throw IOException("Not a SoCreate file")
            val major = dis.readShort()
            val minor = dis.readShort()
            // Version check can be added later
            val name = dis.readUTF()
            val canvasWidth = dis.readInt()
            val canvasHeight = dis.readInt()
            val fps = dis.readInt()
            val frameCount = dis.readInt()
            val frames = mutableListOf<Frame>()
            repeat(frameCount) {
                val duration = dis.readInt()
                val layerCount = dis.readByte().toInt()
                val layers = mutableListOf<Layer>()
                repeat(layerCount) {
                    val opacity = dis.readFloat()
                    val blendOrd = dis.readByte().toInt()
                    val blendMode = BlendMode.values()[blendOrd]
                    val tileCount = dis.readInt()
                    val tiles = mutableMapOf<Pair<Int, Int>, IntArray>()
                    repeat(tileCount) {
                        val tileX = dis.readInt()
                        val tileY = dis.readInt()
                        val compressedSize = dis.readInt()
                        val compressedData = ByteArray(compressedSize)
                        dis.readFully(compressedData)
                        val pixels = decompressTile(compressedData)
                        tiles[Pair(tileX, tileY)] = pixels
                    }
                    layers.add(Layer(opacity = opacity, blendMode = blendMode, tiles = tiles))
                }
                frames.add(Frame(durationMs = duration, layers = layers.toMutableList()))
            }
            return Project(
                name = name,
                frames = frames.toMutableList(),
                canvasWidth = canvasWidth,
                canvasHeight = canvasHeight,
                fps = fps
            )
        }
    }

    private fun compressTile(pixels: IntArray): ByteArray {
        val bitmap = Bitmap.createBitmap(pixels, TILE_SIZE, TILE_SIZE, Bitmap.Config.ARGB_8888)
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun decompressTile(data: ByteArray): IntArray {
        val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
        val intArray = IntArray(TILE_SIZE * TILE_SIZE)
        bitmap.getPixels(intArray, 0, TILE_SIZE, 0, 0, TILE_SIZE, TILE_SIZE)
        return intArray
    }
}
