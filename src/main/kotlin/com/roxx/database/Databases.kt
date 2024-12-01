package com.roxx.database

import com.roxx.jwt.createJWT
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.exposed.sql.*
import java.time.Duration
import java.time.LocalDateTime

@OptIn(DelicateCoroutinesApi::class)
fun Application.configureRoutes() {
    val database = Database.connect(
        url = "jdbc:h2:file:./data/baaaase;DB_CLOSE_DELAY=-1;",
        driver = "org.h2.Driver"
    )
    val userService = AuctionService(database)
    GlobalScope.launch {
        while (true) {
            val now = LocalDateTime.now()
            val nextRun = now.withHour(23).withMinute(59).withSecond(59).plusDays(1)
            val delay = Duration.between(now, nextRun).toMillis()

            delay(delay)

            userService.randomExchange()
        }
    }
    routing {
        route("/roxx") {

            // greeting
            get("/") {
                call.respondText("Business is business")
            }

            // create user
            post("/users") {
                try {
                    val user = call.receive<UserRequestLogin>()
                    val id = userService.create(user)
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

            // user info
            get("/users/{id}") {
                val id = call.parameters["id"]?.toInt() ?: throw IllegalArgumentException("Invalid ID")
                val user = userService.read(id)
                if (user != null) {
                    call.respond(HttpStatusCode.OK, user)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            }

            // get top 10 users
            get("/top") {
                val topUsers = userService.getTopUserByBalance(limit = 10)
                if (topUsers.isNotEmpty()) {
                    call.respond(HttpStatusCode.OK, topUsers)
                } else {
                    call.respond(HttpStatusCode.NoContent, "No users found.")
                }
            }

            // Login return jwt
            post("/login") {
                val user = call.receive<UserRequestLogin>()
                val existingUser = userService.authenticate(user.username, user.password)

                if (existingUser != null) {
                    val token = createJWT(existingUser.id, user.password)
                    call.respond(HttpStatusCode.OK, mapOf("token" to token))
                } else {
                    call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
                }
            }

            // complete all bids
            get("/bids/complete") {
                userService.randomExchange()
                call.respond(HttpStatusCode.OK)
            }

            authenticate {
                // user balance
                get("/me/balance") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asInt()

                    if (userId != null) {
                        val user = userService.read(userId)
                        if (user != null) {
                            call.respond(HttpStatusCode.OK, user.balance)
                        } else {
                            call.respond(HttpStatusCode.NotFound, "User not found.")
                        }
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, "User ID not found in token.")
                    }
                }
                // user bids
                get("/me/bids") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asInt()

                    if (userId != null) {
                        val bids = userService.getBidsByUserId(userId)
                        if (bids.isNotEmpty()) {
                            call.respond(HttpStatusCode.OK, bids)
                        } else {
                            call.respond(HttpStatusCode.NotFound, "No bids found for the user.")
                        }
                    } else {
                        call.respond(HttpStatusCode.Unauthorized, "User Id not found in token.")
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

                    val user = userService.read(userId)

                    if (user != null) {
                        call.respond(HttpStatusCode.OK, user)
                    } else {
                        call.respond(HttpStatusCode.NotFound, "User not found.")
                    }
                }
                post("/me/bid") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asInt()

                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, "User  Id not found in token.")
                        return@post
                    }

                    val bidRequest = call.receive<BidRequest>()
                    if (bidRequest.amount <= 0) {
                        call.respond(HttpStatusCode.BadRequest, "Bid amount must be greater than zero.")
                        return@post
                    }

                    try {
                        val (bidId, updatedBalance) = userService.makeBid(userId, bidRequest.amount)

                        call.respond(HttpStatusCode.Created, mapOf("bidId" to bidId, "balance" to updatedBalance))
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, e.message ?: "Invalid request.")
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to place bid: ${e.message}")
                    }
                }
                // delete bid
                delete("/me/bids/{id}") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.getClaim("userId")?.asInt()

                    if (userId == null) {
                        call.respond(HttpStatusCode.Unauthorized, "User  Id not found in token.")
                        return@delete
                    }

                    val bidId = call.parameters["id"]?.toIntOrNull()
                    if (bidId == null) {
                        call.respond(HttpStatusCode.BadRequest, "Invalid bid Id.")
                        return@delete
                    }

                    val bid = userService.getBidById(bidId)
                    if (bid == null) {
                        call.respond(HttpStatusCode.NotFound, "Bid not found.")
                        return@delete
                    }

                    if (bid.userId != userId) {
                        call.respond(HttpStatusCode.Forbidden, "You can only delete your own bids.")
                        return@delete
                    }

                    try {
                        val updatedBalance = userService.deleteBid(bidId)
                        call.respond(HttpStatusCode.OK, updatedBalance)
                    } catch (e: Exception) {
                        call.respond(HttpStatusCode.InternalServerError, "Failed to delete bid: ${e.message}")
                    }
                }
            }
        }
    }
}