
package com.example.dropnote

import android.content.Context
import com.example.dropnote.model.CreateNoteRequest
import com.example.dropnote.server.NoteStorage
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json

object DropNoteServer {

    private var server: EmbeddedServer<*, *>? = null

    fun start(context: Context) {
        if (server != null) return

        val appContext = context.applicationContext

        server = embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                })
            }

            routing {
                get("/") {
                    call.respondBytes(
                        appContext.assets.open("index.html").use { it.readBytes() },
                        ContentType.Text.Html
                    )
                }

                get("/style.css") {
                    call.respondBytes(
                        appContext.assets.open("style.css").use { it.readBytes() },
                        ContentType.parse("text/css")
                    )
                }

                get("/app.js") {
                    call.respondBytes(
                        appContext.assets.open("app.js").use { it.readBytes() },
                        ContentType.parse("application/javascript")
                    )
                }

                get("/api/notes") {
                    call.respond(NoteStorage.getNotes())
                }

                post("/api/notes") {
                    try {
                        val request = call.receive<CreateNoteRequest>()
                        when (val result = NoteStorage.addNote(request)) {
                            is NoteStorage.AddNoteResult.Success -> {
                                call.respond(HttpStatusCode.Created, result.note)
                            }
                            is NoteStorage.AddNoteResult.Error -> {
                                call.respondText(
                                    result.message,
                                    status = HttpStatusCode.BadRequest
                                )
                            }
                        }
                    } catch (e: Exception) {
                        call.respondText(
                            "Invalid request format",
                            status = HttpStatusCode.BadRequest
                        )
                    }
                }

                post("/api/notes/{id}/like") {
                    val noteId = call.parameters["id"]
                    val action = call.request.queryParameters["action"] ?: "like"
                    if (noteId.isNullOrBlank()) {
                        call.respondText("Missing note ID", status = HttpStatusCode.BadRequest)
                        return@post
                    }

                    val updated = NoteStorage.toggleLike(noteId, increment = (action == "like"))
                    if (updated != null) {
                        call.respond(updated)
                    } else {
                        call.respondText("Note not found", status = HttpStatusCode.NotFound)
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
    }

    fun isRunning(): Boolean = server != null
}
