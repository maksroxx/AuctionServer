package com.roxx.repository

import at.favre.lib.crypto.bcrypt.BCrypt
import com.google.gson.annotations.SerializedName
import com.roxx.database.Bids
import com.roxx.database.Users
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.request.*
import io.ktor.serialization.gson.*
import io.ktor.server.application.*
import kotlinx.coroutines.Dispatchers
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.client.plugins.contentnegotiation.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.*

data class UserRequestLogin(val username: String, val password: String)
data class UserRespond(val id: Int, val username: String, val balance: Int)
data class UsersRespond(val username: String, val balance: Int)

data class SearchRequest(val username: String)
data class BidRequest(val amount: Int)
data class Bid(val id: Int, val userId: Int, val amount: Int, val createdAt: Int, val status: String, val profit: Int, val title: String)
@Serializable
data class DailyItemResponse(
    @SerializedName("title")
    val title: String,

    @SerializedName("imageUrl")
    val imageUrl: String
)

data class DailyItemDto(val title: String, val imageUrl: String)

enum class BidStatus {
    ACTIVE,
    COMPLETED
}

class AuctionServiceImpl : AuctionService {
    private val client = HttpClient(CIO) {
        install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
            gson()
        }
    }

    override suspend fun create(user: UserRequestLogin): Int = dbQuery {
        val existingUser = Users.selectAll().where { Users.username eq user.username }.singleOrNull()
        if (existingUser != null) {
            return@dbQuery -1
        }
        val hashedPassword = BCrypt.withDefaults().hashToString(12, user.password.toCharArray())
        Users.insert {
            it[username] = user.username
            it[password] = hashedPassword
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

    override suspend fun authenticate(username: String, password: String): UserRespond? = dbQuery {
        val userRow = Users.selectAll().where { Users.username eq username }.singleOrNull() ?: return@dbQuery null

        val storedHash = userRow[Users.password]
        val result = BCrypt.verifyer().verify(password.toCharArray(), storedHash)

        if (result.verified) {
            UserRespond(
                id = userRow[Users.id],
                username = userRow[Users.username],
                balance = userRow[Users.balance]
            )
        } else {
            null
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
            val dailyItem = getDailyItem()
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
                    it[itemTitle] = dailyItem.title
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
                        profit = row[Bids.profit],
                        title = row[Bids.itemTitle]
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
                    profit = row[Bids.profit],
                    title = row[Bids.itemTitle]
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
        val activeBids = dbQuery {
            Bids.selectAll()
                .where { Bids.status eq BidStatus.ACTIVE.name }
                .orderBy(Bids.amount, SortOrder.DESC)
                .map {
                    Bid(
                        id = it[Bids.id],
                        userId = it[Bids.userId],
                        amount = it[Bids.amount],
                        createdAt = it[Bids.createdAt],
                        status = it[Bids.status],
                        profit = it[Bids.profit],
                        title = it[Bids.itemTitle]
                    )
                }
        }

        if (activeBids.size < 1) {
            println("Недостаточно ставок для определения победителя: ${activeBids.size}")
            return
        }

        val winnerBid = activeBids.first() // самый высокий bid
        val loserBids = activeBids.drop(1)

        // Обновляем победителя
        dbQuery {
            Bids.update({ Bids.id eq winnerBid.id }) {
                it[status] = BidStatus.COMPLETED.name
                it[profit] = (winnerBid.amount * 0.5).toInt() // +50% прибыли
            }

            Users.update({ Users.id eq winnerBid.userId }) {
                val currentBalance = Users.selectAll().where { Users.id eq winnerBid.userId }
                    .map { it[Users.balance] }
                    .single()
                it[balance] = currentBalance + (winnerBid.amount * 1.5).toInt()
            }
        }

        // Обрабатываем проигравших
        for (loser in loserBids) {
            dbQuery {
                Bids.update({ Bids.id eq loser.id }) {
                    it[status] = BidStatus.COMPLETED.name
                    it[profit] = -loser.amount // потеря
                }

                Users.update({ Users.id eq loser.userId }) {
                    val currentBalance = Users.selectAll().where { Users.id eq loser.userId }
                        .map { it[Users.balance] }
                        .single()
                    it[balance] = currentBalance + loser.amount // возврат ставки
                }
            }
        }

        println("Аукцион завершён: победитель User ${winnerBid.userId}, ставка ${winnerBid.amount}")
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

    override suspend fun searchUser(username: String): UserRespond? = dbQuery {
        Users.selectAll().where { Users.username eq username }
            .map {
                UserRespond(
                    id = it[Users.id],
                    username = it[Users.username],
                    balance = it[Users.balance]
                )
            }
            .firstOrNull()
    }

    override suspend fun getDailyItem(): DailyItemDto {
        val response: DailyItemResponse = client.get("http://localhost:8844/daily").body()
        return DailyItemDto(title = response.title, imageUrl = response.imageUrl)
    }

    override suspend fun updateUserBalance(userId: Int, amount: Int) {
        dbQuery {
            Users.update({ Users.id.eq(userId) }) {
                val currentBalance = Users.selectAll().where { id.eq(userId) }
                    .map { it[balance] }
                    .singleOrNull() ?: throw IllegalArgumentException("User not found")

                it[balance] = currentBalance + amount / 2
            }
        }
    }

    private suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}