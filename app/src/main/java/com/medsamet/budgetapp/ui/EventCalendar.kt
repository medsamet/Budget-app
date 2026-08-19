package com.medsamet.budgetapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.medsamet.budgetapp.domain.EventItem
import com.medsamet.budgetapp.domain.EventKind
import java.time.LocalDate
import java.time.YearMonth

/** Couleur associée à chaque type d'événement, pour les pastilles du calendrier. */
fun eventKindColorHex(kind: EventKind): String = when (kind) {
    EventKind.ANNIVERSAIRE -> "#C1666B"
    EventKind.MAINTENANCE -> "#2E7D8F"
    EventKind.LICENCE -> "#8A6552"
    EventKind.ASSURANCE -> "#7C6BB0"
    EventKind.AUTRE -> "#7A7A7A"
}

private val weekDayLabels = listOf("Lu", "Ma", "Me", "Je", "Ve", "Sa", "Di")

/**
 * Grille mensuelle classique, semaines commençant le lundi.
 *
 * Volontairement construite en Column/Row plutôt qu'avec une grille paresseuse :
 * un mois tient en 42 cellules au maximum, et imbriquer une grille défilante
 * dans la liste défilante de l'écran poserait plus de problèmes que le gain.
 */
@Composable
fun MonthCalendar(
    month: YearMonth,
    eventsByDay: Map<LocalDate, List<EventItem>>,
    today: LocalDate,
    selectedDay: LocalDate?,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val firstOfMonth = month.atDay(1)
    // DayOfWeek.value : lundi = 1 … dimanche = 7.
    val leadingBlanks = firstOfMonth.dayOfWeek.value - 1
    val dayCount = month.lengthOfMonth()
    val cellCount = leadingBlanks + dayCount
    val weekCount = (cellCount + 6) / 7

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth()) {
            for (label in weekDayLabels) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        for (week in 0 until weekCount) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (position in 0 until 7) {
                    val cellIndex = week * 7 + position
                    val dayOfMonth = cellIndex - leadingBlanks + 1
                    if (dayOfMonth < 1 || dayOfMonth > dayCount) {
                        Box(modifier = Modifier.weight(1f).aspectRatio(1f))
                    } else {
                        val date = month.atDay(dayOfMonth)
                        DayCell(
                            date = date,
                            events = eventsByDay[date] ?: emptyList(),
                            isToday = date == today,
                            isSelected = date == selectedDay,
                            onClick = { onSelectDay(date) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    events: List<EventItem>,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val background = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .then(
                if (isToday) {
                    Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp)
                    )
                } else {
                    Modifier
                }
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
            if (events.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    // Au-delà de trois événements le même jour, on s'arrête :
                    // la pastille supplémentaire n'apprendrait rien de plus.
                    for (event in events.take(3)) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(parseHexColor(eventKindColorHex(event.kind)))
                        )
                        Spacer(Modifier.width(2.dp))
                    }
                }
            }
        }
    }
}
