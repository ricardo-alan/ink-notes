package app.inknotes.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.UUID

/** Drive rejected the access token; get a fresh one and try again. */
class DriveAuthException : IOException("Drive access token rejected")

class DriveHttpException(val code: Int, body: String) : IOException("Drive HTTP $code: ${body.take(300)}")

/**
 * The few Drive v3 REST calls sync needs, over plain HttpURLConnection. Everything lives in the
 * hidden appDataFolder, which only Ink can see and which the drive.appdata scope is limited to.
 */
class DriveClient(private val accessToken: String) {

    @Serializable
    data class DriveFile(val id: String, val name: String, val appProperties: Map<String, String> = emptyMap())

    @Serializable
    private data class CreatedFile(val id: String)

    @Serializable
    private data class FileList(val files: List<DriveFile> = emptyList(), val nextPageToken: String? = null)

    @Serializable
    private data class About(val user: User? = null) {
        @Serializable
        data class User(val emailAddress: String? = null)
    }

    suspend fun listAppData(): List<DriveFile> {
        val all = mutableListOf<DriveFile>()
        var pageToken: String? = null
        do {
            val url = buildString {
                append("$API/files?spaces=appDataFolder&pageSize=1000")
                append("&fields=").append(enc("nextPageToken,files(id,name,appProperties)"))
                if (pageToken != null) append("&pageToken=").append(enc(pageToken))
            }
            val page = syncJson.decodeFromString<FileList>(request("GET", url))
            all += page.files
            pageToken = page.nextPageToken
        } while (pageToken != null)
        return all
    }

    suspend fun download(fileId: String): String = request("GET", "$API/files/${enc(fileId)}?alt=media")

    /** Creates a file in appDataFolder and returns its id. */
    suspend fun create(name: String, appProperties: Map<String, String>, json: String): String {
        val metadata = buildJsonObject {
            put("name", name)
            putJsonArray("parents") { add("appDataFolder") }
            putJsonObject("appProperties") { appProperties.forEach { (k, v) -> put(k, v) } }
        }.toString()
        val response = multipart("POST", "$UPLOAD/files?uploadType=multipart&fields=id", metadata, json)
        return syncJson.decodeFromString<CreatedFile>(response).id
    }

    suspend fun update(fileId: String, appProperties: Map<String, String>, json: String) {
        val metadata = buildJsonObject {
            putJsonObject("appProperties") { appProperties.forEach { (k, v) -> put(k, v) } }
        }.toString()
        multipart("PATCH", "$UPLOAD/files/${enc(fileId)}?uploadType=multipart&fields=id", metadata, json)
    }

    suspend fun delete(fileId: String) {
        request("DELETE", "$API/files/${enc(fileId)}")
    }

    /** The signed-in account's email, shown in Settings. */
    suspend fun accountEmail(): String? =
        syncJson.decodeFromString<About>(request("GET", "$API/about?fields=user(emailAddress)")).user?.emailAddress

    private suspend fun multipart(method: String, url: String, metadata: String, json: String): String {
        val boundary = "ink-" + UUID.randomUUID()
        val body = buildString {
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").append(metadata).append("\r\n")
            append("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").append(json).append("\r\n")
            append("--$boundary--\r\n")
        }
        return request(method, url, body.toByteArray(), "multipart/related; boundary=$boundary")
    }

    private suspend fun request(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            // HttpURLConnection can't send PATCH; Google APIs accept it as an override.
            if (method == "PATCH") {
                connection.requestMethod = "POST"
                connection.setRequestProperty("X-HTTP-Method-Override", "PATCH")
            } else {
                connection.requestMethod = method
            }
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            if (code in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                val error = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code == 401) throw DriveAuthException()
                throw DriveHttpException(code, error)
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        const val TIMEOUT_MS = 30_000

        fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    }
}
