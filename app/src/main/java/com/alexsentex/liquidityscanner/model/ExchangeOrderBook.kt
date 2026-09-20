package com.alexsentex.liquidityscanner.model

data class NormalizedOrder(
    val price: Double,
    val quantity: Double
)

data class ExchangeOrderBookSnapshot(
    val exchangeName: String,
    val bids: List<NormalizedOrder>,
    val asks: List<NormalizedOrder>,
    val timestampMillis: Long
)
