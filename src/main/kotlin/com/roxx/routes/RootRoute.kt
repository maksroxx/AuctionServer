package com.roxx.routes

import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.root() {
    // greeting
    get("/") {
        call.respondText("Business is business")
    }
}