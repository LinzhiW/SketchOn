package com.example.gemtronicring.p4e7a91c3.ui.ringwear.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject

/**
 * The surface of a made ring, read from its GLB: every triangle's corners in the model's own
 * space, where each corner sits on the texture, and the texture itself. The brush paints on
 * this. Positions are in the mesh's own units, the same space the loaded model entity uses.
 */
internal class RingSurface(
    /** x, y, z per corner. */
    val positions: FloatArray,
    /** u, v per corner, 0..1, v down (as glTF and Android bitmaps both have it). */
    val uvs: FloatArray,
    /** Three corners per triangle. */
    val indices: IntArray,
    val texture: Bitmap,
) {
    companion object {
        private const val GLB_MAGIC = 0x46546C67
        private const val JSON_CHUNK = 0x4E4F534A
        private const val BIN_CHUNK = 0x004E4942
        private const val FLOAT = 5126
        private const val UNSIGNED_BYTE = 5121
        private const val UNSIGNED_SHORT = 5123
        private const val UNSIGNED_INT = 5125

        /**
         * Reads the surface, or null when the model has no texture or no texture coordinates
         * (rings made before clay and resin were given a texture). [maxTextureSize] caps the
         * painted texture's side, to keep painting and uploads quick.
         */
        fun read(file: File, maxTextureSize: Int): RingSurface? {
            val bytes = file.readBytes()
            val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            if (buf.getInt(0) != GLB_MAGIC) return null
            var offset = 12
            var json: JSONObject? = null
            var binStart = -1
            while (offset + 8 <= bytes.size) {
                val length = buf.getInt(offset)
                val type = buf.getInt(offset + 4)
                if (type == JSON_CHUNK) json = JSONObject(String(bytes, offset + 8, length, Charsets.UTF_8))
                if (type == BIN_CHUNK) binStart = offset + 8
                offset += 8 + length
            }
            if (json == null || binStart < 0) return null
            val gltf = Gltf(json, buf, binStart)

            val nodes = json.optJSONArray("nodes") ?: return null
            val meshIndex = (0 until nodes.length()).map { nodes.getJSONObject(it) }.firstOrNull { it.has("mesh") }?.getInt("mesh") ?: return null
            val primitives = json.getJSONArray("meshes").getJSONObject(meshIndex).getJSONArray("primitives")

            val positions = ArrayList<Float>()
            val uvs = ArrayList<Float>()
            val indices = ArrayList<Int>()
            var image = -1
            for (p in 0 until primitives.length()) {
                val prim = primitives.getJSONObject(p)
                val attributes = prim.getJSONObject("attributes")
                if (!attributes.has("POSITION") || !attributes.has("TEXCOORD_0")) return null
                val base = positions.size / 3
                val pos = gltf.floats(attributes.getInt("POSITION"))
                val uv = gltf.floats(attributes.getInt("TEXCOORD_0"))
                pos.forEach(positions::add)
                uv.forEach(uvs::add)
                val count = pos.size / 3
                if (prim.has("indices")) gltf.ints(prim.getInt("indices")).forEach { indices.add(base + it) }
                else for (i in 0 until count) indices.add(base + i)
                if (image < 0) image = gltf.baseColourImage(prim.optInt("material", -1))
            }
            if (image < 0) return null
            val picture = gltf.imageBytes(image) ?: return null
            val decoded = BitmapFactory.decodeByteArray(picture, 0, picture.size) ?: return null
            val side = maxOf(decoded.width, decoded.height)
            val scaled =
                if (side <= maxTextureSize) decoded
                else Bitmap.createScaledBitmap(
                    decoded,
                    decoded.width * maxTextureSize / side,
                    decoded.height * maxTextureSize / side,
                    true,
                )
            val texture = scaled.copy(Bitmap.Config.ARGB_8888, true)
            return RingSurface(positions.toFloatArray(), uvs.toFloatArray(), indices.toIntArray(), texture)
        }
    }

    /** Just enough of glTF to read accessors and the base colour image. */
    private class Gltf(val json: JSONObject, val buf: ByteBuffer, val binStart: Int) {
        private fun view(accessor: JSONObject): Triple<Int, Int, JSONObject> {
            val view = json.getJSONArray("bufferViews").getJSONObject(accessor.getInt("bufferView"))
            val start = binStart + view.optInt("byteOffset", 0) + accessor.optInt("byteOffset", 0)
            return Triple(start, view.optInt("byteStride", 0), view)
        }

        private fun components(type: String) =
            when (type) {
                "SCALAR" -> 1
                "VEC2" -> 2
                "VEC3" -> 3
                "VEC4" -> 4
                else -> error("accessor type $type")
            }

        fun floats(index: Int): FloatArray {
            val a = json.getJSONArray("accessors").getJSONObject(index)
            val n = components(a.getString("type"))
            val count = a.getInt("count")
            val type = a.getInt("componentType")
            val size = when (type) { FLOAT -> 4; UNSIGNED_SHORT -> 2; UNSIGNED_BYTE -> 1; else -> error("component $type") }
            val (start, stride0, _) = view(a)
            val stride = if (stride0 > 0) stride0 else n * size
            val out = FloatArray(count * n)
            for (i in 0 until count) for (c in 0 until n) {
                val at = start + i * stride + c * size
                out[i * n + c] =
                    when (type) {
                        FLOAT -> buf.getFloat(at)
                        UNSIGNED_SHORT -> (buf.getShort(at).toInt() and 0xFFFF) / 65535f
                        else -> (buf.get(at).toInt() and 0xFF) / 255f
                    }
            }
            return out
        }

        fun ints(index: Int): IntArray {
            val a = json.getJSONArray("accessors").getJSONObject(index)
            val count = a.getInt("count")
            val type = a.getInt("componentType")
            val (start, _, _) = view(a)
            return IntArray(count) { i ->
                when (type) {
                    UNSIGNED_INT -> buf.getInt(start + i * 4)
                    UNSIGNED_SHORT -> buf.getShort(start + i * 2).toInt() and 0xFFFF
                    else -> buf.get(start + i).toInt() and 0xFF
                }
            }
        }

        fun baseColourImage(material: Int): Int {
            if (material < 0) return -1
            val m = json.optJSONArray("materials")?.optJSONObject(material) ?: return -1
            val texture = m.optJSONObject("pbrMetallicRoughness")?.optJSONObject("baseColorTexture") ?: return -1
            val t = json.optJSONArray("textures")?.optJSONObject(texture.getInt("index")) ?: return -1
            return t.optInt("source", -1)
        }

        fun imageBytes(image: Int): ByteArray? {
            val img = json.optJSONArray("images")?.optJSONObject(image) ?: return null
            if (!img.has("bufferView")) return null
            val view = json.getJSONArray("bufferViews").getJSONObject(img.getInt("bufferView"))
            val start = binStart + view.optInt("byteOffset", 0)
            val out = ByteArray(view.getInt("byteLength"))
            buf.position(start)
            buf.get(out)
            return out
        }
    }
}
