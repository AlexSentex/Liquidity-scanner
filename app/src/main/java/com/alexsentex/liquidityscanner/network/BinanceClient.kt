package com.alexsentex.liquidityscanner.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

data class BinanceOrderBookResponse(
    val lastUpdateId: Long,
    val bids: List<List<String>>,
    val asks: List<List<String>>
)

data class BinanceDepthUpdate(
    val e: String?,
    val E: Long?,
    val s: String?,
    val U: Long,
    val u: Long,
    val b: List<List<String>>,
    val a: List<List<String>>
)

interface BinanceApi {

    @GET("api/v3/depth")
    suspend fun getOrderBook(
        @Query("symbol") symbol: String = "BTCUSDT",
        @Query("limit") limit: Int = 1000
    ): BinanceOrderBookResponse
}

object BinanceClient {

    private val httpClient = OkHttpClient.Builder()
        .build()

    val api: BinanceApi by lazy {

        Retrofit.Builder()
            .baseUrl("https://api.binance.com/")
            .client(httpClient)
            .addConverterFactory(
                GsonConverterFactory.create()
            )
            .build()
            .create(BinanceApi::class.java)
    }

    val webSocketClient: OkHttpClient
        get() = httpClient
}
