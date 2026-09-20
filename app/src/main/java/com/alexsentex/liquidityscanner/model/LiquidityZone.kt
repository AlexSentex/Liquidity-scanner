package com.alexsentex.liquidityscanner.model

enum class LiquidityZoneType {
    SUPPORT,
    RESISTANCE
}

data class LiquidityZone(
    val type: LiquidityZoneType,
    val lowerPrice: Double,
    val upperPrice: Double,
    val centerPrice: Double,
    val totalQuantity: Double,
    val totalUsdt: Double,
    val levelCount: Int,
    val strength: Double,
    val distancePercent: Double
)
