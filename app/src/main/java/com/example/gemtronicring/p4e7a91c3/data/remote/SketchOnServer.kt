package com.example.gemtronicring.p4e7a91c3.data.remote

import com.example.gemtronicring.p4e7a91c3.domain.model.GalleryPiece
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The render server in SketchOn/server. The fal key stays there; the app never sees it. */
interface SketchOnServer {
    /** Returns the render as a data URI. The other arguments are the server's ids. */
    suspend fun render(
        sketchDataUri: String,
        material: String,
        style: String,
        view: String,
        seed: Int,
    ): String

    /** Every finished piece, newest first. */
    suspend fun library(): List<GalleryPiece>

    /** Fetches a small file, such as a gallery picture. */
    suspend fun bytes(url: String): ByteArray

    /** Starts turning a render into 3D, under the user's [name], and returns the job id. */
    suspend fun start3d(imageDataUri: String, name: String, material: String): String

    suspend fun job(jobId: String): JobStatus

    /** Downloads a finished model into [target]. */
    suspend fun download(modelUrl: String, target: File)
}

sealed interface JobStatus {
    data object Running : JobStatus

    data class Done(val modelUrl: String) : JobStatus

    data class Failed(val reason: String) : JobStatus
}

class HttpSketchOnServer(private val baseUrl: String) : SketchOnServer {
    override suspend fun render(
        sketchDataUri: String,
        material: String,
        style: String,
        view: String,
        seed: Int,
    ): String {
        val body =
            JSONObject()
                .put("sketch", sketchDataUri)
                .put("material", material)
                .put("style", style)
                .put("view", view)
                .put("engine", "klein")
                .put("seed", seed)
        return post("/api/render", body, RENDER_TIMEOUT_MS).getString("imageUrl")
    }

    override suspend fun start3d(imageDataUri: String, name: String, material: String): String =
        post(
                "/api/to3d",
                JSONObject().put("image", imageDataUri).put("name", name).put("material", material),
                RENDER_TIMEOUT_MS,
            )
            .getString("jobId")

    override suspend fun library(): List<GalleryPiece> {
        val pieces = get("/api/library").getJSONArray("pieces")
        return (0 until pieces.length()).map { i ->
            val p = pieces.getJSONObject(i)
            GalleryPiece(
                id = p.getString("id"),
                name = if (p.isNull("name")) null else p.optString("name").ifBlank { null },
                material = if (p.isNull("material")) null else p.optString("material").ifBlank { null },
                modelUrl = p.getString("modelUrl"),
                pictureUrl = if (p.isNull("pictureUrl")) null else p.getString("pictureUrl"),
            )
        }
    }

    override suspend fun bytes(url: String): ByteArray =
        withContext(Dispatchers.IO) {
            val connection = open(url, POLL_TIMEOUT_MS)
            try {
                check(connection)
                connection.inputStream.use { it.readBytes() }
            } finally {
                connection.disconnect()
            }
        }

    override suspend fun job(jobId: String): JobStatus {
        val json = get("/api/jobs/$jobId")
        return when (json.optString("status")) {
            "done" -> JobStatus.Done(json.getString("modelUrl"))
            "failed" -> JobStatus.Failed(json.optString("error", "3D conversion failed"))
            else -> JobStatus.Running
        }
    }

    override suspend fun download(modelUrl: String, target: File) =
        withContext(Dispatchers.IO) {
            val connection = open(modelUrl, DOWNLOAD_TIMEOUT_MS)
            try {
                check(connection)
                val partial = File(target.path + ".part")
                connection.inputStream.use { input ->
                    partial.outputStream().use { input.copyTo(it) }
                }
                if (!partial.renameTo(target)) throw IOException("could not save the model")
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun post(path: String, body: JSONObject, timeoutMs: Int): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = open(path, timeoutMs)
            try {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }
                check(connection)
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }

    private suspend fun get(path: String): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = open(path, POLL_TIMEOUT_MS)
            try {
                check(connection)
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }

    private fun open(path: String, timeoutMs: Int): HttpURLConnection =
        (URL(if (path.startsWith("http")) path else baseUrl + path).openConnection()
                as HttpURLConnection)
            .apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = timeoutMs
            }

    /** Turns a server error into an exception carrying the server's own reason. */
    private fun check(connection: HttpURLConnection) {
        val code = connection.responseCode
        if (code in 200..299) return
        val reason =
            runCatching {
                    JSONObject(connection.errorStream.bufferedReader().use { it.readText() })
                        .optString("error")
                }
                .getOrNull()
        throw IOException(reason?.takeIf { it.isNotBlank() } ?: "server error $code")
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val RENDER_TIMEOUT_MS = 60_000
        const val POLL_TIMEOUT_MS = 60_000
        const val DOWNLOAD_TIMEOUT_MS = 120_000
    }
}
