package com.medsamet.budgetapp.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

@Composable
fun EventsScreen(viewModel: BudgetViewModel) {
    val data = viewModel.data
    val today = LocalDate.now()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var calendarMode by remember { mutableStateOf(false) }
    var calendarMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDay by remember { mutableStateOf<LocalDate?>(null) }

    var showForm by remember { mutableStateOf(false) }
    // 0 = nouvel evenement ; sinon identifiant de celui en cours de modification.
    var editingId by remember { mutableStateOf(0L) }
    // La derniere occurrence traitee n'apparait pas dans le formulaire : on la
    // conserve ici pour ne pas l'effacer en enregistrant une modification.
    var editingLastCompleted by remember { mutableStateOf<LocalDate?>(null) }

    var titleText by remember { mutableStateOf("") }
    var dateText by remember { mutableStateOf(LocalDate.now().format(dayFormatter)) }
    var kind by remember { mutableStateOf(EventKind.ANNIVERSAIRE) }
    var recurrence by remember { mutableStateOf(Recurrence.ANNUELLE) }
    var reminderText by remember { mutableStateOf("7") }
    var amountText by remember { mutableStateOf("") }
    var notesText by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }

    val isEditing = editingId != 0L

    fun resetForm() {
        editingId = 0L
        editingLastCompleted = null
        titleText = ""
        dateText = LocalDate.now().format(dayFormatter)
        kind = EventKind.ANNIVERSAIRE
        recurrence = Recurrence.ANNUELLE
        reminderText = "7"
        amountText = ""
        notesText = ""
        formError = null
        showForm = false
    }

    fun startEditing(event: EventItem) {
        editingId = event.id
        editingLastCompleted = event.lastCompleted
        titleText = event.title
        dateText = event.date.format(dayFormatter)
        kind = event.kind
        recurrence = event.recurrence
        reminderText = event.reminderDays.toString()
        amountText = event.amountMillimes?.let { Money.format(it).replace('.', ',') } ?: ""
        notesText = event.notes
        formError = null
        showForm = true
        scope.launch { listState.animateScrollToItem(0) }
    }

    val sorted = data.events.sortedBy { Stats.nextOccurrence(it, today) ?: LocalDate.MAX }
    val monthEventsByDay = Stats.eventsByDay(
        data.events,
        calendarMonth.atDay(1),
        calendarMonth.atEndOfMonth()
    )

    LazyColumn(
        state = listState,
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
                if (isEditing) {
                    OutlinedButton(onClick = { resetForm() }) { Text("Annuler") }
                } else {
                    Button(onClick = { if (showForm) resetForm() else showForm = true }) {
                        Text(if (showForm) "Fermer" else "Ajouter")
                    }
                }
            }
        }

        item {
            Row(modifier = Modifier.fillMaxWidth()) {
                if (!calendarMode) {
                    Button(onClick = { calendarMode = false }, modifier = Modifier.weight(1f)) {
                        Text("Liste")
                    }
                } else {
                    OutlinedButton(onClick = { calendarMode = false }, modifier = Modifier.weight(1f)) {
                        Text("Liste")
                    }
                }
                Spacer(Modifier.width(8.dp))
                if (calendarMode) {
                    Button(onClick = { calendarMode = true }, modifier = Modifier.weight(1f)) {
                        Text("Calendrier")
                    }
                } else {
                    OutlinedButton(onClick = { calendarMode = true }, modifier = Modifier.weight(1f)) {
                        Text("Calendrier")
                    }
                }
            }
        }

        if (showForm) {
            item {
                SectionCard(title = if (isEditing) "Modifier l'événement" else "Nouvel événement") {
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

                    val done = editingLastCompleted
                    if (isEditing && done != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Dernière occurrence traitée le " + done.format(dayFormatter) +
                                " — conservée telle quelle.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

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
                                        id = editingId,
                                        date = date,
                                        title = titleText.trim(),
                                        kind = kind,
                                        recurrence = recurrence,
                                        reminderDays = reminderText.trim().toIntOrNull() ?: 7,
                                        amountMillimes = Money.parse(amountText),
                                        notes = notesText.trim(),
                                        lastCompleted = editingLastCompleted
                                    )
                                )
                                resetForm()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isEditing) "Enregistrer les modifications" else "Enregistrer")
                    }
                    if (isEditing) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { resetForm() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Annuler la modification")
                        }
                    }
                }
            }
        }

        if (calendarMode) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        calendarMonth = calendarMonth.minusMonths(1)
                        selectedDay = null
                    }) {
                        Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "Mois précédent")
                    }
                    Text(
                        text = monthLabel(calendarMonth),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    IconButton(onClick = {
                        calendarMonth = calendarMonth.plusMonths(1)
                        selectedDay = null
                    }) {
                        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "Mois suivant")
                    }
                }
            }

            item {
                MonthCalendar(
                    month = calendarMonth,
                    eventsByDay = monthEventsByDay,
                    today = today,
                    selectedDay = selectedDay,
                    onSelectDay = { date ->
                        selectedDay = if (selectedDay == date) null else date
                    }
                )
            }

            val chosen = selectedDay
            val occurrences = ArrayList<Pair<LocalDate, EventItem>>()
            if (chosen != null) {
                for (event in monthEventsByDay[chosen] ?: emptyList()) {
                    occurrences.add(Pair(chosen, event))
                }
            } else {
                for ((date, dayEvents) in monthEventsByDay) {
                    for (event in dayEvents) occurrences.add(Pair(date, event))
                }
                occurrences.sortBy { it.first }
            }

            item {
                Text(
                    text = if (chosen != null) {
                        longDayLabel(chosen)
                    } else {
                        "Tout le mois — " + occurrences.size + " échéance(s)"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (occurrences.isEmpty()) {
                item {
                    Text(
                        text = if (chosen != null) {
                            "Aucune échéance ce jour-là."
                        } else {
                            "Aucune échéance ce mois-ci."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            }

            for (occurrence in occurrences) {
                item {
                    val date = occurrence.first
                    val event = occurrence.second
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { startEditing(event) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ColorDot(eventKindColorHex(event.kind))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = date.format(dayFormatter) + " · " + event.kind.label +
                                        if (event.recurrence == Recurrence.AUCUNE) {
                                            ""
                                        } else {
                                            " · " + event.recurrence.label
                                        },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            val amount = event.amountMillimes
                            if (amount != null) {
                                Text(
                                    text = Money.display(amount),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        } else {
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
            } else {
                item {
                    Text(
                        text = "Touche un événement pour le modifier.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            for (event in sorted) {
                item {
                    val next = Stats.nextOccurrence(event, today)
                    val days = if (next == null) null else ChronoUnit.DAYS.between(today, next)
                    val isDue = days != null && days <= event.reminderDays.toLong()
                    val selected = isEditing && editingId == event.id

                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { startEditing(event) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ColorDot(eventKindColorHex(event.kind))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = event.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        selected -> MaterialTheme.colorScheme.primary
                                        isDue -> MaterialTheme.colorScheme.error
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = if (selected) {
                                        "en cours de modification"
                                    } else {
                                        buildEventSubtitle(event, next, days)
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                val amount = event.amountMillimes
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
                            IconButton(
                                onClick = {
                                    if (selected) resetForm()
                                    viewModel.deleteEvent(event.id)
                                }
                            ) {
                                Icon(Icons.Filled.Delete, contentDescription = "Supprimer")
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** « jeudi 12 septembre 2026 » */
fun longDayLabel(date: LocalDate): String {
    val dayNames = listOf(
        "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi", "dimanche"
    )
    val monthNames = listOf(
        "janvier", "février", "mars", "avril", "mai", "juin",
        "juillet", "août", "septembre", "octobre", "novembre", "décembre"
    )
    val dayName = dayNames[date.dayOfWeek.value - 1]
    return dayName.replaceFirstChar { it.uppercase() } + " " + date.dayOfMonth +
        " " + monthNames[date.monthValue - 1] + " " + date.year
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
