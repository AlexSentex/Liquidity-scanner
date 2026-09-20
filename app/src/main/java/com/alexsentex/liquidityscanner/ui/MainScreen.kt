package com.alexsentex.liquidityscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexsentex.liquidityscanner.model.AbsorptionEvent
import com.alexsentex.liquidityscanner.model.AbsorptionOutcome
import com.alexsentex.liquidityscanner.model.LiquidityZone
import java.util.Locale

private val zoneSizes =
    listOf(
        100.0,
        250.0,
        500.0,
        1000.0,
        2500.0,
        5000.0
    )

@Composable
fun MainScreen(
    viewModel: MainViewModel
) {

    val state by
        viewModel.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "BTC Liquidity Scanner",
            style =
                MaterialTheme.typography
                    .headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Column {

                Text(
                    text = "BTC/USDT",
                    style =
                        MaterialTheme.typography
                            .labelLarge
                )

                Text(
                    text =
                        state.currentPrice?.let {

                            "$%,.2f".format(
                                Locale.US,
                                it
                            )

                        } ?: "—",
                    style =
                        MaterialTheme.typography
                            .headlineMedium,
                    fontWeight =
                        FontWeight.Bold
                )
            }

            Column {

                Text(
                    text =
                        if (state.isConnected)
                            "● LIVE"
                        else
                            "○ OFFLINE",
                    fontWeight =
                        FontWeight.Bold
                )

                state.lastUpdateTime?.let {

                    Text(
                        text = it,
                        style =
                            MaterialTheme.typography
                                .bodySmall
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "Розмір діапазону",
            style =
                MaterialTheme.typography
                    .titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        LazyColumn(
            modifier =
                Modifier.height(48.dp)
        ) {

            items(zoneSizes) { size ->

                FilterChip(
                    selected =
                        state.zoneSize == size,

                    onClick = {
                        viewModel
                            .setZoneSize(size)
                    },

                    label = {

                        Text(
                            text =
                                "${
