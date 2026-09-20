package com.alexsentex.liquidityscanner.analysis

import com.alexsentex.liquidityscanner.model.LiquidityZone
import com.alexsentex.liquidityscanner.model.LiquidityZoneType
import com.alexsentex.liquidityscanner.model.Order
import kotlin.math.abs

object LiquidityAnalyzer {

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

                LiquidityZoneType.SUPPORT ->
                    order.price < currentPrice

                LiquidityZoneType.RESISTANCE ->
                    order.price > currentPrice
            }
        }

        if (filtered.isEmpty()) {
            return emptyList()
        }

        val groups = filtered.groupBy { order ->

            val distance =
                when (type) {

                    LiquidityZoneType.SUPPORT ->
                        currentPrice - order.price

                    LiquidityZoneType.RESISTANCE ->
                        order.price - currentPrice
                }

            (distance / zoneSize).toInt()
        }

        val rawZones = groups.mapNotNull { (_, group) ->

            if (group.isEmpty()) {
                return@mapNotNull null
            }

            val totalQuantity =
                group.sumOf { it.quantity }

            if (totalQuantity <= 0.0) {
                return@mapNotNull null
            }

            val totalUsdt =
                group.sumOf { it.totalUsdt }

            val lowerPrice =
                group.minOf { it.price }

            val upperPrice =
                group.maxOf { it.price }

            val centerPrice =
                group.sumOf {
                    it.price * it.quantity
                } / totalQuantity

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

                levelCount = group.size,

                strength = totalQuantity,

                distancePercent = distancePercent,

                firstSeenMillis = nowMillis,
                lastSeenMillis = nowMillis,

                minQuantity = totalQuantity,
                maxQuantity = totalQuantity,

                observationCount = 1,

                stabilityPercent = 100.0
            )
        }

        if (rawZones.isEmpty()) {
            return emptyList()
        }

        val averageQuantity =
            rawZones
                .map { it.totalQuantity }
                .average()

        return rawZones.map { zone ->

            val oldZone =
                previousZones.minByOrNull { previous ->

                    abs(
                        previous.centerPrice -
                            zone.centerPrice
                    )
                }?.takeIf { previous ->

                    val maxDistance =
                        zoneSize * 0.75

                    abs(
                        previous.centerPrice -
                            zone.centerPrice
                    ) <= maxDistance
                }

            if (oldZone == null) {

                val strength =
                    if (averageQuantity > 0.0) {
                        zone.totalQuantity /
                            averageQuantity
                    } else {
                        1.0
                    }

                zone.copy(
                    strength = strength
                )

            } else {

                val quantityDifference =
                    abs(
                        zone.totalQuantity -
                            oldZone.totalQuantity
                    )

                val reference =
                    maxOf(
                        oldZone.totalQuantity,
                        zone.totalQuantity,
                        0.000001
                    )

                val stability =
                    (1.0 -
                        quantityDifference / reference
                    ).coerceIn(0.0, 1.0) * 100.0

                val strength =
                    if (averageQuantity > 0.0) {
                        zone.totalQuantity /
                            averageQuantity
                    } else {
                        1.0
                    }

                zone.copy(
                    strength = strength,

                    firstSeenMillis =
                        oldZone.firstSeenMillis,

                    lastSeenMillis =
                        nowMillis,

                    minQuantity =
                        minOf(
                            oldZone.minQuantity,
                            zone.totalQuantity
                        ),

                    maxQuantity =
                        maxOf(
                            oldZone.maxQuantity,
                            zone.totalQuantity
                        ),

                    observationCount =
                        oldZone.observationCount + 1,

                    stabilityPercent =
                        (
                            oldZone.stabilityPercent *
                                oldZone.observationCount +
                                stability
                        ) /
                        (oldZone.observationCount + 1)
                )
            }
        }
    }
}
