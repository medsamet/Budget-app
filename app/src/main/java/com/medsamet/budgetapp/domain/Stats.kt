package com.medsamet.budgetapp.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * Calculs statistiques et prévisionnels.
 *
 * Ce fichier ne dépend d'aucune API Android : il est entièrement
 * couvert par les tests unitaires exécutés à chaque compilation.
 */
object Stats {

    // ------------------------------------------------------------ événements

    /** Nombre de mois entre deux occurrences ; 0 si l'événement ne se répète pas. */
    fun stepMonths(recurrence: Recurrence): Int = when (recurrence) {
        Recurrence.AUCUNE -> 0
        Recurrence.MENSUELLE -> 1
        Recurrence.TRIMESTRIELLE -> 3
        Recurrence.SEMESTRIELLE -> 6
        Recurrence.ANNUELLE -> 12
    }

    /**
     * Occurrences d'un événement comprises dans [from]..[to] (bornes incluses).
     * Les dates sont toujours recalculées depuis la date d'origine afin
     * d'éviter la dérive des fins de mois (31 janvier -> 28 février -> 28 mars).
     */
    fun occurrencesBetween(event: EventItem, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (to.isBefore(from)) return emptyList()
        val step = stepMonths(event.recurrence)
        if (step == 0) {
            return if (!event.date.isBefore(from) && !event.date.isAfter(to)) listOf(event.date) else emptyList()
        }
        val result = ArrayList<LocalDate>()
        var n = 0L
        val maxIterations = 5000L
        while (n < maxIterations) {
            val candidate = event.date.plusMonths(n * step)
            if (candidate.isAfter(to)) break
            if (!candidate.isBefore(from)) result.add(candidate)
            n++
        }
        return result
    }

    /** Prochaine occurrence à partir de [from] (incluse), ou null s'il n'y en a plus. */
    fun nextOccurrence(event: EventItem, from: LocalDate): LocalDate? {
        val step = stepMonths(event.recurrence)
        if (step == 0) return if (event.date.isBefore(from)) null else event.date
        var n = 0L
        while (n < 5000L) {
            val candidate = event.date.plusMonths(n * step)
            if (!candidate.isBefore(from)) return candidate
            n++
        }
        return null
    }

    /** Événements dont la prochaine occurrence tombe dans leur fenêtre de rappel. */
    fun dueEvents(events: List<EventItem>, today: LocalDate): List<Pair<EventItem, LocalDate>> {
        val due = ArrayList<Pair<EventItem, LocalDate>>()
        for (event in events) {
            val next = nextOccurrence(event, today) ?: continue
            val daysAhead = java.time.temporal.ChronoUnit.DAYS.between(today, next)
            if (daysAhead <= event.reminderDays.toLong()) {
                due.add(Pair(event, next))
            }
        }
        due.sortBy { it.second }
        return due
    }

    // ------------------------------------------------------------ dépenses

    fun monthOf(date: LocalDate): YearMonth = YearMonth.of(date.year, date.monthValue)

    fun expensesOfMonth(expenses: List<Expense>, month: YearMonth): List<Expense> =
        expenses.filter { monthOf(it.date) == month }

    fun total(expenses: List<Expense>): Long {
        var sum = 0L
        for (e in expenses) sum += e.amountCents
        return sum
    }

    fun totalsByCategory(expenses: List<Expense>): Map<String, Long> {
        val totals = LinkedHashMap<String, Long>()
        for (e in expenses) {
            totals[e.categoryCode] = (totals[e.categoryCode] ?: 0L) + e.amountCents
        }
        return totals
    }

    /** Totaux mensuels sur les [count] derniers mois, du plus ancien au plus récent. */
    fun monthlyHistory(expenses: List<Expense>, endMonth: YearMonth, count: Int): List<MonthTotal> {
        val result = ArrayList<MonthTotal>()
        for (i in count - 1 downTo 0) {
            val month = endMonth.minusMonths(i.toLong())
            result.add(MonthTotal(month, total(expensesOfMonth(expenses, month))))
        }
        return result
    }

    data class MonthTotal(val month: YearMonth, val totalCents: Long)

    // ------------------------------------------------------------ prévisions

    data class MonthForecast(
        val month: YearMonth,
        val recurringCents: Long,
        val eventsCents: Long,
        val perCategoryCents: Map<String, Long>
    ) {
        val totalCents: Long get() = recurringCents + eventsCents
    }

    /**
     * Projette les dépenses sur [horizonMonths] mois à partir du mois suivant [from].
     *
     * Méthode : moyenne par catégorie sur les [lookbackMonths] derniers mois
     * **complets** (le mois en cours, partiel, est exclu pour ne pas sous-estimer),
     * à laquelle s'ajoutent les échéances d'événements porteurs d'un montant.
     */
    fun forecast(
        data: BudgetData,
        from: LocalDate,
        horizonMonths: Int = 6,
        lookbackMonths: Int = 3
    ): List<MonthForecast> {
        val currentMonth = monthOf(from)
        val lastCompleteMonth = currentMonth.minusMonths(1)

        // Moyenne par catégorie sur les mois complets précédents.
        val sums = LinkedHashMap<String, Long>()
        var monthsWithData = 0
        for (i in 0 until lookbackMonths) {
            val month = lastCompleteMonth.minusMonths(i.toLong())
            val monthExpenses = expensesOfMonth(data.expenses, month)
            if (monthExpenses.isEmpty()) continue
            monthsWithData++
            for ((code, amount) in totalsByCategory(monthExpenses)) {
                sums[code] = (sums[code] ?: 0L) + amount
            }
        }

        val averages = LinkedHashMap<String, Long>()
        if (monthsWithData > 0) {
            for ((code, sum) in sums) {
                averages[code] = sum / monthsWithData
            }
        }
        var recurringBase = 0L
        for (value in averages.values) recurringBase += value

        val forecasts = ArrayList<MonthForecast>()
        for (i in 1..horizonMonths) {
            val month = currentMonth.plusMonths(i.toLong())
            val monthStart = month.atDay(1)
            val monthEnd = month.atEndOfMonth()

            var eventsCents = 0L
            val perCategory = LinkedHashMap<String, Long>()
            perCategory.putAll(averages)

            for (event in data.events) {
                val amount = event.amountCents ?: continue
                val occurrences = occurrencesBetween(event, monthStart, monthEnd)
                if (occurrences.isEmpty()) continue
                val contribution = amount * occurrences.size
                eventsCents += contribution
            }

            forecasts.add(
                MonthForecast(
                    month = month,
                    recurringCents = recurringBase,
                    eventsCents = eventsCents,
                    perCategoryCents = perCategory
                )
            )
        }
        return forecasts
    }
}
