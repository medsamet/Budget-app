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
        Recurrence.BIMESTRIELLE -> 2
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

    /**
     * Occurrences de tous les événements, regroupées par jour, sur l'intervalle
     * [from]..[to]. Alimente la vue calendrier : un même jour peut porter
     * plusieurs événements, et un même événement apparaître plusieurs fois
     * dans l'intervalle s'il est récurrent.
     */
    fun eventsByDay(
        events: List<EventItem>,
        from: LocalDate,
        to: LocalDate
    ): Map<LocalDate, List<EventItem>> {
        val byDay = LinkedHashMap<LocalDate, MutableList<EventItem>>()
        for (event in events) {
            for (date in occurrencesBetween(event, from, to)) {
                byDay.getOrPut(date) { ArrayList() }.add(event)
            }
        }
        return byDay
    }

    // ------------------------------------------------------- dépenses et revenus

    fun monthOf(date: LocalDate): YearMonth = YearMonth.of(date.year, date.monthValue)

    fun expensesOfMonth(expenses: List<Expense>, month: YearMonth): List<Expense> =
        expenses.filter { monthOf(it.date) == month }

    fun incomesOfMonth(incomes: List<Income>, month: YearMonth): List<Income> =
        incomes.filter { monthOf(it.date) == month }

    fun total(expenses: List<Expense>): Long {
        var sum = 0L
        for (e in expenses) sum += e.amountMillimes
        return sum
    }

    fun totalIncome(incomes: List<Income>): Long {
        var sum = 0L
        for (i in incomes) sum += i.amountMillimes
        return sum
    }

    fun totalsByCategory(expenses: List<Expense>): Map<String, Long> {
        val totals = LinkedHashMap<String, Long>()
        for (e in expenses) {
            totals[e.categoryCode] = (totals[e.categoryCode] ?: 0L) + e.amountMillimes
        }
        return totals
    }

    fun totalsBySource(incomes: List<Income>): Map<String, Long> {
        val totals = LinkedHashMap<String, Long>()
        for (i in incomes) {
            totals[i.sourceCode] = (totals[i.sourceCode] ?: 0L) + i.amountMillimes
        }
        return totals
    }

    /** Solde du mois : revenus encaissés moins dépenses engagées. */
    fun balanceOfMonth(data: BudgetData, month: YearMonth): Long =
        totalIncome(incomesOfMonth(data.incomes, month)) - total(expensesOfMonth(data.expenses, month))

    data class MonthTotal(
        val month: YearMonth,
        val expenseMillimes: Long,
        val incomeMillimes: Long
    ) {
        val netMillimes: Long get() = incomeMillimes - expenseMillimes
    }

    /** Totaux mensuels sur les [count] derniers mois, du plus ancien au plus récent. */
    fun monthlyHistory(data: BudgetData, endMonth: YearMonth, count: Int): List<MonthTotal> {
        val result = ArrayList<MonthTotal>()
        for (i in count - 1 downTo 0) {
            val month = endMonth.minusMonths(i.toLong())
            result.add(
                MonthTotal(
                    month = month,
                    expenseMillimes = total(expensesOfMonth(data.expenses, month)),
                    incomeMillimes = totalIncome(incomesOfMonth(data.incomes, month))
                )
            )
        }
        return result
    }

    // ------------------------------------------------------------ prévisions

    data class MonthForecast(
        val month: YearMonth,
        val recurringExpenseMillimes: Long,
        val eventsMillimes: Long,
        val expectedIncomeMillimes: Long,
        val perCategoryMillimes: Map<String, Long>
    ) {
        val totalExpenseMillimes: Long get() = recurringExpenseMillimes + eventsMillimes
        val netMillimes: Long get() = expectedIncomeMillimes - totalExpenseMillimes
    }

    /**
     * Projette le budget sur [horizonMonths] mois à partir du mois suivant [from].
     *
     * Méthode : moyennes par catégorie (dépenses) et globale (revenus) sur les
     * [lookbackMonths] derniers mois **complets** — le mois en cours, partiel,
     * est exclu pour ne pas fausser la tendance — auxquelles s'ajoutent les
     * échéances d'événements porteurs d'un montant.
     */
    fun forecast(
        data: BudgetData,
        from: LocalDate,
        horizonMonths: Int = 6,
        lookbackMonths: Int = 3
    ): List<MonthForecast> {
        val currentMonth = monthOf(from)
        val lastCompleteMonth = currentMonth.minusMonths(1)

        // Moyenne des dépenses par catégorie sur les mois complets précédents.
        val sums = LinkedHashMap<String, Long>()
        var monthsWithExpenses = 0
        var incomeSum = 0L
        var monthsWithIncome = 0

        for (i in 0 until lookbackMonths) {
            val month = lastCompleteMonth.minusMonths(i.toLong())

            val monthExpenses = expensesOfMonth(data.expenses, month)
            if (monthExpenses.isNotEmpty()) {
                monthsWithExpenses++
                for ((code, amount) in totalsByCategory(monthExpenses)) {
                    sums[code] = (sums[code] ?: 0L) + amount
                }
            }

            val monthIncomes = incomesOfMonth(data.incomes, month)
            if (monthIncomes.isNotEmpty()) {
                monthsWithIncome++
                incomeSum += totalIncome(monthIncomes)
            }
        }

        val averages = LinkedHashMap<String, Long>()
        if (monthsWithExpenses > 0) {
            for ((code, sum) in sums) {
                averages[code] = sum / monthsWithExpenses
            }
        }
        var recurringBase = 0L
        for (value in averages.values) recurringBase += value

        val expectedIncome = if (monthsWithIncome > 0) incomeSum / monthsWithIncome else 0L

        val forecasts = ArrayList<MonthForecast>()
        for (i in 1..horizonMonths) {
            val month = currentMonth.plusMonths(i.toLong())
            val monthStart = month.atDay(1)
            val monthEnd = month.atEndOfMonth()

            var eventsMillimes = 0L
            for (event in data.events) {
                val amount = event.amountMillimes ?: continue
                val occurrences = occurrencesBetween(event, monthStart, monthEnd)
                if (occurrences.isEmpty()) continue
                eventsMillimes += amount * occurrences.size
            }

            val perCategory = LinkedHashMap<String, Long>()
            perCategory.putAll(averages)

            forecasts.add(
                MonthForecast(
                    month = month,
                    recurringExpenseMillimes = recurringBase,
                    eventsMillimes = eventsMillimes,
                    expectedIncomeMillimes = expectedIncome,
                    perCategoryMillimes = perCategory
                )
            )
        }
        return forecasts
    }
}
