package com.roxx.repository

interface AuctionService {
    suspend fun create(user: UserRequestLogin): Int
    suspend fun read(id: Int): UserRespond?
    suspend fun authenticate(username: String, password: String): UserRespond?
    suspend fun getTopUserByBalance(limit: Int): List<UsersRespond>
    suspend fun makeBid(userId: Int, amount: Int): Pair<Int, Int>
    suspend fun deleteBid(id: Int): Int
    suspend fun getBidById(bidId: Int): Bid?
    suspend fun getBidsByUserId(userId: Int): List<Bid>
    suspend fun clearAllData()
    suspend fun randomExchange()

    suspend fun updateUserBalance(userId: Int, amount: Int)

    suspend fun getLastActiveBidIdToday(userId: Int): Int

    suspend fun searchUser(username: String): UserRespond?

    suspend fun getDailyItem(): DailyItemDto
}