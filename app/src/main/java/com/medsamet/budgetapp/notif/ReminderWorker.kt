package com.medsamet.budgetapp.notif

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.medsamet.budgetapp.data.BudgetRepository
import com.medsamet.budgetapp.domain.Money
import com.medsamet.budgetapp.domain.Stats
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Vérification quotidienne : notifie les événements dont la prochaine
 * occurrence entre dans leur fenêtre de rappel.
 */
class ReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    override fun doWork(): Result {
        return try {
            val today = LocalDate.now()
            val events = BudgetRepository(applicationContext).loadEvents()
            val due = Stats.dueEvents(events, today)

            val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
            for ((event, date) in due) {
                // On ne renotifie pas une occurrence déjà marquée comme traitée.
                val done = event.lastCompleted
                if (done != null && !done.isBefore(date)) continue

                val days = ChronoUnit.DAYS.between(today, date)
                val whenText = when {
                    days <= 0L -> "aujourd'hui"
                    days == 1L -> "demain"
                    else -> "dans $days jours"
                }
                val amount = event.amountCents
                val amountText = if (amount != null) " — ${Money.display(amount)}" else ""
                Reminders.notify(
                    context = applicationContext,
                    id = (event.id % Int.MAX_VALUE).toInt(),
                    title = event.title,
                    text = "${event.kind.label} $whenText (${date.format(formatter)})$amountText"
                )
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
