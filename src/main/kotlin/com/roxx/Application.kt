package com.roxx

import com.roxx.plugins.*
import io.ktor.server.application.*

fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

fun Application.module() {
    configureKoin()
    configureDatabase()
    configureSerialization()
    configureAuth()
    configureMonitoring()
    configureHTTP()

    auctionModule()
}

fun Application.auctionModule() {
    configureRouting()
}