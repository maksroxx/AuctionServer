package com.roxx

import com.roxx.database.configureRoutes
import com.roxx.jwt.configureAuth
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureSerialization()
    configureAuth()
    configureRoutes()
    configureMonitoring()
    configureHTTP()
}
