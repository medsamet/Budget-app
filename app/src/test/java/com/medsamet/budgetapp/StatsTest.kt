package com.medsamet.budgetapp

import com.medsamet.budgetapp.domain.BudgetData
import com.medsamet.budgetapp.domain.EventItem
import com.medsamet.budgetapp.domain.EventKind
import com.medsamet.budgetapp.domain.Expense
import com.medsamet.budgetapp.domain.Income
import com.medsamet.budgetapp.domain.Recurrence
import com.medsamet.budgetapp.domain.Stats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class StatsTest {

    private fun expense(date: String, millimes: Long, category: String = "alimentation") =
        Expense(date = LocalDate.parse(date), amountMillimes = millimes, categoryCode = category)

    private fun income(date: String, millimes: Long, source: String = "salaire") =
        Income(date = LocalDate.parse(date), amountMillimes = millimes, sourceCode = source)

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

    @Test
    fun bimonthlyEventStepsByTwoMonths() {
        val event = EventItem(
            date = LocalDate.of(2026, 1, 5),
            title = "Facture eau",
            recurrence = Recurrence.BIMESTRIELLE
        )
        assertEquals(
            listOf(
                LocalDate.of(2026, 1, 5),
                LocalDate.of(2026, 3, 5),
                LocalDate.of(2026, 5, 5),
                LocalDate.of(2026, 7, 5)
            ),
            Stats.occurrencesBetween(event, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 8, 31))
        )
        assertEquals(
            LocalDate.of(2026, 3, 5),
            Stats.nextOccurrence(event, LocalDate.of(2026, 2, 1))
        )
    }

    @Test
    fun bimonthlyDoesNotDriftAtMonthEnds() {
        val event = EventItem(
            date = LocalDate.of(2025, 12, 31),
            title = "Relève compteur",
            recurrence = Recurrence.BIMESTRIELLE
        )
        assertEquals(
            listOf(
                LocalDate.of(2025, 12, 31),
                LocalDate.of(2026, 2, 28),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 6, 30)
            ),
            Stats.occurrencesBetween(event, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 7, 1))
        )
    }

    // ------------------------------------------------------------ calendrier

    @Test
    fun groupsOccurrencesByDayIncludingSeveralOnTheSameDate() {
        val anniversaire = EventItem(
            date = LocalDate.of(2026, 9, 12),
            title = "Anniversaire",
            recurrence = Recurrence.ANNUELLE
        )
        val abonnement = EventItem(
            date = LocalDate.of(2026, 9, 12),
            title = "Abonnement",
            recurrence = Recurrence.MENSUELLE
        )
        val byDay = Stats.eventsByDay(
            listOf(anniversaire, abonnement),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30)
        )
        assertEquals(1, byDay.size)
        assertEquals(2, byDay[LocalDate.of(2026, 9, 12)]?.size)
    }

    @Test
    fun aMonthlyEventAppearsOnceInEachMonthOfTheRange() {
        val event = EventItem(
            date = LocalDate.of(2026, 3, 10),
            title = "Abonnement",
            recurrence = Recurrence.MENSUELLE
        )
        val byDay = Stats.eventsByDay(
            listOf(event),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30)
        )
        assertEquals(1, byDay.size)
        assertEquals(listOf(LocalDate.of(2026, 9, 10)), byDay.keys.toList())
    }

    @Test
    fun anEmptyMonthGroupsToNothing() {
        val event = EventItem(date = LocalDate.of(2026, 5, 4), title = "Contrôle technique")
        val byDay = Stats.eventsByDay(
            listOf(event),
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 30)
        )
        assertTrue(byDay.isEmpty())
    }

    // -------------------------------------------------------------- totaux

    @Test
    fun computesMonthlyTotalsAndCategoryBreakdown() {
        val expenses = listOf(
            expense("2026-08-01", 10000L, "alimentation"),
            expense("2026-08-15", 25000L, "alimentation"),
            expense("2026-08-20", 40000L, "loisirs"),
            expense("2026-07-10", 99990L, "alimentation")
        )
        val august = Stats.expensesOfMonth(expenses, YearMonth.of(2026, 8))
        assertEquals(3, august.size)
        assertEquals(75000L, Stats.total(august))

        val breakdown = Stats.totalsByCategory(august)
        assertEquals(35000L, breakdown["alimentation"])
        assertEquals(40000L, breakdown["loisirs"])
    }

    @Test
    fun computesIncomeTotalsAndSourceBreakdown() {
        val incomes = listOf(
            income("2026-08-01", 2450000L, "salaire"),
            income("2026-08-12", 380500L, "vente"),
            income("2026-08-20", 150000L, "prime"),
            income("2026-07-01", 2450000L, "salaire")
        )
        val august = Stats.incomesOfMonth(incomes, YearMonth.of(2026, 8))
        assertEquals(3, august.size)
        assertEquals(2980500L, Stats.totalIncome(august))

        val bySource = Stats.totalsBySource(august)
        assertEquals(2450000L, bySource["salaire"])
        assertEquals(380500L, bySource["vente"])
        assertEquals(150000L, bySource["prime"])
    }

    @Test
    fun computesTheMonthlyBalance() {
        val data = BudgetData(
            expenses = listOf(expense("2026-08-05", 850000L, "logement")),
            incomes = listOf(income("2026-08-01", 2450000L))
        )
        assertEquals(1600000L, Stats.balanceOfMonth(data, YearMonth.of(2026, 8)))
    }

    @Test
    fun aNegativeBalanceIsReportedAsSuch() {
        val data = BudgetData(
            expenses = listOf(expense("2026-08-05", 3000000L, "logement")),
            incomes = listOf(income("2026-08-01", 2450000L))
        )
        assertEquals(-550000L, Stats.balanceOfMonth(data, YearMonth.of(2026, 8)))
    }

    @Test
    fun monthlyHistoryReturnsOldestFirstWithBothFlows() {
        val data = BudgetData(
            expenses = listOf(
                expense("2026-06-01", 100000L),
                expense("2026-08-01", 300000L)
            ),
            incomes = listOf(income("2026-08-01", 2450000L))
        )
        val history = Stats.monthlyHistory(data, YearMonth.of(2026, 8), 3)
        assertEquals(3, history.size)
        assertEquals(YearMonth.of(2026, 6), history[0].month)
        assertEquals(100000L, history[0].expenseMillimes)
        assertEquals(0L, history[0].incomeMillimes)
        assertEquals(0L, history[1].expenseMillimes)
        assertEquals(300000L, history[2].expenseMillimes)
        assertEquals(2450000L, history[2].incomeMillimes)
        assertEquals(2150000L, history[2].netMillimes)
    }

    // ---------------------------------------------------------- prévisions

    @Test
    fun forecastAveragesCompleteMonthsAndAddsScheduledEvents() {
        val data = BudgetData(
            expenses = listOf(
                expense("2026-05-10", 300000L),
                expense("2026-06-10", 200000L),
                expense("2026-07-10", 100000L),
                // Le mois courant est partiel : il ne doit pas tirer la moyenne vers le bas.
                expense("2026-08-02", 5000L)
            ),
            events = listOf(
                EventItem(
                    date = LocalDate.of(2026, 9, 12),
                    title = "Anniversaire",
                    recurrence = Recurrence.ANNUELLE,
                    amountMillimes = 50000L
                )
            )
        )

        val forecasts = Stats.forecast(data, LocalDate.of(2026, 8, 18), horizonMonths = 6)

        assertEquals(6, forecasts.size)
        assertEquals(YearMonth.of(2026, 9), forecasts[0].month)
        assertEquals(200000L, forecasts[0].recurringExpenseMillimes)
        assertEquals(50000L, forecasts[0].eventsMillimes)
        assertEquals(250000L, forecasts[0].totalExpenseMillimes)

        assertEquals(YearMonth.of(2026, 10), forecasts[1].month)
        assertEquals(0L, forecasts[1].eventsMillimes)
        assertEquals(200000L, forecasts[1].totalExpenseMillimes)
    }

    @Test
    fun forecastProjectsIncomeAndNetBalance() {
        val data = BudgetData(
            expenses = listOf(
                expense("2026-06-10", 1000000L),
                expense("2026-07-10", 1000000L)
            ),
            incomes = listOf(
                income("2026-06-01", 2400000L),
                income("2026-07-01", 2400000L)
            )
        )

        val forecasts = Stats.forecast(data, LocalDate.of(2026, 8, 18), horizonMonths = 2)

        assertEquals(2400000L, forecasts[0].expectedIncomeMillimes)
        assertEquals(1000000L, forecasts[0].totalExpenseMillimes)
        assertEquals(1400000L, forecasts[0].netMillimes)
    }

    @Test
    fun forecastNetTurnsNegativeWhenChargesExceedIncome() {
        val data = BudgetData(
            expenses = listOf(expense("2026-07-10", 2000000L)),
            incomes = listOf(income("2026-07-01", 1500000L))
        )
        val forecasts = Stats.forecast(data, LocalDate.of(2026, 8, 18), horizonMonths = 1)
        assertEquals(-500000L, forecasts[0].netMillimes)
    }

    @Test
    fun monthlyEventAppearsInEveryForecastMonth() {
        val data = BudgetData(
            events = listOf(
                EventItem(
                    date = LocalDate.of(2026, 3, 5),
                    title = "Abonnement",
                    recurrence = Recurrence.MENSUELLE,
                    amountMillimes = 15000L
                )
            )
        )
        val forecasts = Stats.forecast(data, LocalDate.of(2026, 8, 18), horizonMonths = 3)
        assertEquals(3, forecasts.size)
        for (forecast in forecasts) {
            assertEquals(15000L, forecast.eventsMillimes)
        }
    }

    @Test
    fun forecastWithoutHistoryStaysAtZeroWithoutCrashing() {
        val forecasts = Stats.forecast(BudgetData(), LocalDate.of(2026, 8, 18), horizonMonths = 2)
        assertEquals(2, forecasts.size)
        assertTrue(forecasts.all { it.totalExpenseMillimes == 0L })
        assertTrue(forecasts.all { it.expectedIncomeMillimes == 0L })
        assertTrue(forecasts.all { it.netMillimes == 0L })
    }
}
