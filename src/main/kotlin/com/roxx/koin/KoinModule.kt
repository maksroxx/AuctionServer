package com.roxx.koin

import com.roxx.repository.AuctionService
import com.roxx.repository.AuctionServiceImpl
import org.jetbrains.exposed.sql.Database
import org.koin.dsl.module

val appModule = module {
    single {
        Database.connect(
            url = "jdbc:h2:file:./data/baaaase;DB_CLOSE_DELAY=-1;",
            driver = "org.h2.Driver"
        )
    }
    single<AuctionService> {
        AuctionServiceImpl()
    }
}