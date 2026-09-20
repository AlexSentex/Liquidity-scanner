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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

private data class OkxWsMessage(
    val arg: OkxArg?,
    val action: String?,
    val data: List<OkxBookData>?
)

private data class OkxArg(val channel: String?, val instId: String?)

private data class OkxBookData(
    val asks: List<List<String>>?,
    val bids: List<List<String>>?
)

class OkxDataSource : ExchangeDataSource {

    override val name = "OKX"

    private val client = OkHttpClient.Builder().build()
    private val gson = Gson()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val bids = sortedMapOf<Double, Double>(compareByDescending { it })
    private val asks = sortedMapOf<Double, Double>()

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
        connectSocket(toOkxInstId(symbol))
    }

    override fun stop() {
        scope.coroutineContext.cancelChildren()
        socket?.close(1000, "Stopping")
        socket = null
    }

    private fun toOkxInstId(symbol: String): String {
        if (symbol.contains("-")) return symbol
        val quote = listOf("USDT", "USDC", "BTC", "ETH")
            .firstOrNull { symbol.endsWith(it) && symbol.length > it.length } ?: "USDT"
        val base = symbol.removeSuffix(quote)
        return "$base-$quote"
    }

    private fun connectSocket(instId: String) {
        socket?.close(1000, "Reconnect")

        val request = Request.Builder()
            .url("wss://ws.okx.com:8443/ws/v5/public")
            .build()

        socket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(
                    """{"op":"subscribe","args":[{"channel":"books","instId":"$instId"}]}"""
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
                onStatus?.invoke(false, t.message ?: "OKX WebSocket error")
            }
        })
    }

    private fun startHeartbeat(webSocket: WebSocket) {
        scope.launch {
            while (true) {
                delay(20_000)
                webSocket.send("ping")
            }
        }
    }

    private fun handleMessage(text: String) {
        if (text == "pong") return

        try {
            val message = gson.fromJson(text, OkxWsMessage::class.java)
            if (message.arg?.channel != "books") return

            val book = message.data?.firstOrNull() ?: return

            synchronized(this) {
                if (message.action == "snapshot") {
                    bids.clear()
                    asks.clear()
                }
                book.bids?.let { applyLevels(it, bids) }
                book.asks?.let { applyLevels(it, asks) }
            }

            emitSnapshot()

        } catch (e: Exception) {
            onStatus?.invoke(false, e.message ?: "Помилка обробки OKX WebSocket")
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
