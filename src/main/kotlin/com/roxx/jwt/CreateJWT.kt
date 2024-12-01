package com.roxx.jwt

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

fun createJWT(userId: Int, username: String): String {
    val jwtSecret = "secret"
    return JWT.create()
        .withAudience("jwt-audience")
        .withIssuer("https://jwt-provider-domain/")
        .withClaim("userId", userId)
        .withClaim("username", username)
        .withExpiresAt(Date(System.currentTimeMillis() + 604800000)) // Token expires in 7 days
        .sign(Algorithm.HMAC256(jwtSecret))
}