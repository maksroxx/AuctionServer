package com.roxx.routes

import com.roxx.repository.UserRequestLogin
import com.roxx.jwt.createJWT
import com.roxx.repository.AuctionService
import com.roxx.repository.SearchRequest
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.userRoutes(auctionServiceImpl: AuctionService) {
    // create user
    post("/users") {
        try {
            val user = call.receive<UserRequestLogin>()
            val id = auctionServiceImpl.create(user)
            if (id == -1) {
                call.respond(HttpStatusCode.Conflict, "User has already exists.")
            } else {
                val token = createJWT(id, user.username)
                call.respond(HttpStatusCode.Created, mapOf("token" to token))
            }
        } catch (e: Exception) {
            call.respond(HttpStatusCode.BadRequest, "Error: ${e.message}")
        }
    }

    // get top 10 users
    get("/top") {
        val topUsers = auctionServiceImpl.getTopUserByBalance(limit = 10)
        if (topUsers.isNotEmpty()) {
            call.respond(HttpStatusCode.OK, topUsers)
        } else {
            call.respond(HttpStatusCode.NoContent, "No users found.")
        }
    }

    authenticate {
        // user balance
        get("/me/balance") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asInt()

            if (userId != null) {
                val user = auctionServiceImpl.read(userId)
                if (user != null) {
                    call.respond(HttpStatusCode.OK, user.balance)
                } else {
                    call.respond(HttpStatusCode.NotFound, "User not found.")
                }
            } else {
                call.respond(HttpStatusCode.Unauthorized, "User ID not found in token.")
            }
        }
        // user info auth
        get("/me") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asInt()
            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "Authentication is required.")
                return@get
            }

            val user = auctionServiceImpl.read(userId)

            if (user != null) {
                call.respond(HttpStatusCode.OK, user)
            } else {
                call.respond(HttpStatusCode.NotFound, "User not found.")
            }
        }

        // search user
        post("/search") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asInt()

            if (userId == null) {
                call.respond(HttpStatusCode.Unauthorized, "User Id not found in token.")
                return@post
            }

            val searchRequest = call.receive<SearchRequest>()
            if (searchRequest.username.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "Search is empty")
                return@post
            }

            try {
                val user = auctionServiceImpl.searchUser(username = searchRequest.username)
                if (user == null) {
                    call.respond(HttpStatusCode.NoContent, "No users found.")
                } else {
                    call.respond(HttpStatusCode.OK, user)
                }
            } catch (e: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid request.")
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, "Failed to search user: ${e.message}")
            }
        }
    }
}