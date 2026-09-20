package com.alexsentex.liquidityscanner.network

import com.alexsentex.liquidityscanner.model.ExchangeOrderBookSnapshot

interface ExchangeDataSource {

    val name: String

    fun start(
        symbol: String,
        onUpdate: (ExchangeOrderBookSnapshot) -> Unit,
        onStatus: (connected: Boolean, error: String?) -> Unit
    )

    fun stop()
}
