package com.alexsentex.liquidityscanner.repository

import com.alexsentex.liquidityscanner.analysis.LiquidityAnalyzer
import com.alexsentex.liquidityscanner.model.ExchangeOrderBookSnapshot
import com.alexsentex.liquidityscanner.model.LiquidityZone
import com.alexsentex.liquidityscanner.model.LiquidityZoneType
import com.alexsentex.liquidityscanner.model.Order
import com.alexsentex.liquidityscanner.model.OrderBookState
import com.alexsentex.liquidityscanner.network.BinanceDataSource
import com.alexsentex.liquidityscanner.network.BybitDataSource
import com.alexsentex.liquidityscanner.network.ExchangeDataSource
import com.alexsentex.liquidityscanner.network.OkxDataSource
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MultiExchangeOrderBookRepository(
    private val sources: List<ExchangeDataSource> = listOf(
        BinanceDataSource(),
        BybitDataSource(),
        OkxDataSource()
    )
) {

    private val latestSnapshots = mutableMapOf<String, ExchangeOrderBookSnapshot>()
    private val connectionStatus = mutableMapOf<String, Boolean>()

    private var currentZoneSize = 500.0

    private var previousSupportZones = emptyList<LiquidityZone>()
    private var previousResistanceZones = emptyList<LiquidityZone>()

    private var currentState = OrderBookState(zoneSize = currentZoneSize)
    private var onStateChanged: ((OrderBookState) -> Unit)? = null

    fun start(
        zoneSize: Double,
        symbol: String = "BTCUSDT",
        callback: (OrderBookState) -> Unit
    ) {
        currentZoneSize = zoneSize
        onStateChanged = callback

        sources.forEach { source ->
            source.start(
                symbol = symbol,
                onUpdate = { snapshot ->
                    synchronized(this) { latestSnapshots[source.name] = snapshot }
                    recalculate()
                },
                onStatus = { connected, error ->
                    synchronized(this) { connectionStatus[source.name] = connected }
                    updateState(
                        currentState.copy(
                            isConnected = connectionStatus.values.any { it },
                            error = error ?: currentState.error
                        )
                    )
                }
            )
        }
    }

    fun setZoneSize(zoneSize: Double) {
        currentZoneSize = zoneSize
        recalculate()
    }

    fun stop() {
        sources.forEach { it.stop() }
    }

    private fun recalculate() {

        val now = System.currentTimeMillis()

        val snapshots = synchronized(this) { latestSnapshots.values.toList() }
        if (snapshots.isEmpty()) return

        val bestBid = snapshots.mapNotNull { it.bids.firstOrNull()?.price }.maxOrNull()
        val bestAsk = snapshots.mapNotNull { it.asks.firstOrNull()?.price }.minOrNull()

        if (bestBid == null && bestAsk == null) return

        val currentPrice = when {
            bestBid != null && bestAsk != null -> (bestBid + bestAsk) / 2.0
            bestBid != null -> bestBid
            else -> bestAsk!!
        }

        val bidOrders = snapshots.flatMap { snapshot ->
            snapshot.bids.map { Order(it.price, it.quantity, snapshot.exchangeName) }
        }

        val askOrders = snapshots.flatMap { snapshot ->
            snapshot.asks.map { Order(it.price, it.quantity, snapshot.exchangeName) }
        }

        val support = LiquidityAnalyzer.analyze(
            orders = bidOrders,
            currentPrice = currentPrice,
            type = LiquidityZoneType.SUPPORT,
            zoneSize = currentZoneSize,
            previousZones = previousSupportZones,
            nowMillis = now
        )

        val resistance = LiquidityAnalyzer.analyze(
            orders = askOrders,
            currentPrice = currentPrice,
            type = LiquidityZoneType.RESISTANCE,
            zoneSize = currentZoneSize,
            previousZones = previousResistanceZones,
            nowMillis = now
        )

        previousSupportZones = support
        previousResistanceZones = resistance

        val date = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(now))

        updateState(
            OrderBookState(
                currentPrice = currentPrice,
                supportZones = support.sortedBy { it.distancePercent },
                resistanceZones = resistance.sortedBy { it.distancePercent },
                zoneSize = currentZoneSize,
                isConnected = connectionStatus.values.any { it },
                isLoading = false,
                error = null,
                lastUpdateTime = date
            )
        )
    }

    private fun updateState(state: OrderBookState) {
        currentState = state
        onStateChanged?.invoke(state)
    }
}
