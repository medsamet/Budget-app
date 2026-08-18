package com.medsamet.budgetapp.domain

import java.time.LocalDate

/**
 * Modèles de données de l'application.
 *
 * Tous les montants sont stockés en **centimes** (Long) afin d'éviter
 * les erreurs d'arrondi des nombres à virgule flottante.
 */

data class Category(
    val id: Long = 0L,
    val code: String,
    val name: String,
    val colorHex: String = "#7A7A7A",
    val monthlyBudgetCents: Long? = null
)

data class Expense(
    val id: Long = 0L,
    val date: LocalDate,
    val amountCents: Long,
    val categoryCode: String,
    val label: String = "",
    val method: String = "",
    val notes: String = ""
)

enum class EventKind(val code: String, val label: String) {
    ANNIVERSAIRE("ANNIVERSAIRE", "Anniversaire"),
    MAINTENANCE("MAINTENANCE", "Maintenance"),
    LICENCE("LICENCE", "Licence / abonnement"),
    ASSURANCE("ASSURANCE", "Assurance"),
    AUTRE("AUTRE", "Autre");

    companion object {
        fun fromCode(value: String): EventKind {
            val v = value.trim()
            for (kind in values()) {
                if (kind.code.equals(v, ignoreCase = true)) return kind
            }
            return AUTRE
        }
    }
}

enum class Recurrence(val code: String, val label: String) {
    AUCUNE("AUCUNE", "Aucune"),
    MENSUELLE("MENSUELLE", "Mensuelle"),
    TRIMESTRIELLE("TRIMESTRIELLE", "Trimestrielle"),
    SEMESTRIELLE("SEMESTRIELLE", "Semestrielle"),
    ANNUELLE("ANNUELLE", "Annuelle");

    companion object {
        fun fromCode(value: String): Recurrence {
            val v = value.trim()
            for (r in values()) {
                if (r.code.equals(v, ignoreCase = true)) return r
            }
            return AUCUNE
        }
    }
}

/**
 * Un événement daté : anniversaire, maintenance d'équipement,
 * renouvellement de licence, etc.
 *
 * [amountCents] est optionnel : lorsqu'il est renseigné, l'événement
 * est intégré aux prévisions budgétaires.
 */
data class EventItem(
    val id: Long = 0L,
    val date: LocalDate,
    val title: String,
    val kind: EventKind = EventKind.AUTRE,
    val recurrence: Recurrence = Recurrence.AUCUNE,
    val reminderDays: Int = 7,
    val amountCents: Long? = null,
    val notes: String = "",
    val lastCompleted: LocalDate? = null
)

/** Instantané complet des données de l'application. */
data class BudgetData(
    val categories: List<Category> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val events: List<EventItem> = emptyList()
)

object Money {

    /** 4250 -> "42.50" (séparateur point, sans symbole). */
    fun format(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = if (cents < 0) -cents else cents
        val units = abs / 100
        val frac = (abs % 100).toString().padStart(2, '0')
        return "$sign$units.$frac"
    }

    /** 4250 -> "42,50 €" (affichage à l'écran). */
    fun display(cents: Long): String = format(cents).replace('.', ',') + " €"

    /**
     * Analyse un montant saisi ou importé.
     * Accepte "42.50", "42,50", "42", "1 234,56", "-12.30", "12,50 €".
     * Renvoie null si la valeur est vide ou invalide.
     * Au-delà de deux décimales, les chiffres supplémentaires sont tronqués.
     */
    fun parseToCents(raw: String): Long? {
        // On ne conserve que ce qui a un sens numerique : tout le reste
        // (espaces ordinaires ou insecables, symboles monetaires, lettres)
        // est ignore. Evite toute sequence d'echappement dans le code.
        val builder = StringBuilder()
        for (c in raw.trim()) {
            when {
                c.isDigit() -> builder.append(c)
                c == '.' || c == ',' -> builder.append('.')
                c == '-' && builder.isEmpty() -> builder.append('-')
                else -> Unit
            }
        }
        var cleaned = builder.toString()
        if (cleaned.isEmpty()) return null

        val negative = cleaned.startsWith("-")
        cleaned = cleaned.removePrefix("-").removePrefix("+")
        if (cleaned.isEmpty()) return null

        for (c in cleaned) {
            if (!c.isDigit() && c != '.') return null
        }

        val parts = cleaned.split(".")
        if (parts.size > 2) return null

        val wholeText = if (parts[0].isEmpty()) "0" else parts[0]
        val whole = wholeText.toLongOrNull() ?: return null

        val fracText = if (parts.size == 2) parts[1].padEnd(2, '0').substring(0, 2) else "00"
        val frac = fracText.toLongOrNull() ?: return null

        val total = whole * 100L + frac
        return if (negative) -total else total
    }
}
