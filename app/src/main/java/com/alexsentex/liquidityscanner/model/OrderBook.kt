package com.alexsentex.liquidityscanner.model

data class OrderBookResponse(
    val lastUpdateId: Long,
    val bids: List<List<String>>,
    val asks: List<List<String>>
)

data class Order(
    val price: Double,
    val quantity: Double
) {
    val totalUsdt: Double
        get() = price * quantity
}

data class OrderBookState(
    val currentPrice: Double? = null,
    val bids: List<Order> = emptyList(),
    val asks: List<Order> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val lastUpdateTime: String? = null
)
