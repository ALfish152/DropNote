package com.example.dropnote

import io.ktor.server.engine.*
import io.ktor.server.cio.*
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.response.*

object DropNoteServer {

    private var server: EmbeddedServer<*, *>? = null

    fun start() {
        if (server != null) return

        server = embeddedServer(CIO, port = 8080, host = "0.0.0.0") {
            routing {
                get("/") {
                    call.respondText("DropNote server is running!")
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