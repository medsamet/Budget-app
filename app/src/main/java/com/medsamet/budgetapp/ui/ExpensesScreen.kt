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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medsamet.budgetapp.domain.Expense
import com.medsamet.budgetapp.domain.Income
import com.medsamet.budgetapp.domain.Money
import com.medsamet.budgetapp.domain.Stats
import com.medsamet.budgetapp.domain.TextFormat
import java.time.LocalDate
import java.time.YearMonth

/** Une ligne du relevé du mois : dépense ou revenu, présentés côte à côte. */
private data class MonthEntry(
    val id: Long,
    val isIncome: Boolean,
    val date: LocalDate,
    val amountMillimes: Long,
    val title: String,
    val subtitle: String,
    val colorHex: String
)

@Composable
fun ExpensesScreen(viewModel: BudgetViewModel) {
    val data = viewModel.data
    var month by remember { mutableStateOf(YearMonth.now()) }

    var incomeMode by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var labelText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().format(dayFormatter)) }
    var categoryCode by remember { mutableStateOf("") }
    var sourceCode by remember { mutableStateOf("") }
    var methodText by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    // Sélection effective : le premier élément de la liste tant que rien n'est choisi.
    val effectiveCategory = if (categoryCode.isEmpty()) {
        data.categories.firstOrNull()?.code ?: ""
    } else {
        categoryCode
    }
    val effectiveSource = if (sourceCode.isEmpty()) {
        data.sources.firstOrNull()?.code ?: ""
    } else {
        sourceCode
    }

    val monthExpenses = Stats.expensesOfMonth(data.expenses, month)
    val monthIncomes = Stats.incomesOfMonth(data.incomes, month)
    val expenseTotal = Stats.total(monthExpenses)
    val incomeTotal = Stats.totalIncome(monthIncomes)
    val balance = incomeTotal - expenseTotal

    val entries = ArrayList<MonthEntry>()
    for (e in monthExpenses) {
        val category = data.categories.firstOrNull { it.code == e.categoryCode }
        entries.add(
            MonthEntry(
                id = e.id,
                isIncome = false,
                date = e.date,
                amountMillimes = e.amountMillimes,
                title = if (e.label.isEmpty()) (category?.name ?: e.categoryCode) else e.label,
                subtitle = e.date.format(dayFormatter) +
                    " · " + (category?.name ?: e.categoryCode) +
                    if (e.method.isEmpty()) "" else " · " + e.method,
                colorHex = category?.colorHex ?: "#7A7A7A"
            )
        )
    }
    for (i in monthIncomes) {
        val source = data.sources.firstOrNull { it.code == i.sourceCode }
        entries.add(
            MonthEntry(
                id = i.id,
                isIncome = true,
                date = i.date,
                amountMillimes = i.amountMillimes,
                title = if (i.label.isEmpty()) (source?.name ?: i.sourceCode) else i.label,
                subtitle = i.date.format(dayFormatter) +
                    " · " + (source?.name ?: i.sourceCode) +
                    if (i.method.isEmpty()) "" else " · " + i.method,
                colorHex = source?.colorHex ?: "#4C956C"
            )
        )
    }
    entries.sortWith(compareByDescending<MonthEntry> { it.date }.thenByDescending { it.id })

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                if (!incomeMode) {
                    Button(onClick = { incomeMode = false }, modifier = Modifier.weight(1f)) {
                        Text("Dépense")
                    }
                } else {
                    OutlinedButton(onClick = { incomeMode = false }, modifier = Modifier.weight(1f)) {
                        Text("Dépense")
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (incomeMode) {
                    Button(onClick = { incomeMode = true }, modifier = Modifier.weight(1f)) {
                        Text("Revenu")
                    }
                } else {
                    OutlinedButton(onClick = { incomeMode = true }, modifier = Modifier.weight(1f)) {
                        Text("Revenu")
                    }
                }
            }
        }

        item {
            SectionCard(title = if (incomeMode) "Nouveau revenu" else "Nouvelle dépense") {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Montant (" + Money.SYMBOL + ")") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = labelText,
                    onValueChange = { labelText = it },
                    label = { Text(if (incomeMode) "Libellé (ex. vente voiture)" else "Libellé") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                DateField(label = "Date", text = dateText, onTextChange = { dateText = it })
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (incomeMode) "Source" else "Catégorie",
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (incomeMode) {
                        for (source in data.sources) {
                            if (source.code == effectiveSource) {
                                Button(onClick = { sourceCode = source.code }) { Text(source.name) }
                            } else {
                                OutlinedButton(onClick = { sourceCode = source.code }) { Text(source.name) }
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                    } else {
                        for (category in data.categories) {
                            if (category.code == effectiveCategory) {
                                Button(onClick = { categoryCode = category.code }) { Text(category.name) }
                            } else {
                                OutlinedButton(onClick = { categoryCode = category.code }) { Text(category.name) }
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = methodText,
                    onValueChange = { methodText = it },
                    label = { Text(if (incomeMode) "Moyen d'encaissement (facultatif)" else "Moyen de paiement (facultatif)") },
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
                        val amount = Money.parse(amountText)
                        val date = TextFormat.parseDate(dateText)
                        formError = when {
                            amount == null || amount == 0L -> "Montant invalide"
                            date == null -> "Date invalide"
                            incomeMode && effectiveSource.isEmpty() -> "Choisis une source"
                            !incomeMode && effectiveCategory.isEmpty() -> "Choisis une catégorie"
                            else -> null
                        }
                        if (formError == null && amount != null && date != null) {
                            if (incomeMode) {
                                viewModel.saveIncome(
                                    Income(
                                        date = date,
                                        amountMillimes = amount,
                                        sourceCode = effectiveSource,
                                        label = labelText.trim(),
                                        method = methodText.trim()
                                    )
                                )
                            } else {
                                viewModel.saveExpense(
                                    Expense(
                                        date = date,
                                        amountMillimes = amount,
                                        categoryCode = effectiveCategory,
                                        label = labelText.trim(),
                                        method = methodText.trim()
                                    )
                                )
                            }
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
                        text = Money.displaySigned(balance),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (balance < 0L) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        }
                    )
                    Text(
                        text = "Solde du mois",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { month = month.plusMonths(1) }) {
                    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Mois suivant")
                }
            }
        }

        item {
            SectionCard(title = "Le mois en un coup d'œil") {
                LabeledRow("Revenus encaissés", Money.display(incomeTotal))
                LabeledRow("Dépenses engagées", Money.display(expenseTotal))
                LabeledRow(
                    "Solde",
                    Money.displaySigned(balance),
                    valueColor = if (balance < 0L) MaterialTheme.colorScheme.error else null
                )
            }
        }

        if (entries.isEmpty()) {
            item {
                Text(
                    text = "Aucun mouvement enregistré sur ce mois.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            }
        }

        items(entries, key = { (if (it.isIncome) "revenu-" else "depense-") + it.id }) { entry ->
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorDot(entry.colorHex)
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(entry.title, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = entry.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (entry.isIncome) {
                            "+" + Money.display(entry.amountMillimes)
                        } else {
                            "-" + Money.display(entry.amountMillimes)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (entry.isIncome) {
                            Color(0xFF2E7D32)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    IconButton(
                        onClick = {
                            if (entry.isIncome) {
                                viewModel.deleteIncome(entry.id)
                            } else {
                                viewModel.deleteExpense(entry.id)
                            }
                        }
                    ) {
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
