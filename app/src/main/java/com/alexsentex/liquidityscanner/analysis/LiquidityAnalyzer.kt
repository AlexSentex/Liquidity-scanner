package com.alexsentex.liquidityscanner.analysis

import com.alexsentex.liquidityscanner.model.LiquidityZone
import com.alexsentex.liquidityscanner.model.LiquidityZoneType
import com.alexsentex.liquidityscanner.model.Order
import kotlin.math.abs

object LiquidityAnalyzer {

    /*
     * Розмір однієї цінової зони у відсотках.
     *
     * Наприклад:
     * BTC = $100 000
     * ZONE_SIZE_PERCENT = 0.25
     *
     * Одна зона буде приблизно $250 шириною.
     */
    private const val ZONE_SIZE_PERCENT = 0.25

    /*
     * Мінімальна кількість рівнів у зоні.
     *
     * Якщо в зоні є хоча б один рівень,
     * вона все одно може бути врахована.
     */
    private const val MIN_LEVELS = 1

    /*
     * Кількість зон, які показуємо на екрані.
     */
    const val MAX_ZONES = 8

    fun analyze(
        orders: List<Order>,
        currentPrice: Double,
        type: LiquidityZoneType
    ): List<LiquidityZone> {

        if (orders.isEmpty() || currentPrice <= 0.0) {
            return emptyList()
        }

        val filteredOrders = orders.filter { order ->
            when (type) {
                LiquidityZoneType.SUPPORT ->
                    order.price < currentPrice

                LiquidityZoneType.RESISTANCE ->
                    order.price > currentPrice
            }
        }

        if (filteredOrders.isEmpty()) {
            return emptyList()
        }

        val zoneSize = currentPrice * ZONE_SIZE_PERCENT / 100.0

        if (zoneSize <= 0.0) {
            return emptyList()
        }

        data class ZoneAccumulator(
            val index: Int,
            val orders: MutableList<Order> = mutableListOf()
        )

        val zones = mutableMapOf<Int, ZoneAccumulator>()

        filteredOrders.forEach { order ->

            val distance = when (type) {
                LiquidityZoneType.SUPPORT ->
                    currentPrice - order.price

                LiquidityZoneType.RESISTANCE ->
                    order.price - currentPrice
            }

            val index = (distance / zoneSize).toInt()

            zones.getOrPut(index) {
                ZoneAccumulator(index)
            }.orders.add(order)
        }

        val rawZones = zones.values
            .filter { it.orders.size >= MIN_LEVELS }
            .mapNotNull { zone ->

                if (zone.orders.isEmpty()) {
                    return@mapNotNull null
                }

                val prices = zone.orders.map { it.price }

                val totalQuantity =
                    zone.orders.sumOf { it.quantity }

                val totalUsdt =
                    zone.orders.sumOf { it.totalUsdt }

                if (totalQuantity <= 0.0) {
                    return@mapNotNull null
                }

                val lowerPrice = prices.minOrNull() ?: return@mapNotNull null
                val upperPrice = prices.maxOrNull() ?: return@mapNotNull null

                val centerPrice =
                    zone.orders.sumOf { it.price * it.quantity } /
                            totalQuantity

                val distancePercent =
                    abs(centerPrice - currentPrice) /
                            currentPrice * 100.0

                LiquidityZone(
                    type = type,
                    lowerPrice = lowerPrice,
                    upperPrice = upperPrice,
                    centerPrice = centerPrice,
                    totalQuantity = totalQuantity,
                    totalUsdt = totalUsdt,
                    levelCount = zone.orders.size,
                    strength = totalQuantity,
                    distancePercent = distancePercent
                )
            }

        if (rawZones.isEmpty()) {
            return emptyList()
        }

        /*
         * Середній обсяг зони використовується як базовий рівень.
         *
         * Наприклад:
         *
         * середня зона = 1 BTC
         * конкретна зона = 5 BTC
         *
         * strength = 5x
         */
        val averageQuantity =
            rawZones.map { it.totalQuantity }.average()

        return rawZones
            .map { zone ->

                val relativeStrength =
                    if (averageQuantity > 0.0) {
                        zone.totalQuantity / averageQuantity
                    } else {
                        0.0
                    }

                zone.copy(
                    strength = relativeStrength
                )
            }
            .sortedByDescending { it.strength }
            .take(MAX_ZONES)
    }
}
