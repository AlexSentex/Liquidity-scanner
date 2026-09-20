package com.alexsentex.liquidityscanner.model

data class OrderBookResponse(
    val lastUpdateId: Long,
    val bids: List<List<String>>,
    val asks: List<List<String>>
)

data class Order(
    val price: Double,
    val quantity: Double,
    val exchange: String = ""
) {

    val totalUsdt: Double
        get() = price * quantity
}

data class OrderBookState(
    val currentPrice: Double = 0.0,
    val supportZones: List<LiquidityZone> = emptyList(),
    val resistanceZones: List<LiquidityZone> = emptyList(),
    val zoneSize: Double = 500.0,
    val isConnected: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
    val lastUpdateTime: String = "",
    val recentEvents: List<AbsorptionEvent> = emptyList()
)
