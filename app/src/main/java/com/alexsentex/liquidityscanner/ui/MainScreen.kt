package com.alexsentex.liquidityscanner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Column {

                Text(
                    text = "BTC/USDT",
                    style = MaterialTheme.typography.labelLarge
                )

                Text(
                    text =
                        state.currentPrice?.let {
                            "$%,.2f".format(Locale.US, it)
                        } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Column {

                Text(
                    text = if (state.isConnected) "● LIVE" else "○ OFFLINE",
                    fontWeight = FontWeight.Bold
                )

                state.lastUpdateTime?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Розмір діапазону",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(6.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            items(zoneSizes) { size ->

                FilterChip(
                    selected = state.zoneSize == size,
                    onClick = { viewModel.setZoneSize(size) },
                    label = {
                        Text(text = "${size.toInt()} $")
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        state.error?.let {

            Text(
                text = it,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
        ) {

            ZonePanel(
                title = "🟢 SUPPORT",
                zones = state.supportZones,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(12.dp))

            ZonePanel(
                title = "🔴 RESISTANCE",
                zones = state.resistanceZones,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "📋 ОСТАННІ ПОДІЇ",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        EventListCompact(events = state.recentEvents)
    }
}

@Composable
private fun ZonePanel(
    title: String,
    zones: List<LiquidityZone>,
    modifier: Modifier = Modifier
) {

    Column(
        modifier = modifier.fillMaxWidth()
    ) {

        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        if (zones.isEmpty()) {

            Text(
                text = "Немає даних",
                style = MaterialTheme.typography.bodySmall
            )

        } else {

            LazyColumn(
                modifier = Modifier.weight(1f)
            ) {

                items(items = zones.take(15)) { zone ->
                    ZoneRow(zone = zone)
                }
            }
        }
    }
}

@Composable
private fun ZoneRow(
    zone: LiquidityZone
) {

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
    ) {

        Text(
            text =
                "$%,.0f – $%,.0f".format(
                    Locale.US,
                    zone.lowerPrice,
                    zone.upperPrice
                ),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            text = "%.4f BTC".format(Locale.US, zone.totalQuantity),
            style = MaterialTheme.typography.bodySmall
        )

        Text(
            text =
                "%.2f%% · %.1fx · %.0f%%".format(
                    Locale.US,
                    zone.distancePercent,
                    zone.strength,
                    zone.stabilityPercent
                ),
            style = MaterialTheme.typography.bodySmall
        )

        if (zone.exchangeCount > 0) {

            Text(
                text = "📊 ${zone.exchanges.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (zone.exchangeCount > 1)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EventListCompact(
    events: List<AbsorptionEvent>
) {

    if (events.isEmpty()) {

        Text(
            text = "Подій ще не було",
            style = MaterialTheme.typography.bodySmall
        )

        return
    }

    Column {

        events.take(4).forEach { event ->

            val label =
                if (event.outcome == AbsorptionOutcome.ABSORBED)
                    "🟢 Поглинуто"
                else
                    "⚪ Знято"

            Text(
                text =
                    "$label: $%,.0f – $%,.0f (%.3f BTC)".format(
                        Locale.US,
                        event.lowerPrice,
                        event.upperPrice,
                        event.tradedQuantity
                    ),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
