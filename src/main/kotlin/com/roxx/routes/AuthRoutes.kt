package com.roxx.routes

import com.roxx.repository.UserRequestLogin
import com.roxx.jwt.createJWT
import com.roxx.repository.AuctionService
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(auctionServiceImpl: AuctionService) {
    // Login return jwt
    post("/login") {
        val user = call.receive<UserRequestLogin>()
        val existingUser = auctionServiceImpl.authenticate(user.username, user.password)

        if (existingUser != null) {
            val token = createJWT(existingUser.id, user.username)
            call.respond(HttpStatusCode.OK, mapOf("token" to token))
        } else {
            call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
        }
    }

    authenticate {
        // validate token
        get("/protected") {
            val principal = call.principal<JWTPrincipal>()
            val userId = principal?.payload?.getClaim("userId")?.asInt()
            if (userId != null) {
                call.respond(HttpStatusCode.OK, mapOf("token" to "Token valid"))
            } else {
                call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
            }
        }
    }
}