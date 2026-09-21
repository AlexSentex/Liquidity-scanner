package com.alexsentex.liquidityscanner.analysis

import com.alexsentex.liquidityscanner.model.LiquidityZone
import com.alexsentex.liquidityscanner.model.LiquidityZoneType
import com.alexsentex.liquidityscanner.model.Order
import kotlin.math.abs
import kotlin.math.floor

object LiquidityAnalyzer {

    private const val MIN_ZONE_QUANTITY_BTC = 0.25

    fun analyze(
        orders: List<Order>,
        currentPrice: Double,
        type: LiquidityZoneType,
        zoneSize: Double,
        previousZones: List<LiquidityZone>,
        nowMillis: Long
    ): List<LiquidityZone> {

        if (
            orders.isEmpty() ||
            currentPrice <= 0.0 ||
            zoneSize <= 0.0
        ) {
            return emptyList()
        }

        val filtered = orders.filter { order ->
            when (type) {
                LiquidityZoneType.SUPPORT -> order.price < currentPrice
                LiquidityZoneType.RESISTANCE -> order.price > currentPrice
            }
        }

        if (filtered.isEmpty()) {
            return emptyList()
        }

        // Прив'язуємо зони до фіксованої цінової сітки (кратні zoneSize),
        // а не до відстані від поточної ціни — так межі зон не "їдуть"
        // при кожному незначному коливанні ціни.
        val groups = filtered.groupBy { order ->
            floor(order.price / zoneSize).toLong()
        }

        val rawZones = groups.mapNotNull { (gridIndex, group) ->

            if (group.isEmpty()) {
                return@mapNotNull null
            }

            val totalQuantity = group.sumOf { it.quantity }

            // Ігноруємо "пустишні" зони нижче мінімального обсягу.
            if (totalQuantity < MIN_ZONE_QUANTITY_BTC) {
                return@mapNotNull null
            }

            val totalUsdt = group.sumOf { it.totalUsdt }

            val gridLower = gridIndex * zoneSize
            val gridUpper = gridLower + zoneSize

            // Зону, в якій зараз знаходиться ціна, ділимо навпіл
            // самою ціною: support бачить лише нижню половину,
            // resistance — лише верхню.
            val lowerPrice =
                if (type == LiquidityZoneType.RESISTANCE)
                    maxOf(gridLower, currentPrice)
                else
                    gridLower

            val upperPrice =
                if (type == LiquidityZoneType.SUPPORT)
                    minOf(gridUpper, currentPrice)
                else
                    gridUpper

            val centerPrice =
                group.sumOf { it.price * it.quantity } / totalQuantity

            val distancePercent =
                abs(centerPrice - currentPrice) / currentPrice * 100.0

            val exchangesInGroup =
                group
                    .map { it.exchange }
                    .filter { it.isNotBlank() }
                    .distinct()

            LiquidityZone(
                type = type,
                gridIndex = gridIndex,

                lowerPrice = lowerPrice,
                upperPrice = upperPrice,
                centerPrice = centerPrice,

                totalQuantity = totalQuantity,
                totalUsdt = totalUsdt,

                levelCount = group.size,

                strength = totalQuantity,

                distancePercent = distancePercent,

                firstSeenMillis = nowMillis,
                lastSeenMillis = nowMillis,

                minQuantity = totalQuantity,
                maxQuantity = totalQuantity,

                observationCount = 1,

                stabilityPercent = 100.0,
                exchanges = exchangesInGroup
            )
        }

        if (rawZones.isEmpty()) {
            return emptyList()
        }

        val averageQuantity =
            rawZones.map { it.totalQuantity }.average()

        return rawZones.map { zone ->

            // Зіставляємо з попередньою зоною за тим самим gridIndex —
            // це надійніше за пошук "найближчої ціни", бо сітка стабільна.
            val oldZone =
                previousZones.find { it.gridIndex == zone.gridIndex }

            if (oldZone == null) {

                val strength =
                    if (averageQuantity > 0.0) zone.totalQuantity / averageQuantity else 1.0

                zone.copy(strength = strength)

            } else {

                val quantityDifference =
                    abs(zone.totalQuantity - oldZone.totalQuantity)

                val reference =
                    maxOf(oldZone.totalQuantity, zone.totalQuantity, 0.000001)

                val stability =
                    (1.0 - quantityDifference / reference).coerceIn(0.0, 1.0) * 100.0

                val strength =
                    if (averageQuantity > 0.0) zone.totalQuantity / averageQuantity else 1.0

                zone.copy(
                    strength = strength,
                    firstSeenMillis = oldZone.firstSeenMillis,
                    lastSeenMillis = nowMillis,
                    minQuantity = minOf(oldZone.minQuantity, zone.totalQuantity),
                    maxQuantity = maxOf(oldZone.maxQuantity, zone.totalQuantity),
                    observationCount = oldZone.observationCount + 1,
                    stabilityPercent =
                        (oldZone.stabilityPercent * oldZone.observationCount + stability) /
                            (oldZone.observationCount + 1)
                )
            }
        }
    }
}
