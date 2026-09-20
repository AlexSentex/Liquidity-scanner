package com.alexsentex.liquidityscanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexsentex.liquidityscanner.model.OrderBookState
import com.alexsentex.liquidityscanner.repository.OrderBookRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val repository = OrderBookRepository()

    private val _state =
        MutableStateFlow(OrderBookState())

    val state =
        _state.asStateFlow()

    init {
        startAutoRefresh()
    }

    fun loadOrderBook() {

        viewModelScope.launch {

            _state.value =
                _state.value.copy(
                    isLoading = true,
                    error = null
                )

            try {

                val newState =
                    repository.getOrderBook()

                _state.value = newState

            } catch (e: Exception) {

                _state.value =
                    _state.value.copy(
                        isLoading = false,
                        error =
                            e.message
                                ?: "Помилка підключення до Binance"
                    )
            }
        }
    }

    private fun startAutoRefresh() {

        viewModelScope.launch {

            loadOrderBook()

            while (true) {

                delay(5000)

                loadOrderBook()
            }
        }
    }
}
