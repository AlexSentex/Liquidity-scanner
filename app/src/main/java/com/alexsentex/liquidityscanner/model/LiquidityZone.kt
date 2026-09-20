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

    val distancePercent: Double,

    val firstSeenMillis: Long,
    val lastSeenMillis: Long,

    val minQuantity: Double,
    val maxQuantity: Double,

    val observationCount: Int,

    val stabilityPercent: Double
) {

    val lifetimeMillis: Long
        get() = lastSeenMillis - firstSeenMillis

    val lifetimeMinutes: Double
        get() = lifetimeMillis / 60_000.0
}
