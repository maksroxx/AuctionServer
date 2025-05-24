package com.roxx.plugins

import com.roxx.database.Bids
import com.roxx.database.Users
import io.ktor.server.application.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.koin.mp.KoinPlatform.getKoin

fun Application.configureDatabase() {
    val database: Database = getKoin().get()
    transaction(db = database) {
        SchemaUtils.create(Users, Bids)
    }
}