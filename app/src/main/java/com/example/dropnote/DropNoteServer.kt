
package com.example.dropnote

import android.content.Context
import com.example.dropnote.model.CreateNoteRequest
import com.example.dropnote.server.NoteStorage
import io.ktor.http.ContentType
import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.request.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*

object DropNoteServer {

    private var server: EmbeddedServer<*, *>? = null

    fun start(context: Context) {
        if (server != null) return

        val appContext = context.applicationContext

        server = embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
            install(ContentNegotiation) {
                json()
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
                    val request = call.receive<CreateNoteRequest>()

                    if (request.text.isBlank()) {
                        call.respondText(
                            "Note cannot be empty",
                            status = io.ktor.http.HttpStatusCode.BadRequest
                        )
                    } else {
                        val note = NoteStorage.addNote(request.text.trim())
                        call.respond(note)
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
