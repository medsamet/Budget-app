package com.medsamet.budgetapp

import com.medsamet.budgetapp.domain.BudgetData
import com.medsamet.budgetapp.domain.EventItem
import com.medsamet.budgetapp.domain.EventKind
import com.medsamet.budgetapp.domain.Expense
import com.medsamet.budgetapp.domain.Recurrence
import com.medsamet.budgetapp.domain.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsTest {

    private fun expense(date: String, cents: Long, category: String = "alimentation") =
        Expense(date = LocalDate.parse(date), amountCents = cents, categoryCode = category)

    // ------------------------------------------------------------ récurrence

    @Test
    fun monthlyOccurrencesDoNotDriftAtMonthEnds() {
        val event = EventItem(
            date = LocalDate.of(2026, 1, 31),
            title = "Loyer",
            recurrence = Recurrence.MENSUELLE
        )
        val occurrences = Stats.occurrencesBetween(
            event,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 4, 30)
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30)
            ),
            occurrences
        )
    }

    @Test
    fun annualEventRepeatsEveryYear() {
        val event = EventItem(
            date = LocalDate.of(2024, 9, 12),
            title = "Anniversaire",
            recurrence = Recurrence.ANNUELLE
        )
        val occurrences = Stats.occurrencesBetween(
            event,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2027, 12, 31)
        )
        assertEquals(
            listOf(LocalDate.of(2026, 9, 12), LocalDate.of(2027, 9, 12)),
            occurrences
        )
    }

    @Test
    fun nonRecurringEventHasASingleOccurrence() {
        val event = EventItem(date = LocalDate.of(2026, 5, 4), title = "Contrôle technique")
        assertEquals(
            listOf(LocalDate.of(2026, 5, 4)),
            Stats.occurrencesBetween(event, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31))
        )
        assertNull(Stats.nextOccurrence(event, LocalDate.of(2026, 6, 1)))
        assertEquals(
            LocalDate.of(2026, 5, 4),
            Stats.nextOccurrence(event, LocalDate.of(2026, 1, 1))
        )
    }

    @Test
    fun quarterlyEventStepsByThreeMonths() {
        val event = EventItem(
            date = LocalDate.of(2026, 1, 15),
            title = "Maintenance chaudière",
            recurrence = Recurrence.TRIMESTRIELLE
        )
        assertEquals(
            LocalDate.of(2026, 4, 15),
            Stats.nextOccurrence(event, LocalDate.of(2026, 2, 1))
        )
    }

    @Test
    fun dueEventsRespectTheReminderWindow() {
        val today = LocalDate.of(2026, 8, 18)
        val soon = EventItem(
            date = LocalDate.of(2026, 8, 25),
            title = "Licence",
            kind = EventKind.LICENCE,
            reminderDays = 10
        )
        val later = EventItem(
            date = LocalDate.of(2026, 12, 1),
            title = "Assurance",
            reminderDays = 10
        )
        val due = Stats.dueEvents(listOf(soon, later), today)
        assertEquals(1, due.size)
        assertEquals("Licence", due[0].first.title)
        assertEquals(LocalDate.of(2026, 8, 25), due[0].second)
    }

    // -------------------------------------------------------------- totaux

    @Test
    fun computesMonthlyTotalsAndCategoryBreakdown() {
        val expenses = listOf(
            expense("2026-08-01", 1000L, "alimentation"),
            expense("2026-08-15", 2500L, "alimentation"),
            expense("2026-08-20", 4000L, "loisirs"),
            expense("2026-07-10", 9999L, "alimentation")
        )
        val august = Stats.expensesOfMonth(expenses, YearMonth.of(2026, 8))
        assertEquals(3, august.size)
        assertEquals(7500L, Stats.total(august))

        val breakdown = Stats.totalsByCategory(august)
        assertEquals(3500L, breakdown["alimentation"])
        assertEquals(4000L, breakdown["loisirs"])
    }

    @Test
    fun monthlyHistoryReturnsOldestFirst() {
        val expenses = listOf(
            expense("2026-06-01", 1000L),
            expense("2026-08-01", 3000L)
        )
        val history = Stats.monthlyHistory(expenses, YearMonth.of(2026, 8), 3)
        assertEquals(3, history.size)
        assertEquals(YearMonth.of(2026, 6), history[0].month)
        assertEquals(1000L, history[0].totalCents)
        assertEquals(0L, history[1].totalCents)
        assertEquals(3000L, history[2].totalCents)
    }

    // ---------------------------------------------------------- prévisions

    @Test
    fun forecastAveragesCompleteMonthsAndAddsScheduledEvents() {
        val data = BudgetData(
            expenses = listOf(
                expense("2026-05-10", 30000L),
                expense("2026-06-10", 20000L),
                expense("2026-07-10", 10000L),
                // Le mois courant est partiel : il ne doit pas tirer la moyenne vers le bas.
                expense("2026-08-02", 500L)
            ),
            events = listOf(
                EventItem(
                    date = LocalDate.of(2026, 9, 12),
                    title = "Anniversaire",
                    recurrence = Recurrence.ANNUELLE,
                    amountCents = 5000L
                )
            )
        )

        val forecasts = Stats.forecast(data, LocalDate.of(2026, 8, 18), horizonMonths = 6)

        assertEquals(6, forecasts.size)
        assertEquals(YearMonth.of(2026, 9), forecasts[0].month)
        assertEquals(20000L, forecasts[0].recurringCents)
        assertEquals(5000L, forecasts[0].eventsCents)
        assertEquals(25000L, forecasts[0].totalCents)

        assertEquals(YearMonth.of(2026, 10), forecasts[1].month)
        assertEquals(0L, forecasts[1].eventsCents)
        assertEquals(20000L, forecasts[1].totalCents)
    }

    @Test
    fun monthlyEventAppearsInEveryForecastMonth() {
        val data = BudgetData(
            events = listOf(
                EventItem(
                    date = LocalDate.of(2026, 3, 5),
                    title = "Abonnement",
                    recurrence = Recurrence.MENSUELLE,
                    amountCents = 1500L
                )
            )
        )
        val forecasts = Stats.forecast(data, LocalDate.of(2026, 8, 18), horizonMonths = 3)
        assertEquals(3, forecasts.size)
        for (forecast in forecasts) {
            assertEquals(1500L, forecast.eventsCents)
        }
    }

    @Test
    fun forecastWithoutHistoryStaysAtZeroWithoutCrashing() {
        val forecasts = Stats.forecast(BudgetData(), LocalDate.of(2026, 8, 18), horizonMonths = 2)
        assertEquals(2, forecasts.size)
        assertTrue(forecasts.all { it.totalCents == 0L })
    }
}
