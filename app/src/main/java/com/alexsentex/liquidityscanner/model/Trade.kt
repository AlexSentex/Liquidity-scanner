package com.alexsentex.liquidityscanner.model

enum class TradeSide {
    BUY,
    SELL
}

data class ExchangeTrade(
    val exchangeName: String,
    val price: Double,
    val quantity: Double,
    val side: TradeSide,
    val timestampMillis: Long
)

enum class AbsorptionOutcome {
    ABSORBED,
    PULLED
}

data class AbsorptionEvent(
    val type: LiquidityZoneType,
    val lowerPrice: Double,
    val upperPrice: Double,
    val originalQuantity: Double,
    val tradedQuantity: Double,
    val outcome: AbsorptionOutcome,
    val timestampMillis: Long
)
