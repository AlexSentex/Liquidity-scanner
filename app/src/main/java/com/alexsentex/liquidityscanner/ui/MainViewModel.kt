package com.alexsentex.liquidityscanner.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alexsentex.liquidityscanner.model.OrderBookState
import com.alexsentex.liquidityscanner.repository.MultiExchangeOrderBookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val repository =
        MultiExchangeOrderBookRepository()

    private val _state =
        MutableStateFlow(
            OrderBookState()
        )

    val state =
        _state.asStateFlow()

    private var started = false

    init {

        start(
            zoneSize = 500.0
        )
    }

    private fun start(
        zoneSize: Double
    ) {

        if (started) {
            return
        }

        started = true

        viewModelScope.launch {

            repository.start(
                zoneSize = zoneSize
            ) { newState ->

                _state.value =
                    newState
            }
        }
    }

    fun setZoneSize(
        zoneSize: Double
    ) {

        repository.setZoneSize(
            zoneSize
        )

        _state.value =
            _state.value.copy(
                zoneSize = zoneSize
            )
    }

    override fun onCleared() {

        repository.stop()

        super.onCleared()
    }
}
