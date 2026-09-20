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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alexsentex.liquidityscanner.model.LiquidityZone
import com.alexsentex.liquidityscanner.model.Order
import java.util.Locale

@Composable
fun MainScreen(viewModel: MainViewModel) {

    val state by viewModel.state.collectAsState()

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

        Text(
            text = "Binance Spot • BTC/USDT",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

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
                            "$%,.2f".format(
                                Locale.US,
                                it
                            )
                        } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = viewModel::loadOrderBook
            ) {
                Text("Оновити")
            }
        }

        state.lastUpdateTime?.let {

            Text(
                text = "Оновлено: $it",
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        state.error?.let {

            Text(
                text = "Помилка: $it",
                color = MaterialTheme.colorScheme.error
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )
        }

        if (
            state.isLoading &&
            state.bids.isEmpty()
        ) {

            CircularProgressIndicator()

        } else {

            LiquidityZonesSection(
                title = "🟢 SUPPORT — ПІДТРИМКА",
                zones = state.supportZones
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )

            LiquidityZonesSection(
                title = "🔴 RESISTANCE — ОПІР",
                zones = state.resistanceZones
            )

            Spacer(
                modifier = Modifier.height(16.dp)
            )

            Text(
                text = "СТАКАН",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    text = "BIDS — BUY",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall
                )

                Text(
                    text = "ASKS — SELL",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Row(
                modifier = Modifier.weight(1f)
            ) {

                OrderColumn(
                    orders = state.bids,
                    modifier = Modifier.weight(1f)
                )

                OrderColumn(
                    orders = state.asks,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LiquidityZonesSection(
    title: String,
    zones: List<LiquidityZone>
) {

    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    )

    Spacer(
        modifier = Modifier.height(4.dp)
    )

    if (zones.isEmpty()) {

        Text(
            text = "Зони не знайдені",
            style = MaterialTheme.typography.bodySmall
        )

    } else {

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
        ) {

            items(zones) { zone ->

                LiquidityZoneRow(zone)

                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun LiquidityZoneRow(
    zone: LiquidityZone
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                vertical = 6.dp
            ),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {

        Column(
            modifier = Modifier.weight(1.2f)
        ) {

            Text(
                text =
                    "$%,.0f".format(
                        Locale.US,
                        zone.centerPrice
                    ),
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    "%.2f%% від ціни"
                        .format(
                            Locale.US,
                            zone.distancePercent
                        ),
                style =
                    MaterialTheme.typography.bodySmall
            )
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {

            Text(
                text =
                    "%.4f BTC"
                        .format(
                            Locale.US,
                            zone.totalQuantity
                        ),
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    "$%,.0f"
                        .format(
                            Locale.US,
                            zone.totalUsdt
                        ),
                style =
                    MaterialTheme.typography.bodySmall
            )
        }

        Column(
            modifier = Modifier.weight(0.7f)
        ) {

            Text(
                text =
                    "%.1fx"
                        .format(
                            Locale.US,
                            zone.strength
                        ),
                fontWeight = FontWeight.Bold
            )

            Text(
                text =
                    "${zone.levelCount} рів.",
                style =
                    MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun OrderColumn(
    orders: List<Order>,
    modifier: Modifier = Modifier
) {

    LazyColumn(
        modifier = modifier.padding(
            horizontal = 4.dp
        )
    ) {

        items(orders) { order ->

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        vertical = 4.dp
                    )
            ) {

                Text(
                    text =
                        "%,.2f".format(
                            Locale.US,
                            order.price
                        )
                )

                Text(
                    text =
                        "%.6f BTC"
                            .format(
                                Locale.US,
                                order.quantity
                            )
                )

                Text(
                    text =
                        "$%,.2f"
                            .format(
                                Locale.US,
                                order.totalUsdt
                            )
                )

                HorizontalDivider(
                    modifier = Modifier.padding(
                        top = 4.dp
                    )
                )
            }
        }
    }
}
