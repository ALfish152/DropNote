package com.example.dropnote

import android.content.Context
import com.example.dropnote.model.CreateNoteRequest
import com.example.dropnote.server.NoteStorage
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.defaultForFile
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.origin
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.jvm.javaio.copyTo
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
data class UploadResponse(
    val fileUrl: String,
    val fileName: String,
    val fileSize: String,
    val isImage: Boolean
)

object DropNoteServer {

    private var server: EmbeddedServer<*, *>? = null
    private var localHostIp: String = "127.0.0.1"
    private val activeClients = ConcurrentHashMap<String, Long>()
    private val _connectedClientsCount = MutableStateFlow(0)
    val connectedClientsCount = _connectedClientsCount.asStateFlow()

    private const val CLIENT_TIMEOUT_MS = 5_000L

    fun start(context: Context, hostIp: String = "127.0.0.1") {
        if (server != null) return

        localHostIp = hostIp
        val appContext = context.applicationContext
        val uploadDir = File(appContext.cacheDir, "dropnote_uploads").apply { mkdirs() }

        server = embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = true
                    isLenient = true
                })
            }

            launch {
                while (isActive) {
                    delay(1_500L)
                    pruneInactiveClients()
                }
            }

            routing {
                get("/") {
                    call.response.headers.append(
                        HttpHeaders.CacheControl,
                        "no-store, no-cache, must-revalidate"
                    )
                    call.respondBytes(
                        appContext.assets.open("index.html").use { it.readBytes() },
                        ContentType.Text.Html
                    )
                }

                get("/style.css") {
                    call.response.headers.append(
                        HttpHeaders.CacheControl,
                        "no-store, no-cache, must-revalidate"
                    )
                    call.respondBytes(
                        appContext.assets.open("style.css").use { it.readBytes() },
                        ContentType.parse("text/css")
                    )
                }

                get("/app.js") {
                    call.response.headers.append(
                        HttpHeaders.CacheControl,
                        "no-store, no-cache, must-revalidate"
                    )
                    call.respondBytes(
                        appContext.assets.open("app.js").use { it.readBytes() },
                        ContentType.parse("application/javascript")
                    )
                }

                get("/uploads/{fileName}") {
                    val fileName = call.parameters["fileName"]
                    if (fileName.isNullOrBlank() || fileName.contains("..")) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }
                    val target = File(uploadDir, fileName)
                    if (!target.exists()) {
                        call.respond(HttpStatusCode.NotFound)
                        return@get
                    }
                    call.respondFile(target)
                }

                post("/api/upload") {
                    val multipart = call.receiveMultipart()
                    var uploadResponse: UploadResponse? = null

                    multipart.forEachPart { part ->
                        if (part is PartData.FileItem) {
                            val originalName = part.originalFileName ?: "attachment"
                            val ext = originalName.substringAfterLast('.', "")
                            val storedName = if (ext.isNotEmpty()) {
                                "${UUID.randomUUID()}.$ext"
                            } else {
                                UUID.randomUUID().toString()
                            }
                            val targetFile = File(uploadDir, storedName)
                            part.provider().copyTo(targetFile.outputStream())

                            val sizeBytes = targetFile.length()
                            val formattedSize = if (sizeBytes < 1024 * 1024) {
                                String.format(Locale.US, "%.1f KB", sizeBytes / 1024.0)
                            } else {
                                String.format(Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))
                            }

                            val contentType = part.contentType?.toString()
                                ?: ContentType.defaultForFile(targetFile).toString()
                            val isImg = contentType.startsWith("image/") ||
                                    ext.lowercase() in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "svg")

                            uploadResponse = UploadResponse(
                                fileUrl = "/uploads/$storedName",
                                fileName = originalName,
                                fileSize = formattedSize,
                                isImage = isImg
                            )
                        }
                        part.dispose()
                    }

                    val result = uploadResponse
                    if (result != null) {
                        call.respond(result)
                    } else {
                        call.respondText("No file uploaded", status = HttpStatusCode.BadRequest)
                    }
                }

                get("/api/notes") {
                    val clientId = call.request.queryParameters["clientId"]
                    val remoteIp = call.request.origin.remoteAddress
                    val isHostDevice = clientId == "host-app" ||
                            remoteIp == "127.0.0.1" ||
                            remoteIp == "0:0:0:0:0:0:0:1" ||
                            remoteIp == localHostIp

                    if (!isHostDevice) {
                        activeClients[remoteIp] = System.currentTimeMillis()
                        pruneInactiveClients()
                    }
                    call.respond(NoteStorage.getNotes())
                }

                post("/api/notes") {
                    val request = call.receive<CreateNoteRequest>()

                    if (request.text.isBlank() && request.fileUrl.isNullOrBlank()) {
                        call.respondText(
                            "Note or attachment cannot be empty",
                            status = HttpStatusCode.BadRequest
                        )
                    } else {
                        val note = NoteStorage.addNote(request)
                        call.respond(HttpStatusCode.Created, note)
                    }
                }

                post("/api/notes/{id}/like") {
                    val id = call.parameters["id"] ?: return@post call.respond(HttpStatusCode.BadRequest)
                    val liked = call.request.queryParameters["liked"]?.toBooleanStrictOrNull() ?: true
                    val updated = NoteStorage.toggleLike(id, liked)
                    if (updated != null) {
                        call.respond(updated)
                    } else {
                        call.respond(HttpStatusCode.NotFound)
                    }
                }
            }
        }.start(wait = false)
    }

    private fun pruneInactiveClients() {
        val now = System.currentTimeMillis()
        activeClients.entries.removeIf { now - it.value > CLIENT_TIMEOUT_MS }
        _connectedClientsCount.value = activeClients.size
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        activeClients.clear()
        _connectedClientsCount.value = 0
    }

    fun isRunning(): Boolean = server != null
}