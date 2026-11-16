package com.example.gemtronicring.p4e7a91c3.data.remote

import android.util.Base64
import com.example.gemtronicring.p4e7a91c3.domain.model.Colourway
import com.example.gemtronicring.p4e7a91c3.domain.model.GalleryPiece
import com.example.gemtronicring.p4e7a91c3.domain.model.MyColourway
import com.example.gemtronicring.p4e7a91c3.domain.model.MyLibrary
import com.example.gemtronicring.p4e7a91c3.domain.model.Notice
import com.example.gemtronicring.p4e7a91c3.domain.model.NoticeKind
import com.example.gemtronicring.p4e7a91c3.domain.model.RecordStatus
import com.example.gemtronicring.p4e7a91c3.domain.model.RingPage
import com.example.gemtronicring.p4e7a91c3.domain.model.RingRecord
import org.json.JSONArray
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** The server answered 401: the user must log in (again). */
class NotLoggedInException(message: String) : IOException(message)

/** A signed-in user and the token that proves it. */
data class Session(val token: String, val name: String)

/** The render server in SketchOn/server. The fal key stays there; the app never sees it. */
interface SketchOnServer {
    /** Sent with every request once the user has logged in. */
    var token: String?

    suspend fun signUp(name: String, password: String): Session

    suspend fun logIn(name: String, password: String): Session

    suspend fun logOut()

    /** The signed-in user's name; throws [NotLoggedInException] if the token is not valid. */
    suspend fun me(): String

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

    /** Gives a piece its name after its job has started. */
    suspend fun nameJob(jobId: String, name: String)

    /** Downloads a finished model into [target]. */
    suspend fun download(modelUrl: String, target: File)

    // Colourways, likes, saves, wears and notifications (docs/colourways-plan.md).
    // Defaults let a test double leave out what it does not use.

    /** One ring's page. */
    suspend fun ringPage(pieceId: String): RingPage = unsupported()

    /** Saves the colours on a ring, with the painted texture as PNG bytes if there is one. */
    suspend fun saveColourway(pieceId: String, paints: Map<Int, Int>, texturePng: ByteArray?): String = unsupported()

    suspend fun deleteColourway(id: String): Unit = unsupported()

    suspend fun featureColourway(id: String, on: Boolean): Unit = unsupported()

    suspend fun hideColourway(id: String, on: Boolean): Unit = unsupported()

    /** The ring's cover: one of the author's own colourways, or null for the render. */
    suspend fun setCover(pieceId: String, colourwayId: String?): Unit = unsupported()

    suspend fun deletePiece(pieceId: String): Unit = unsupported()

    suspend fun like(kind: String, id: String, on: Boolean): Unit = unsupported()

    suspend fun saveRing(pieceId: String, on: Boolean): Unit = unsupported()

    /** Records that the user put on a ring, as made or in a colourway. */
    suspend fun wear(pieceId: String, colourwayId: String?): Unit = unsupported()

    suspend fun myLibrary(): MyLibrary = unsupported()

    suspend fun notices(): List<Notice> = unsupported()

    suspend fun markNoticesSeen(): Unit = unsupported()
}

private fun unsupported(): Nothing = throw IOException("not supported")

sealed interface JobStatus {
    data object Running : JobStatus

    data class Done(val modelUrl: String) : JobStatus

    data class Failed(val reason: String) : JobStatus
}

class HttpSketchOnServer(private val baseUrl: String) : SketchOnServer {
    @Volatile override var token: String? = null

    override suspend fun signUp(name: String, password: String) = session("/api/signup", name, password)

    override suspend fun logIn(name: String, password: String) = session("/api/login", name, password)

    private suspend fun session(path: String, name: String, password: String): Session {
        val json = post(path, JSONObject().put("name", name).put("password", password), POLL_TIMEOUT_MS)
        return Session(json.getString("token"), json.getJSONObject("user").getString("name"))
    }

    override suspend fun logOut() {
        runCatching { post("/api/logout", JSONObject(), POLL_TIMEOUT_MS) }
        token = null
    }

    override suspend fun me(): String = get("/api/me").getJSONObject("user").getString("name")

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
                author = if (p.isNull("author")) null else p.optString("author").ifBlank { null },
                material = if (p.isNull("material")) null else p.optString("material").ifBlank { null },
                modelUrl = p.getString("modelUrl"),
                pictureUrl = if (p.isNull("pictureUrl")) null else p.getString("pictureUrl"),
                likes = p.optInt("likes"),
                saves = p.optInt("saves"),
                cover = p.optJSONObject("cover")?.let(::colourwayOf),
            )
        }
    }

    override suspend fun ringPage(pieceId: String): RingPage {
        val json = get("/api/pieces/$pieceId")
        val p = json.getJSONObject("piece")
        return RingPage(
            piece =
                GalleryPiece(
                    id = p.getString("id"),
                    name = p.textOrNull("name"),
                    author = p.textOrNull("author"),
                    material = p.textOrNull("material"),
                    modelUrl = p.getString("modelUrl"),
                    pictureUrl = p.textOrNull("pictureUrl"),
                ),
            isAuthor = p.optBoolean("isAuthor"),
            coverId = p.textOrNull("cover"),
            likes = json.optInt("likes"),
            saves = json.optInt("saves"),
            wearers = json.optInt("wearers"),
            likedByMe = json.optBoolean("likedByMe"),
            savedByMe = json.optBoolean("savedByMe"),
            authorColourways = json.getJSONArray("authorColourways").objects().map(::colourwayOf),
            otherColourways = json.getJSONArray("otherColourways").objects().map(::colourwayOf),
        )
    }

    override suspend fun saveColourway(pieceId: String, paints: Map<Int, Int>, texturePng: ByteArray?): String {
        val body = JSONObject().put("paints", JSONObject().apply { paints.forEach { (part, c) -> put(part.toString(), c) } })
        texturePng?.let { body.put("texture", "data:image/png;base64," + Base64.encodeToString(it, Base64.NO_WRAP)) }
        return post("/api/pieces/$pieceId/colourways", body, RENDER_TIMEOUT_MS).getString("id")
    }

    override suspend fun deleteColourway(id: String) {
        send("DELETE", "/api/colourways/$id", null)
    }

    override suspend fun featureColourway(id: String, on: Boolean) {
        post("/api/colourways/$id/feature", JSONObject().put("on", on), POLL_TIMEOUT_MS)
    }

    override suspend fun hideColourway(id: String, on: Boolean) {
        post("/api/colourways/$id/hide", JSONObject().put("on", on), POLL_TIMEOUT_MS)
    }

    override suspend fun setCover(pieceId: String, colourwayId: String?) {
        post("/api/pieces/$pieceId/cover", JSONObject().put("colourwayId", colourwayId ?: JSONObject.NULL), POLL_TIMEOUT_MS)
    }

    override suspend fun deletePiece(pieceId: String) {
        send("DELETE", "/api/pieces/$pieceId", null)
    }

    override suspend fun like(kind: String, id: String, on: Boolean) {
        post("/api/likes", JSONObject().put("kind", kind).put("id", id).put("on", on), POLL_TIMEOUT_MS)
    }

    override suspend fun saveRing(pieceId: String, on: Boolean) {
        post("/api/saves", JSONObject().put("pieceId", pieceId).put("on", on), POLL_TIMEOUT_MS)
    }

    override suspend fun wear(pieceId: String, colourwayId: String?) {
        post("/api/wears", JSONObject().put("pieceId", pieceId).put("colourwayId", colourwayId ?: JSONObject.NULL), POLL_TIMEOUT_MS)
    }

    override suspend fun myLibrary(): MyLibrary {
        val json = get("/api/me/library")
        return MyLibrary(
            rings =
                json.getJSONArray("rings").objects().map { r ->
                    GalleryPiece(
                        id = r.getString("id"),
                        name = r.textOrNull("name"),
                        author = null,
                        material = r.textOrNull("material"),
                        modelUrl = r.getString("modelUrl"),
                        pictureUrl = r.textOrNull("pictureUrl"),
                    )
                },
            colourways =
                json.getJSONArray("colourways").objects().map { c ->
                    MyColourway(
                        colourway = colourwayOf(c),
                        pieceName = c.textOrNull("pieceName"),
                        pieceAuthor = c.textOrNull("pieceAuthor"),
                        onMyRing = c.optBoolean("onMyRing"),
                        status = RecordStatus.of(c.textOrNull("status")),
                    )
                },
            saved =
                json.getJSONArray("saved").objects().map { s ->
                    RingRecord(s.getString("id"), s.textOrNull("name"), s.textOrNull("author"), RecordStatus.of(s.textOrNull("status")))
                },
            worn =
                json.getJSONArray("wears").objects().map { w ->
                    RingRecord(
                        pieceId = w.getString("pieceId"),
                        name = w.textOrNull("name"),
                        author = w.textOrNull("author"),
                        status = RecordStatus.of(w.textOrNull("status")),
                        colourwayId = w.textOrNull("colourwayId"),
                        paints = w.optJSONObject("paints")?.let(::paintsOf),
                    )
                },
        )
    }

    override suspend fun notices(): List<Notice> =
        get("/api/notifications").getJSONArray("notifications").objects().map { n ->
            Notice(
                id = n.getLong("id"),
                kind = if (n.optString("kind") == "featured") NoticeKind.Featured else NoticeKind.Recoloured,
                pieceId = n.getString("pieceId"),
                pieceName = n.textOrNull("pieceName"),
                actor = n.optString("actor"),
                seen = n.optBoolean("seen"),
            )
        }

    override suspend fun markNoticesSeen() {
        post("/api/notifications/seen", JSONObject(), POLL_TIMEOUT_MS)
    }

    private fun colourwayOf(c: JSONObject) =
        Colourway(
            id = c.getString("id"),
            pieceId = c.getString("pieceId"),
            author = c.optString("author"),
            paints = c.optJSONObject("paints")?.let(::paintsOf) ?: emptyMap(),
            textureUrl = c.textOrNull("textureUrl"),
            pictureUrl = c.textOrNull("pictureUrl"),
            featured = c.optBoolean("featured"),
            hidden = c.optBoolean("hidden"),
            likes = c.optInt("likes"),
            likedByMe = c.optBoolean("likedByMe"),
            wearers = c.optInt("wearers"),
        )

    private fun paintsOf(p: JSONObject): Map<Int, Int> =
        p.keys().asSequence().mapNotNull { k -> k.toIntOrNull()?.let { it to p.getInt(k) } }.toMap()

    private fun JSONObject.textOrNull(key: String): String? = if (isNull(key)) null else optString(key).ifBlank { null }

    private fun JSONArray.objects(): List<JSONObject> = (0 until length()).map { getJSONObject(it) }

    /** A request with an optional JSON body, for verbs other than GET and POST. */
    private suspend fun send(method: String, path: String, body: JSONObject?): JSONObject =
        withContext(Dispatchers.IO) {
            val connection = open(path, POLL_TIMEOUT_MS)
            try {
                connection.requestMethod = method
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toString().toByteArray()) }
                }
                check(connection)
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
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

    override suspend fun nameJob(jobId: String, name: String) {
        post("/api/jobs/$jobId/name", JSONObject().put("name", name), POLL_TIMEOUT_MS)
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
                // Only to our own server: model and picture URLs are relative to it.
                token?.let { if (!path.startsWith("http")) setRequestProperty("Authorization", "Bearer $it") }
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
        val message = reason?.takeIf { it.isNotBlank() } ?: "server error $code"
        if (code == 401 && message != "Wrong name or password.") throw NotLoggedInException(message)
        throw IOException(message)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val RENDER_TIMEOUT_MS = 60_000
        const val POLL_TIMEOUT_MS = 60_000
        const val DOWNLOAD_TIMEOUT_MS = 120_000
    }
}
