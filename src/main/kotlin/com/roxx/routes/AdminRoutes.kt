package com.roxx.routes

import com.roxx.repository.AuctionServiceImpl
import com.roxx.repository.BidRequest
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.miskRoutes(auctionServiceImpl: AuctionServiceImpl) {
    var isAvailable = true
    // delete all data
    delete("/clear") {
        auctionServiceImpl.clearAllData()
        call.respondText("All data cleared successfully", status = HttpStatusCode.OK)
    }

    // start service
    post("/start") {
        if (!isAvailable) {
            isAvailable = true
            call.respondText("The service is enabled", ContentType.Text.Plain)
        } else {
            call.respond(HttpStatusCode.Conflict, "The service is already enabled")
        }
    }

    // stop service
    post("/stop") {
        if (isAvailable) {
            isAvailable = false
            call.respondText("The service is turned off", ContentType.Text.Plain)
        } else {
            call.respond(HttpStatusCode.Conflict, "The service is already turned off")
        }
    }

    // get service status
    get("/status") {
        if (isAvailable) {
            call.respondText("The service is available", ContentType.Text.Plain)
        } else {
            call.respond(HttpStatusCode.ServiceUnavailable, "The service is unavailable")
        }
    }

    // complete all bids
    get("/bids/complete") {
        auctionServiceImpl.randomExchange()
        call.respond(HttpStatusCode.OK)
    }

    // Add balance
    post("/update/{id}") {
        val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
        val money = call.receive<BidRequest>().amount
        if (money <= 0) {
            call.respond(HttpStatusCode.BadRequest, "Update less than 0")
        } else {
            auctionServiceImpl.updateUserBalance(id, money)
            call.respond(HttpStatusCode.OK)
        }
    }

    // user info
    get("/users/{id}") {
        val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
        val user = auctionServiceImpl.read(id)
        if (user != null) {
            call.respond(HttpStatusCode.OK, user)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }
}