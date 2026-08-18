package com.medsamet.budgetapp.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medsamet.budgetapp.domain.Expense
import com.medsamet.budgetapp.domain.Money
import com.medsamet.budgetapp.domain.Stats
import com.medsamet.budgetapp.domain.TextFormat
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ExpensesScreen(viewModel: BudgetViewModel) {
    val data = viewModel.data
    var month by remember { mutableStateOf(YearMonth.now()) }

    var amountText by remember { mutableStateOf("") }
    var labelText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().format(dayFormatter)) }
    var categoryCode by remember { mutableStateOf("") }
    var methodText by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    // Catégorie effective : la première de la liste tant que rien n'est choisi.
    val effectiveCategory = if (categoryCode.isEmpty()) {
        data.categories.firstOrNull()?.code ?: ""
    } else {
        categoryCode
    }

    val monthExpenses = Stats.expensesOfMonth(data.expenses, month)
    val monthTotal = Stats.total(monthExpenses)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Nouvelle dépense",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            SectionCard(title = "Saisie rapide") {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Montant (€)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text("Libellé") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DateField(label = "Date", text = dateText, onTextChange = { dateText = it })
                Spacer(Modifier.height(4.dp))
                Text("Catégorie", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (category in data.categories) {
                        if (category.code == effectiveCategory) {
                            Button(onClick = { categoryCode = category.code }) {
                                Text(category.name)
                            }
                        } else {
                            OutlinedButton(onClick = { categoryCode = category.code }) {
                                Text(category.name)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = methodText,
                    onValueChange = { methodText = it },
                    label = { Text("Moyen de paiement (facultatif)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val error = formError
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error, color = MaterialTheme.colorScheme.error)
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        val cents = Money.parseToCents(amountText)
                        val date = TextFormat.parseDate(dateText)
                        formError = when {
                            cents == null || cents == 0L -> "Montant invalide"
                            date == null -> "Date invalide"
                            effectiveCategory.isEmpty() -> "Choisis une catégorie"
                            else -> null
                        }
                        if (formError == null && cents != null && date != null) {
                            viewModel.saveExpense(
                                Expense(
                                    date = date,
                                    amountCents = cents,
                                    categoryCode = effectiveCategory,
                                    label = labelText.trim(),
                                    method = methodText.trim()
                                )
                            )
                            amountText = ""
                            labelText = ""
                            methodText = ""
                            month = YearMonth.of(date.year, date.monthValue)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enregistrer")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Mois précédent")
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = monthLabel(month),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = Money.display(monthTotal),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Mois suivant")
                }
            }
        }

        if (monthExpenses.isEmpty()) {
            item {
                Text(
                    text = "Aucune dépense enregistrée sur ce mois.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }

        items(monthExpenses, key = { it.id }) { expense ->
            val category = data.categories.firstOrNull { it.code == expense.categoryCode }
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorDot(category?.colorHex ?: "#7A7A7A")
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (expense.label.isEmpty()) {
                                category?.name ?: expense.categoryCode
                            } else {
                                expense.label
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = expense.date.format(dayFormatter) +
                                " · " + (category?.name ?: expense.categoryCode) +
                                if (expense.method.isEmpty()) "" else " · " + expense.method,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = Money.display(expense.amountCents),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = { viewModel.deleteExpense(expense.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                    }
                }
                HorizontalDivider()
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

fun monthLabel(month: YearMonth): String {
    val names = listOf(
        "janvier", "février", "mars", "avril", "mai", "juin",
        "juillet", "août", "septembre", "octobre", "novembre", "décembre"
    )
    val name = names[month.monthValue - 1]
    return name.replaceFirstChar { it.uppercase() } + " " + month.year
}
