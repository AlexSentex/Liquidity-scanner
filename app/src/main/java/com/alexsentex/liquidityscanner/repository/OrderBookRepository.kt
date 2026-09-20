package com.alexsentex.liquidityscanner.repository

import com.alexsentex.liquidityscanner.analysis.LiquidityAnalyzer
import com.alexsentex.liquidityscanner.model.LiquidityZoneType
import com.alexsentex.liquidityscanner.model.Order
import com.alexsentex.liquidityscanner.model.OrderBookState
import com.alexsentex.liquidityscanner.network.BinanceClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class OrderBookRepository {

    private val api = BinanceClient.api

    suspend fun getOrderBook(): OrderBookState {

        val response = api.getOrderBook(limit = 100)

        val bids: List<Order> = response.bids.mapNotNull { item ->

            if (item.size < 2) {
                return@mapNotNull null
            }

            val price = item[0].toDoubleOrNull()
                ?: return@mapNotNull null

            val quantity = item[1].toDoubleOrNull()
                ?: return@mapNotNull null

            Order(
                price = price,
                quantity = quantity
            )
        }

        val asks: List<Order> = response.asks.mapNotNull { item ->

            if (item.size < 2) {
                return@mapNotNull null
            }

            val price = item[0].toDoubleOrNull()
                ?: return@mapNotNull null

            val quantity = item[1].toDoubleOrNull()
                ?: return@mapNotNull null

            Order(
                price = price,
                quantity = quantity
            )
        }

        val currentPrice = when {
            bids.isNotEmpty() && asks.isNotEmpty() ->
                (bids.first().price + asks.first().price) / 2.0

            bids.isNotEmpty() ->
                bids.first().price

            asks.isNotEmpty() ->
                asks.first().price

            else ->
                null
        }

        val supportZones =
            currentPrice?.let { price ->
                LiquidityAnalyzer.analyze(
                    orders = bids,
                    currentPrice = price,
                    type = LiquidityZoneType.SUPPORT
                )
            } ?: emptyList()

        val resistanceZones =
            currentPrice?.let { price ->
                LiquidityAnalyzer.analyze(
                    orders = asks,
                    currentPrice = price,
                    type = LiquidityZoneType.RESISTANCE
                )
            } ?: emptyList()

        val time = SimpleDateFormat(
            "HH:mm:ss",
            Locale.getDefault()
        ).format(Date())

        return OrderBookState(
            currentPrice = currentPrice,
            bids = bids,
            asks = asks,
            supportZones = supportZones,
            resistanceZones = resistanceZones,
            isLoading = false,
            error = null,
            lastUpdateTime = time
        )
    }
}
