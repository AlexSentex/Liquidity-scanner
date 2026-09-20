package com.alexsentex.liquidityscanner.network

import com.alexsentex.liquidityscanner.model.ExchangeOrderBookSnapshot
import com.alexsentex.liquidityscanner.model.ExchangeTrade
import com.alexsentex.liquidityscanner.model.NormalizedOrder
import com.alexsentex.liquidityscanner.model.TradeSide
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private data class BybitTopicPeek(val topic: String?)

private data class BybitOrderBookMessage(
    val topic: String?,
    val type: String?,
    val data: BybitOrderBookData?
)

private data class BybitOrderBookData(
    val b: List<List<String>>?,
    val a: List<List<String>>?
)

private data class BybitTradeMessage(
    val topic: String?,
    val data: List<BybitTradeItem>?
)

private data class BybitTradeItem(
    val S: String?,
    val p: String?,
    val v: String?,
    val T: Long?
)

class BybitDataSource : ExchangeDataSource {

    override val name = "Bybit"

    private val client = OkHttpClient.Builder().build()
    private val gson = Gson()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bids = sortedMapOf<Double, Double>(compareByDescending { it })
    private val asks = sortedMapOf<Double, Double>()

    private var socket: WebSocket? = null

    private var onUpdate: ((ExchangeOrderBookSnapshot) -> Unit)? = null
    private var onTrade: ((ExchangeTrade) -> Unit)? = null
    private var onStatus: ((Boolean, String?) -> Unit)? = null

    override fun start(
        symbol: String,
        onUpdate: (ExchangeOrderBookSnapshot) -> Unit,
        onTrade: (ExchangeTrade) -> Unit,
        onStatus: (Boolean, String?) -> Unit
    ) {
        this.onUpdate = onUpdate
        this.onTrade = onTrade
        this.onStatus = onStatus
        connectSocket(symbol)
    }

    override fun stop() {
        scope.coroutineContext.cancelChildren()
        socket?.close(1000, "Stopping")
        socket = null
    }

    private fun connectSocket(symbol: String) {
        socket?.close(1000, "Reconnect")

        val request = Request.Builder()
            .url("wss://stream.bybit.com/v5/public/spot")
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(
                    """{"op":"subscribe","args":["orderbook.50.$symbol","publicTrade.$symbol"]}"""
                )
                onStatus?.invoke(true, null)
                startHeartbeat(webSocket)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                onStatus?.invoke(false, null)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                onStatus?.invoke(false, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                onStatus?.invoke(false, t.message ?: "Bybit WebSocket error")
            }
        })
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        scope.launch {
            while (true) {
                delay(20_000)
                webSocket.send("""{"op":"ping"}""")
            }
        }
    }

    private fun handleMessage(text: String) {
        try {
            val topic = gson.fromJson(text, BybitTopicPeek::class.java).topic ?: return

            when {
                topic.startsWith("orderbook.") -> handleOrderBook(text)
                topic.startsWith("publicTrade.") -> handleTrade(text)
            }

        } catch (e: Exception) {
            onStatus?.invoke(false, e.message ?: "Помилка обробки Bybit WebSocket")
        }
    }

    private fun handleOrderBook(text: String) {
        val message = gson.fromJson(text, BybitOrderBookMessage::class.java)
        val data = message.data ?: return

        synchronized(this) {
            if (message.type == "snapshot") {
                bids.clear()
                asks.clear()
            }
            data.b?.let { applyLevels(it, bids) }
            data.a?.let { applyLevels(it, asks) }
        }

        emitSnapshot()
    }

    private fun handleTrade(text: String) {
        val message = gson.fromJson(text, BybitTradeMessage::class.java)

        message.data?.forEach { item ->
            val price = item.p?.toDoubleOrNull() ?: return@forEach
            val quantity = item.v?.toDoubleOrNull() ?: return@forEach

            onTrade?.invoke(
                ExchangeTrade(
                    exchangeName = name,
                    price = price,
                    quantity = quantity,
                    side = if (item.S == "Buy") TradeSide.BUY else TradeSide.SELL,
                    timestampMillis = item.T ?: System.currentTimeMillis()
                )
            )
        }
    }

    private fun applyLevels(items: List<List<String>>, target: MutableMap<Double, Double>) {
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
