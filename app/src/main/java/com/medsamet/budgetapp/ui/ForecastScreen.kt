package com.medsamet.budgetapp.ui

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medsamet.budgetapp.domain.Money
import com.medsamet.budgetapp.domain.Stats
import java.time.LocalDate

@Composable
fun ForecastScreen(viewModel: BudgetViewModel) {
    val data = viewModel.data
    val today = LocalDate.now()
    val forecasts = Stats.forecast(data, today, horizonMonths = 6, lookbackMonths = 3)
    val maxTotal = forecasts.maxOfOrNull { it.totalCents } ?: 0L

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Prévisions",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Projection sur 6 mois : moyenne par catégorie des 3 derniers mois " +
                    "complets, à laquelle s'ajoutent les échéances datées de l'agenda.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (data.expenses.isEmpty()) {
            item {
                SectionCard(title = "Pas encore de données") {
                    Text(
                        "Saisis quelques dépenses : la prévision devient fiable " +
                            "à partir d'un mois complet d'historique."
                    )
                }
            }
        }

        for (forecast in forecasts) {
            item {
                SectionCard(title = monthLabel(forecast.month)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = Money.display(forecast.totalCents),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    ProportionBar(
                        fraction = if (maxTotal == 0L) {
                            0f
                        } else {
                            forecast.totalCents.toFloat() / maxTotal.toFloat()
                        },
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    LabeledRow("Dépenses courantes estimées", Money.display(forecast.recurringCents))
                    LabeledRow("Échéances de l'agenda", Money.display(forecast.eventsCents))

                    val monthStart = forecast.month.atDay(1)
                    val monthEnd = forecast.month.atEndOfMonth()
                    val dueThisMonth = data.events.filter { event ->
                        event.amountCents != null &&
                            Stats.occurrencesBetween(event, monthStart, monthEnd).isNotEmpty()
                    }
                    if (dueThisMonth.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Échéances prévues",
                            style = MaterialTheme.typography.labelLarge
                        )
                        for (event in dueThisMonth) {
                            val occurrences = Stats.occurrencesBetween(event, monthStart, monthEnd)
                            val amount = event.amountCents ?: 0L
                            for (date in occurrences) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "· " + event.title + " (" +
                                            date.format(dayFormatter) + ")",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = Money.display(amount),
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            val totalHorizon = forecasts.sumOf { it.totalCents }
            SectionCard(title = "Total projeté sur 6 mois") {
                Text(
                    text = Money.display(totalHorizon),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Soit environ " +
                        Money.display(if (forecasts.isEmpty()) 0L else totalHorizon / forecasts.size) +
                        " par mois.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
