package com.roxx.routes

import com.roxx.repository.AuctionService

import com.roxx.repository.BidRequest
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.routing.*
import io.ktor.server.response.*

fun Route.bidRoutes(auctionServiceImpl: AuctionService) {
    authenticate {
        // user bids
        get("/me/bids") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asInt()

            if (userId != null) {
                val bids = auctionServiceImpl.getBidsByUserId(userId)
                call.respond(HttpStatusCode.OK, bids)
            } else {
                call.respond(HttpStatusCode.Unauthorized, "User Id not found in token.")
            }
        }

        // last user bid
        get("/me/bid/last") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asInt()

            if (userId != null) {
                val lastBidId = auctionServiceImpl.getLastActiveBidIdToday(userId)
                call.respond(HttpStatusCode.OK, mapOf("lastBidId" to lastBidId))
            } else {
                call.respond(HttpStatusCode.Unauthorized, "User ID not found in token.")
            }
        }

        // delete bid
        delete("/me/bids/{id}") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asInt()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "User Id not found in token.")
                return@delete
            }

            val bidId = call.parameters["id"]?.toIntOrNull()
            if (bidId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid bid Id.")
                return@delete
            }

            val bid = auctionServiceImpl.getBidById(bidId)
            if (bid == null) {
                call.respond(HttpStatusCode.NotFound, "Bid not found.")
                return@delete
            }

            if (bid.userId != userId) {
                call.respond(HttpStatusCode.Forbidden, "You can only delete your own bids.")
                return@delete
            }

            try {
                val updatedBalance = auctionServiceImpl.deleteBid(bidId)
                call.respond(HttpStatusCode.OK, mapOf("amount" to updatedBalance))
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to delete bid: ${e.message}")
            }
        }

        // make bid
        post("/me/bid") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asInt()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "User Id not found in token.")
                return@post
            }

            val bidRequest = call.receive<BidRequest>()
            if (bidRequest.amount <= 0) {
                call.respond(HttpStatusCode.BadRequest, "Bid amount must be greater than zero.")
                return@post
            }

            try {
                val (bidId, updatedBalance) = auctionServiceImpl.makeBid(userId, bidRequest.amount)

                call.respond(HttpStatusCode.Created, mapOf("bidId" to bidId, "balance" to updatedBalance))
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid request.")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to place bid: ${e.message}")
            }
        }
    }
}