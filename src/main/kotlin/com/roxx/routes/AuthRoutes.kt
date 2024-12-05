package com.roxx.routes

import com.roxx.repository.AuctionServiceImpl
import com.roxx.repository.UserRequestLogin
import com.roxx.jwt.createJWT
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(auctionServiceImpl: AuctionServiceImpl) {
    // Login return jwt
    post("/login") {
        val user = call.receive<UserRequestLogin>()
        val existingUser  = auctionServiceImpl.authenticate(user.username, user.password)

        if (existingUser  != null) {
            val token = createJWT(existingUser.id, user.password)
            call.respond(HttpStatusCode.OK, mapOf("token" to token))
        } else {
            call.respond(HttpStatusCode.Unauthorized, "Invalid credentials")
        }
    }
}