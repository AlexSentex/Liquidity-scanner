package com.alexsentex.liquidityscanner.repository

import com.alexsentex.liquidityscanner.analysis.LiquidityAnalyzer
import com.alexsentex.liquidityscanner.model.LiquidityZone
import com.alexsentex.liquidityscanner.model.LiquidityZoneType
import com.alexsentex.liquidityscanner.model.Order
import com.alexsentex.liquidityscanner.model.OrderBookState
import com.alexsentex.liquidityscanner.network.BinanceClient
import com.alexsentex.liquidityscanner.network.BinanceDepthUpdate
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class OrderBookRepository {

    private val api = BinanceClient.api
    private val client = BinanceClient.webSocketClient

    private val repositoryScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val gson = Gson()

    private val bids =
        sortedMapOf<Double, Double>(
            compareByDescending { it }
        )

    private val asks =
        sortedMapOf<Double, Double>()

    private var lastUpdateId: Long = 0L

    private var initialized = false

    private var socket: WebSocket? = null

    private var currentZoneSize = 500.0

    private var previousSupportZones =
        emptyList<LiquidityZone>()

    private var previousResistanceZones =
        emptyList<LiquidityZone>()

    private var currentState =
        OrderBookState(
            zoneSize = currentZoneSize
        )

    private var onStateChanged:
        ((OrderBookState) -> Unit)? = null

    suspend fun start(
        zoneSize: Double,
        callback: (OrderBookState) -> Unit
    ) {

        currentZoneSize = zoneSize
        onStateChanged = callback

        withContext(Dispatchers.IO) {

            try {

                loadInitialSnapshot()

                connectWebSocket()

            } catch (e: Exception) {

                updateState(
                    currentState.copy(
                        isLoading = false,
                        isConnected = false,
                        error =
                            e.message
                                ?: "Помилка підключення до Binance"
                    )
                )
            }
        }
    }

    fun setZoneSize(
        zoneSize: Double
    ) {

        currentZoneSize = zoneSize

        recalculateZones()
    }

    fun stop() {
        repositoryScope.coroutineContext.cancelChildren()

        socket?.close(
            1000,
            "Stopping"
        )
        
        socket = null
        initialized = false
        
        }

    private suspend fun loadInitialSnapshot() {

        val response =
            api.getOrderBook(
                symbol = "BTCUSDT",
                limit = 1000
            )

        synchronized(this) {

            bids.clear()
            asks.clear()

            response.bids.forEach { item ->

                if (item.size >= 2) {

                    val price =
                        item[0].toDoubleOrNull()

                    val quantity =
                        item[1].toDoubleOrNull()

                    if (
                        price != null &&
                        quantity != null &&
                        quantity > 0.0
                    ) {
                        bids[price] = quantity
                    }
                }
            }

            response.asks.forEach { item ->

                if (item.size >= 2) {

                    val price =
                        item[0].toDoubleOrNull()

                    val quantity =
                        item[1].toDoubleOrNull()

                    if (
                        price != null &&
                        quantity != null &&
                        quantity > 0.0
                    ) {
                        asks[price] = quantity
                    }
                }
            }

            lastUpdateId =
                response.lastUpdateId

            initialized = true
        }

        recalculateZones()
    }

    private fun connectWebSocket() {

        socket?.close(
            1000,
            "Reconnect"
        )

        val request =
            Request.Builder()
                .url(
                    "wss://stream.binance.com:9443/ws/btcusdt@depth@100ms"
                )
                .build()

        socket =
            client.newWebSocket(
                request,
                object : WebSocketListener() {

                    override fun onOpen(
                        webSocket: WebSocket,
                        response: Response
                    ) {

                        updateState(
                            currentState.copy(
                                isConnected = true,
                                isLoading = false,
                                error = null
                            )
                        )
                    }

                    override fun onMessage(
                        webSocket: WebSocket,
                        text: String
                    ) {

                        handleDepthUpdate(text)
                    }

                    override fun onClosing(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        updateState(
                            currentState.copy(
                                isConnected = false
                            )
                        )
                    }

                    override fun onClosed(
                        webSocket: WebSocket,
                        code: Int,
                        reason: String
                    ) {

                        updateState(
                            currentState.copy(
                                isConnected = false
                            )
                        )
                    }

                    override fun onFailure(
                        webSocket: WebSocket,
                        t: Throwable,
                        response: Response?
                    ) {

                        updateState(
                            currentState.copy(
                                isConnected = false,
                                error =
                                    t.message
                                        ?: "WebSocket error"
                            )
                        )
                    }
                }
            )
    }

    private fun handleDepthUpdate(
        text: String
    ) {

        try {

            val update =
                gson.fromJson(
                    text,
                    BinanceDepthUpdate::class.java
                )

            synchronized(this) {

                if (!initialized) {
                    return
                }

                /*
                 * Ігноруємо події,
                 * які повністю відбулися
                 * до нашого REST snapshot.
                 */
                if (
                    update.finalUpdateId <= lastUpdateId
                ) {
                    return
                }

                /*
                 * Перша актуальна подія
                 * повинна перетинати snapshot.
                 */
                if (
                    update.finalUpdateId > lastUpdateId + 1
                ) {

                    initialized = false

                    updateState(
                        currentState.copy(
                            isConnected = false,
                            error =
                                "Виявлено пропуск даних. Повторна синхронізація..."
                        )
                    )

                    reconnect()

                    return
                }

                update.bids.forEach { item ->

                    if (item.size < 2) {
                        return@forEach
                    }

                    val price =
                        item[0].toDoubleOrNull()
                            ?: return@forEach

                    val quantity =
                        item[1].toDoubleOrNull()
                            ?: return@forEach

                    if (quantity == 0.0) {
                        bids.remove(price)
                    } else {
                        bids[price] = quantity
                    }
                }

                update.asks.forEach { item ->

                    if (item.size < 2) {
                        return@forEach
                    }

                    val price =
                        item[0].toDoubleOrNull()
                            ?: return@forEach

                    val quantity =
                        item[1].toDoubleOrNull()
                            ?: return@forEach

                    if (quantity == 0.0) {
                        asks.remove(price)
                    } else {
                        asks[price] = quantity
                    }
                }

                lastUpdateId =
                    update.finalUpdateId
            }

            recalculateZones()

        } catch (e: Exception) {

            updateState(
                currentState.copy(
                    error =
                        e.message
                            ?: "Помилка обробки WebSocket"
                )
            )
        }
    }

    private fun reconnect() {
        socket?.close( 
            1000, 
            "Resynchronization" 
        )
        repositoryScope.launch { 
            try { delay(1000) 
                val snapshot = 
                    api.getOrderBook( symbol = "BTCUSDT", limit = 1000 ) 
                synchronized(this@OrderBookRepository) {
                    bids.clear() 
                    asks.clear() 
                    snapshot.bids.forEach { item -> 
                        if (item.size >= 2) { 
                            val price = item[0].toDoubleOrNull() 
                            val quantity = item[1].toDoubleOrNull() 
                            if ( 
                                price != null && 
                                quantity != null && 
                                quantity > 0.0 
                                ) { 
                                bids[price] = quantity 
                                } 
                            } 
                        } 
                    
                        snapshot.asks.forEach {
                            item -> 
                            if (item.size >= 2) { 
                                val price = item[0].toDoubleOrNull() 
                                val quantity = item[1].toDoubleOrNull() 
                                if ( 
                                    price != null && 
                                    quantity != null && 
                                    quantity > 0.0 
                                    ) { 
                                    asks[price] = quantity 
                                    } 
                                } 
                            } 
                    
                            lastUpdateId = snapshot.lastUpdateId 
                            initialized = true 
                        } 
                
                        recalculateZones() 
                        connectWebSocket() 
                    } catch (e: Exception) {
                        updateState( 
                            currentState.copy( 
                                isConnected = false, 
                                error = e.message 
                                ?: "Не вдалося синхронізувати стакан" 
                                ) 
                            ) 
                        } 
                    } 
                }

    private fun recalculateZones() {

        val now =
            System.currentTimeMillis()

        val bestBid =
            synchronized(this) {
                bids.keys.firstOrNull()
            }

        val bestAsk =
            synchronized(this) {
                asks.keys.firstOrNull()
            }

        if (
            bestBid == null &&
            bestAsk == null
        ) {
            return
        }

        val currentPrice =
            when {

                bestBid != null &&
                    bestAsk != null ->
                    (bestBid + bestAsk) / 2.0

                bestBid != null ->
                    bestBid

                else ->
                    bestAsk!!
            }

        val bidOrders =
            synchronized(this) {

                bids.map {
                    Order(
                        price = it.key,
                        quantity = it.value
                    )
                }
            }

        val askOrders =
            synchronized(this) {

                asks.map {
                    Order(
                        price = it.key,
                        quantity = it.value
                    )
                }
            }

        val support =
            LiquidityAnalyzer.analyze(
                orders = bidOrders,
                currentPrice = currentPrice,
                type = LiquidityZoneType.SUPPORT,
                zoneSize = currentZoneSize,
                previousZones = previousSupportZones,
                nowMillis = now
            )

        val resistance =
            LiquidityAnalyzer.analyze(
                orders = askOrders,
                currentPrice = currentPrice,
                type = LiquidityZoneType.RESISTANCE,
                zoneSize = currentZoneSize,
                previousZones = previousResistanceZones,
                nowMillis = now
            )

        previousSupportZones =
            support

        previousResistanceZones =
            resistance

        val date =
            SimpleDateFormat(
                "HH:mm:ss",
                Locale.getDefault()
            ).format(
                Date(now)
            )

        updateState(
            OrderBookState(
                currentPrice = currentPrice,

                supportZones =
                    support.sortedBy {
                        it.distancePercent
                    },

                resistanceZones =
                    resistance.sortedBy {
                        it.distancePercent
                    },

                zoneSize = currentZoneSize,

                isConnected = true,

                isLoading = false,

                error = null,

                lastUpdateTime = date
            )
        )
    }

    private fun updateState(
        state: OrderBookState
    ) {

        currentState = state

        onStateChanged?.invoke(
            state
        )
    }
}
