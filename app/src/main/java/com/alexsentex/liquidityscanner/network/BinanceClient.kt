package com.alexsentex.liquidityscanner.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class BinanceOrderBookResponse(
    val lastUpdateId: Long,
    val bids: List<List<String>>,
    val asks: List<List<String>>
)

interface BinanceApi {

    @GET("api/v3/depth")
    suspend fun getOrderBook(
        @Query("symbol") symbol: String = "BTCUSDT",
        @Query("limit") limit: Int = 100
    ): BinanceOrderBookResponse
}

object BinanceClient {

    val api: BinanceApi by lazy {

        Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(BinanceApi::class.java)
    }
}

