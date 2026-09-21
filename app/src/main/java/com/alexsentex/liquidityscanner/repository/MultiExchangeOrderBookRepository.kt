package com.alexsentex.liquidityscanner.repository

import com.alexsentex.liquidityscanner.analysis.LiquidityAnalyzer
import com.alexsentex.liquidityscanner.analysis.TradeBuffer
import com.alexsentex.liquidityscanner.model.AbsorptionEvent
import com.alexsentex.liquidityscanner.model.AbsorptionOutcome
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
    private val tradeBuffer = TradeBuffer()

    private var currentZoneSize = 500.0

    private var previousSupportZones = emptyList<LiquidityZone>()
    private var previousResistanceZones = emptyList<LiquidityZone>()

    private val recentEvents = ArrayDeque<AbsorptionEvent>()

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
                onTrade = { trade ->
                    tradeBuffer.record(trade)
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

    private var lastEmitMillis = 0L
    private val minEmitIntervalMillis = 700L

    private fun recalculate() {

        val now = System.currentTimeMillis()

        // Не перераховуємо й не оновлюємо UI частіше, ніж раз на 700мс —
        // саме часті апдейти й були головною причиною "дригання" екрану.
        if (now - lastEmitMillis < minEmitIntervalMillis) {
            return
        }

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

        detectDisappearedZones(previousSupportZones, support, now)
        detectDisappearedZones(previousResistanceZones, resistance, now)

        previousSupportZones = support
        previousResistanceZones = resistance

        lastEmitMillis = now

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
                lastUpdateTime = date,
                recentEvents = recentEvents.toList()
            )
        )
    }

    // Порівнюємо попередні й нові зони: якщо зона з попереднього
    // циклу зникла з нового списку — дивимось, чи на цьому ціновому
    // рівні щойно пройшли реальні угоди (поглинання), чи ні (зняття).
    private fun detectDisappearedZones(
        oldZones: List<LiquidityZone>,
        newZones: List<LiquidityZone>,
        now: Long
        ) {
        val disappeared = oldZones.filter { old ->
            newZones.none { it.gridIndex == old.gridIndex }
        }

        disappeared.forEach { zone ->
            val traded = tradeBuffer.volumeInRange(zone.lowerPrice, zone.upperPrice, now)

            val outcome =
                if (traded >= zone.totalQuantity * 0.5)
                    AbsorptionOutcome.ABSORBED
                else
                    AbsorptionOutcome.PULLED

            recentEvents.addFirst(
                AbsorptionEvent(
                    type = zone.type,
                    lowerPrice = zone.lowerPrice,
                    upperPrice = zone.upperPrice,
                    originalQuantity = zone.totalQuantity,
                    tradedQuantity = traded,
                    outcome = outcome,
                    timestampMillis = now
                )
            )

            while (recentEvents.size > 20) {
                recentEvents.removeLast()
            }
        }
    }
        
    private fun updateState(state: OrderBookState) {
        currentState = state
        onStateChanged?.invoke(state)
    }
}
