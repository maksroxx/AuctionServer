package com.roxx.database

import org.jetbrains.exposed.sql.Table

object Users : Table() {
    val id = integer("id").autoIncrement()
    val username = varchar("username", 255).uniqueIndex()
    val password = varchar("password", 255)
    val balance = integer("balance")

    override val primaryKey = PrimaryKey(id)
}

object Bids : Table() {
    val id = integer("id").autoIncrement()
    val userId = integer("user_id").references(Users.id)
    val amount = integer("amount")
    val createdAt = integer("created_at")
    val status = varchar("status", 20)
    val profit = integer("profit")
    val itemTitle = varchar("item_title", 255)

    override val primaryKey = PrimaryKey(id)
}
