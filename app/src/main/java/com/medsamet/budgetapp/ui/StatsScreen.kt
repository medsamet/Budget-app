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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medsamet.budgetapp.domain.Money
import com.medsamet.budgetapp.domain.Stats
import java.time.YearMonth

@Composable
fun StatsScreen(viewModel: BudgetViewModel) {
    val data = viewModel.data
    var month by remember { mutableStateOf(YearMonth.now()) }

    val monthExpenses = Stats.expensesOfMonth(data.expenses, month)
    val total = Stats.total(monthExpenses)
    val byCategory = Stats.totalsByCategory(monthExpenses)
        .toList()
        .sortedByDescending { it.second }
    val history = Stats.monthlyHistory(data.expenses, month, 6)
    val maxHistory = history.maxOfOrNull { it.totalCents } ?: 0L
    val average = if (history.isEmpty()) 0L else history.sumOf { it.totalCents } / history.size

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Mois précédent")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(monthLabel(month), style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = Money.display(total),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Mois suivant")
                }
            }
        }

        item {
            SectionCard(title = "Répartition par catégorie") {
                if (byCategory.isEmpty()) {
                    Text("Aucune dépense sur ce mois.")
                } else {
                    for ((code, amount) in byCategory) {
                        val category = data.categories.firstOrNull { it.code == code }
                        val share = if (total == 0L) 0f else amount.toFloat() / total.toFloat()
                        Column(modifier = Modifier.padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ColorDot(category?.colorHex ?: "#7A7A7A")
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = category?.name ?: code,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = Money.display(amount) +
                                        "  (" + Math.round(share * 100f).toString() + " %)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            ProportionBar(
                                fraction = share,
                                color = parseHexColor(category?.colorHex ?: "#7A7A7A")
                            )
                            val budget = category?.monthlyBudgetCents
                            if (budget != null && budget > 0L) {
                                val ratio = amount.toFloat() / budget.toFloat()
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Budget " + Money.display(budget) + " · " +
                                        Math.round(ratio * 100f).toString() + " % consommé" +
                                        if (amount > budget) " — dépassement" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (amount > budget) {
                                        MaterialTheme.colorScheme.error
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            SectionCard(title = "6 derniers mois") {
                for (entry in history) {
                    val fraction = if (maxHistory == 0L) {
                        0f
                    } else {
                        entry.totalCents.toFloat() / maxHistory.toFloat()
                    }
                    Column(modifier = Modifier.padding(vertical = 5.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(monthLabel(entry.month), style = MaterialTheme.typography.bodySmall)
                            Text(
                                Money.display(entry.totalCents),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        ProportionBar(
                            fraction = fraction,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                LabeledRow("Moyenne sur la période", Money.display(average))
            }
        }

        item {
            SectionCard(title = "Repères") {
                LabeledRow("Nombre de dépenses", monthExpenses.size.toString())
                LabeledRow(
                    "Dépense moyenne",
                    if (monthExpenses.isEmpty()) "—" else Money.display(total / monthExpenses.size)
                )
                LabeledRow(
                    "Plus grosse dépense",
                    monthExpenses.maxByOrNull { it.amountCents }
                        ?.let { Money.display(it.amountCents) } ?: "—"
                )
                val budgetTotal = data.categories.sumOf { it.monthlyBudgetCents ?: 0L }
                if (budgetTotal > 0L) {
                    LabeledRow("Budget mensuel défini", Money.display(budgetTotal))
                    LabeledRow(
                        "Reste à dépenser",
                        Money.display(budgetTotal - total),
                        valueColor = if (total > budgetTotal) {
                            MaterialTheme.colorScheme.error
                        } else {
                            null
                        }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
