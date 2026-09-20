package com.alexsentex.liquidityscanner.analysis

import com.alexsentex.liquidityscanner.model.ExchangeTrade

class TradeBuffer(
    private val windowMillis: Long = 15_000
) {

    private val trades = ArrayDeque<ExchangeTrade>()

    @Synchronized
    fun record(trade: ExchangeTrade) {
        trades.addLast(trade)
        prune(trade.timestampMillis)
    }

    @Synchronized
    fun volumeInRange(lowerPrice: Double, upperPrice: Double, nowMillis: Long): Double {
        prune(nowMillis)
        return trades
            .filter { it.price in lowerPrice..upperPrice }
            .sumOf { it.quantity }
    }

    private fun prune(nowMillis: Long) {
        while (trades.isNotEmpty() && nowMillis - trades.first().timestampMillis > windowMillis) {
            trades.removeFirst()
        }
    }
}
