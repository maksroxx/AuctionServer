package com.roxx.plugins

import com.roxx.repository.AuctionServiceImpl
import com.roxx.routes.authRoutes
import com.roxx.routes.bidRoutes
import com.roxx.routes.miskRoutes
import com.roxx.routes.userRoutes
import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.ktor.ext.inject
import java.time.Duration
import java.time.LocalDateTime

@OptIn(DelicateCoroutinesApi::class)
fun Application.configureRouting() {
    val auctionServiceImpl: AuctionServiceImpl by inject()

    routing {
        miskRoutes(auctionServiceImpl)
        userRoutes(auctionServiceImpl)
        bidRoutes(auctionServiceImpl)
        authRoutes(auctionServiceImpl)
        GlobalScope.launch {
            while (true) {
                val now = LocalDateTime.now()
                val nextRun = now.withHour(23).withMinute(59).withSecond(59).plusDays(1)
                val delay = Duration.between(now, nextRun).toMillis()

                delay(delay)

                auctionServiceImpl.randomExchange()
            }
        }
    }
}