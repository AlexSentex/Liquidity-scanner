package com.alexsentex.liquidityscanner.network

import com.alexsentex.liquidityscanner.model.ExchangeOrderBookSnapshot
import com.alexsentex.liquidityscanner.model.ExchangeTrade

interface ExchangeDataSource {

    val name: String

    fun start(
        symbol: String,
        onUpdate: (ExchangeOrderBookSnapshot) -> Unit,
        onTrade: (ExchangeTrade) -> Unit,
        onStatus: (connected: Boolean, error: String?) -> Unit
    )

    fun stop()
}
