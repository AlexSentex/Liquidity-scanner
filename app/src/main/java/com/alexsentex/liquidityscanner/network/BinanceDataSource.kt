package com.alexsentex.liquidityscanner.network

import com.alexsentex.liquidityscanner.model.ExchangeOrderBookSnapshot
import com.alexsentex.liquidityscanner.model.NormalizedOrder
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class BinanceDataSource : ExchangeDataSource {

    override val name = "Binance"

    private val api = BinanceClient.api
    private val client = BinanceClient.webSocketClient
    private val gson = Gson()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bids = sortedMapOf<Double, Double>(compareByDescending { it })
    private val asks = sortedMapOf<Double, Double>()

    private var lastUpdateId = 0L
    private var initialized = false
    private var socket: WebSocket? = null

    private var onUpdate: ((ExchangeOrderBookSnapshot) -> Unit)? = null
    private var onStatus: ((Boolean, String?) -> Unit)? = null

    override fun start(
        symbol: String,
        onUpdate: (ExchangeOrderBookSnapshot) -> Unit,
        onStatus: (Boolean, String?) -> Unit
    ) {
        this.onUpdate = onUpdate
        this.onStatus = onStatus

        scope.launch {
            try {
                loadSnapshot(symbol)
                connectSocket(symbol)
            } catch (e: Exception) {
                onStatus(false, e.message ?: "Помилка підключення до Binance")
            }
        }
    }

    override fun stop() {
        scope.coroutineContext.cancelChildren()
        socket?.close(1000, "Stopping")
        socket = null
        initialized = false
    }

    private suspend fun loadSnapshot(symbol: String) {
        val response = api.getOrderBook(symbol = symbol, limit = 1000)

        synchronized(this) {
            bids.clear()
            asks.clear()
            applyLevels(response.bids, bids)
            applyLevels(response.asks, asks)
            lastUpdateId = response.lastUpdateId
            initialized = true
        }

        emitSnapshot()
    }

    private fun connectSocket(symbol: String) {
        socket?.close(1000, "Reconnect")

        val stream = symbol.lowercase() + "@depth@100ms"
        val request = Request.Builder()
            .url("wss://stream.binance.com:9443/ws/$stream")
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                onStatus?.invoke(true, null)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleUpdate(text, symbol)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                onStatus?.invoke(false, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onStatus?.invoke(false, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onStatus?.invoke(false, t.message ?: "Binance WebSocket error")
            }
        })
    }

    private fun handleUpdate(text: String, symbol: String) {
        try {
            val update = gson.fromJson(text, BinanceDepthUpdate::class.java)

            synchronized(this) {
                if (!initialized) return

                if (update.finalUpdateId <= lastUpdateId) return

                if (update.firstUpdateId > lastUpdateId + 1) {
                    initialized = false
                    onStatus?.invoke(false, "Binance: виявлено пропуск даних, ресинхронізація...")
                    scope.launch { resync(symbol) }
                    return
                }

                applyDelta(update.bids, bids)
                applyDelta(update.asks, asks)

                lastUpdateId = update.finalUpdateId
            }

            emitSnapshot()

        } catch (e: Exception) {
            onStatus?.invoke(false, e.message ?: "Помилка обробки Binance WebSocket")
        }
    }

    private suspend fun resync(symbol: String) {
        try {
            delay(1000)
            loadSnapshot(symbol)
            connectSocket(symbol)
        } catch (e: Exception) {
            onStatus?.invoke(false, e.message ?: "Не вдалося ресинхронізувати Binance")
        }
    }

    private fun applyLevels(items: List<List<String>>, target: MutableMap<Double, Double>) {
        items.forEach { item ->
            if (item.size < 2) return@forEach
            val price = item[0].toDoubleOrNull() ?: return@forEach
            val quantity = item[1].toDoubleOrNull() ?: return@forEach
            if (quantity > 0.0) target[price] = quantity
        }
    }

    private fun applyDelta(items: List<List<String>>, target: MutableMap<Double, Double>) {
        items.forEach { item ->
            if (item.size < 2) return@forEach
            val price = item[0].toDoubleOrNull() ?: return@forEach
            val quantity = item[1].toDoubleOrNull() ?: return@forEach
            if (quantity == 0.0) target.remove(price) else target[price] = quantity
        }
    }

    private fun emitSnapshot() {
        val (bidList, askList) = synchronized(this) {
            bids.map { NormalizedOrder(it.key, it.value) } to
                asks.map { NormalizedOrder(it.key, it.value) }
        }

        onUpdate?.invoke(
            ExchangeOrderBookSnapshot(
                exchangeName = name,
                bids = bidList,
                asks = askList,
                timestampMillis = System.currentTimeMillis()
            )
        )
    }
}
