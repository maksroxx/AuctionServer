package com.roxx.repository

import com.roxx.database.Bids
import com.roxx.database.Users
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.*

data class UserRequestLogin(val username: String, val password: String)
data class UserRespond(val id: Int, val username: String, val balance: Int)
data class UsersRespond(val username: String, val balance: Int)
data class BidRequest(val amount: Int)
data class Bid(val id: Int, val userId: Int, val amount: Int, val createdAt: Int, val status: String, val profit: Int)

enum class BidStatus {
    ACTIVE,
    COMPLETED
}

class AuctionServiceImpl : AuctionService {
    override suspend fun create(user: UserRequestLogin): Int = dbQuery {
        val existingUser = Users.selectAll().where { Users.username eq user.username }.singleOrNull()
        if (existingUser != null) {
            return@dbQuery -1
        }
        Users.insert {
            it[username] = user.username
            it[password] = user.password
            it[balance] = (10000..1000000).random()
        }[Users.id]
    }

    override suspend fun read(id: Int): UserRespond? {
        return dbQuery {
            Users.selectAll()
                .where { Users.id eq id }
                .map {
                    UserRespond(
                        id = it[Users.id],
                        username = it[Users.username],
                        balance = it[Users.balance]
                    )
                }
                .singleOrNull()
        }
    }

    override suspend fun authenticate(username: String, password: String): UserRespond? {
        return dbQuery {
            Users.selectAll().where { Users.username eq username and (Users.password eq password) }
                .map {
                    UserRespond(
                        id = it[Users.id],
                        username = it[Users.username],
                        balance = it[Users.balance]
                    )
                }
                .singleOrNull()
        }
    }

    override suspend fun getTopUserByBalance(limit: Int): List<UsersRespond> = dbQuery {
        Users.selectAll()
            .orderBy(Users.balance, SortOrder.DESC)
            .limit(limit)
            .map { row ->
                UsersRespond(
                    username = row[Users.username],
                    balance = row[Users.balance]
                )
            }
    }

    override suspend fun makeBid(userId: Int, amount: Int): Pair<Int, Int> {
        return dbQuery {
            transaction {
                val userBalance = Users.selectAll().where { Users.id.eq(userId) }
                    .map { it[Users.balance] }
                    .singleOrNull() ?: throw IllegalArgumentException("User not found")

                if (userBalance < amount) {
                    throw IllegalArgumentException("Insufficient balance")
                }

                Users.update({ Users.id.eq(userId) }) {
                    it[balance] = userBalance - amount
                }

                val bidId = Bids.insert {
                    it[this.userId] = userId
                    it[this.amount] = amount
                    it[createdAt] = Instant.now().epochSecond.toInt()
                    it[status] = BidStatus.ACTIVE.name
                    it[profit] = 0
                }[Bids.id]

                val updatedBalance = userBalance - amount
                Pair(bidId, updatedBalance)
            }
        }
    }

    override suspend fun deleteBid(id: Int): Int {
        return dbQuery {
            transaction {
                val bid = Bids.selectAll().where { Bids.id.eq(id) }.singleOrNull()
                    ?: throw IllegalArgumentException("Bid not found")

                val userId = bid[Bids.userId]
                val amount = bid[Bids.amount]

                Users.update({ Users.id.eq(userId) }) {
                    val currentBalance = Users.selectAll().where { Users.id.eq(userId) }
                        .map { it[balance] }
                        .singleOrNull() ?: throw IllegalArgumentException("User not found")

                    it[balance] = currentBalance + amount
                }

                Bids.deleteWhere { Bids.id.eq(id) }

                val updatedBalance = Users.selectAll().where { Users.id.eq(userId) }
                    .map { it[Users.balance] }
                    .singleOrNull() ?: throw IllegalArgumentException("User not found")
                updatedBalance
            }
        }
    }


    override suspend fun getBidById(bidId: Int): Bid? {
        return dbQuery {
            Bids.selectAll().where { Bids.id eq bidId }
                .mapNotNull { row ->
                    Bid(
                        id = row[Bids.id],
                        userId = row[Bids.userId],
                        amount = row[Bids.amount],
                        createdAt = row[Bids.createdAt],
                        status = row[Bids.status],
                        profit = row[Bids.profit]
                    )
                }
                .singleOrNull()
        }
    }

    override suspend fun getBidsByUserId(userId: Int): List<Bid> = dbQuery {
        Bids.selectAll().where { Bids.userId eq userId }
            .map { row ->
                Bid(
                    id = row[Bids.id],
                    userId = row[Bids.userId],
                    amount = row[Bids.amount],
                    createdAt = row[Bids.createdAt],
                    status = row[Bids.status],
                    profit = row[Bids.profit]
                )
            }
    }

    override suspend fun clearAllData() {
        dbQuery {
            transaction {
                Bids.deleteAll()
                Users.deleteAll()
            }
        }
    }

    override suspend fun randomExchange() {
        val bidsToday = dbQuery {
            Bids.selectAll().where {
                Bids.status eq BidStatus.ACTIVE.name
            }
                .map { it[Bids.userId] to it[Bids.amount] }
        }

        if (bidsToday.size < 2) {
            println("Недостаточно ставок для обмена: ${bidsToday.size}")
            return
        }

        for (i in bidsToday.indices) {
            val (userId1, amount1) = bidsToday[i]
            val (userId2, amount2) = bidsToday[(i + 1) % bidsToday.size]
            println("User  $userId1 обменялся с User $userId2: $amount1 на $amount2")

            updateBids(userId1, amount2, amount1)
            updateUserBalance(userId1, amount2)
            updateBids(userId2, amount1, amount2)
            updateUserBalance(userId2, amount1)
        }
    }

    override suspend fun getLastActiveBidIdToday(userId: Int): Int {
        return dbQuery {
            val lastActiveBid = Bids.selectAll()
                .where {
                    (Bids.status eq BidStatus.ACTIVE.name) and
                            (Bids.userId eq userId)
                }
                .orderBy(Bids.createdAt, SortOrder.DESC)
                .map { it[Bids.id] }
                .singleOrNull()

            lastActiveBid ?: -2
        }
    }

    override suspend fun updateUserBalance(userId: Int, amount: Int) {
        dbQuery {
            Users.update({ Users.id.eq(userId) }) {
                val currentBalance = Users.selectAll().where { id.eq(userId) }
                    .map { it[balance] }
                    .singleOrNull() ?: throw IllegalArgumentException("User not found")

                it[balance] = currentBalance + amount/2
            }
        }
    }

    private suspend fun updateBids(userId: Int, newAmount: Int, original: Int) {
        dbQuery {
            Bids.update({ Bids.userId eq userId and (Bids.status eq BidStatus.ACTIVE.name) }) {
                it[amount] = original
                it[status] = BidStatus.COMPLETED.name
                it[profit] = newAmount - original
            }
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}