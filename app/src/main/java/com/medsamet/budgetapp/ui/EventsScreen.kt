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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.medsamet.budgetapp.domain.EventItem
import com.medsamet.budgetapp.domain.EventKind
import com.medsamet.budgetapp.domain.Money
import com.medsamet.budgetapp.domain.Recurrence
import com.medsamet.budgetapp.domain.Stats
import com.medsamet.budgetapp.domain.TextFormat
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun EventsScreen(viewModel: BudgetViewModel) {
    val data = viewModel.data
    val today = LocalDate.now()

    var showForm by remember { mutableStateOf(false) }
    var titleText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().format(dayFormatter)) }
    var kind by remember { mutableStateOf(EventKind.ANNIVERSAIRE) }
    var recurrence by remember { mutableStateOf(Recurrence.ANNUELLE) }
    var reminderText by remember { mutableStateOf("7") }
    var amountText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    val sorted = data.events.sortedBy { Stats.nextOccurrence(it, today) ?: LocalDate.MAX }

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
                Text(
                    text = "Agenda",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = { showForm = !showForm }) {
                    Text(if (showForm) "Fermer" else "Ajouter")
                }
            }
        }

        if (showForm) {
            item {
                SectionCard(title = "Nouvel événement") {
                    OutlinedTextField(
                        value = titleText,
                        onValueChange = { titleText = it },
                        label = { Text("Titre") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    DateField(label = "Date", text = dateText, onTextChange = { dateText = it })

                    Text("Type", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        for (option in EventKind.values()) {
                            if (option == kind) {
                                Button(onClick = { kind = option }) { Text(option.label) }
                            } else {
                                OutlinedButton(onClick = { kind = option }) { Text(option.label) }
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text("Récurrence", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    ) {
                        for (option in Recurrence.values()) {
                            if (option == recurrence) {
                                Button(onClick = { recurrence = option }) { Text(option.label) }
                            } else {
                                OutlinedButton(onClick = { recurrence = option }) { Text(option.label) }
                            }
                            Spacer(Modifier.width(8.dp))
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reminderText,
                        onValueChange = { reminderText = it },
                        label = { Text("Rappel (jours avant)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it },
                        label = { Text("Montant prévu (facultatif)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = notesText,
                        onValueChange = { notesText = it },
                        label = { Text("Notes") },
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
                            val date = TextFormat.parseDate(dateText)
                            formError = when {
                                titleText.isBlank() -> "Le titre est obligatoire"
                                date == null -> "Date invalide"
                                else -> null
                            }
                            if (formError == null && date != null) {
                                viewModel.saveEvent(
                                    EventItem(
                                        date = date,
                                        title = titleText.trim(),
                                        kind = kind,
                                        recurrence = recurrence,
                                        reminderDays = reminderText.trim().toIntOrNull() ?: 7,
                                        amountCents = Money.parseToCents(amountText),
                                        notes = notesText.trim()
                                    )
                                )
                                titleText = ""
                                amountText = ""
                                notesText = ""
                                showForm = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Enregistrer")
                    }
                }
            }
        }

        if (sorted.isEmpty()) {
            item {
                SectionCard(title = "Aucun événement") {
                    Text(
                        "Ajoute un anniversaire, une maintenance d'équipement ou " +
                            "une date de renouvellement : l'application te préviendra " +
                            "avant l'échéance."
                    )
                }
            }
        }

        for (event in sorted) {
            item {
                val next = Stats.nextOccurrence(event, today)
                val days = if (next == null) null else ChronoUnit.DAYS.between(today, next)
                val isDue = days != null && days <= event.reminderDays.toLong()

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = event.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDue) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = buildEventSubtitle(event, next, days),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val amount = event.amountCents
                            if (amount != null) {
                                Text(
                                    text = "Montant prévu : " + Money.display(amount),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            if (event.notes.isNotEmpty()) {
                                Text(
                                    text = event.notes,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.markEventDone(event) }) {
                            Icon(Icons.Filled.Check, contentDescription = "Marquer comme fait")
                        }
                        IconButton(onClick = { viewModel.deleteEvent(event.id) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                        }
                    }
                    HorizontalDivider()
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

private fun buildEventSubtitle(event: EventItem, next: LocalDate?, days: Long?): String {
    val base = event.kind.label +
        (if (event.recurrence == Recurrence.AUCUNE) "" else " · " + event.recurrence.label)
    if (next == null || days == null) {
        return base + " · échéance passée (" + event.date.format(dayFormatter) + ")"
    }
    val whenText = when {
        days == 0L -> "aujourd'hui"
        days == 1L -> "demain"
        else -> "dans $days jours"
    }
    val done = event.lastCompleted
    val doneText = if (done == null) "" else " · fait le " + done.format(dayFormatter)
    return base + " · " + next.format(dayFormatter) + " (" + whenText + ")" + doneText
}
