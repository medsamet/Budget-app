package com.medsamet.budgetapp.domain

import java.time.LocalDate

/**
 * Modèles de données de l'application.
 *
 * Tous les montants sont stockés en **millimes** (Long) : le dinar tunisien
 * se divise en 1 000 millimes, et l'usage d'entiers écarte définitivement
 * les erreurs d'arrondi des nombres à virgule flottante.
 */

data class Category(
    val id: Long = 0L,
    val code: String,
    val name: String,
    val colorHex: String = "#7A7A7A",
    val monthlyBudgetMillimes: Long? = null
)

data class Expense(
    val id: Long = 0L,
    val date: LocalDate,
    val amountMillimes: Long,
    val categoryCode: String,
    val label: String = "",
    val method: String = "",
    val notes: String = ""
)

/** Origine d'un revenu : salaire, prime, vente, emprunt… */
data class IncomeSource(
    val id: Long = 0L,
    val code: String,
    val name: String,
    val colorHex: String = "#4C956C"
)

data class Income(
    val id: Long = 0L,
    val date: LocalDate,
    val amountMillimes: Long,
    val sourceCode: String,
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
    BIMESTRIELLE("BIMESTRIELLE", "Tous les 2 mois"),
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
 * [amountMillimes] est optionnel : lorsqu'il est renseigné, l'événement
 * est intégré aux prévisions budgétaires.
 */
data class EventItem(
    val id: Long = 0L,
    val date: LocalDate,
    val title: String,
    val kind: EventKind = EventKind.AUTRE,
    val recurrence: Recurrence = Recurrence.AUCUNE,
    val reminderDays: Int = 7,
    val amountMillimes: Long? = null,
    val notes: String = "",
    val lastCompleted: LocalDate? = null
)

/** Instantané complet des données de l'application. */
data class BudgetData(
    val categories: List<Category> = emptyList(),
    val sources: List<IncomeSource> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val incomes: List<Income> = emptyList(),
    val events: List<EventItem> = emptyList()
)

object Money {

    /** Symbole affiché à l'écran. */
    const val SYMBOL = "DT"

    /** Nombre de millimes dans un dinar. */
    const val SCALE = 1000L

    /** 42500 -> "42.500" (séparateur point, sans symbole : forme utilisée à l'export). */
    fun format(millimes: Long): String {
        val sign = if (millimes < 0) "-" else ""
        val abs = if (millimes < 0) -millimes else millimes
        val units = abs / SCALE
        val frac = (abs % SCALE).toString().padStart(3, '0')
        return "$sign$units.$frac"
    }

    /** 42500 -> "42,500 DT" (affichage à l'écran). */
    fun display(millimes: Long): String = format(millimes).replace('.', ',') + " " + SYMBOL

    /** Solde : préfixe explicite du signe pour lever toute ambiguïté. */
    fun displaySigned(millimes: Long): String =
        if (millimes > 0L) "+" + display(millimes) else display(millimes)

    /**
     * Analyse un montant saisi ou importé.
     * Accepte "42.500", "42,5", "42", "1 234,750", "-12.300", "12,500 DT".
     * Renvoie null si la valeur est vide ou ne contient aucun chiffre.
     * Au-delà de trois décimales, les chiffres supplémentaires sont tronqués.
     */
    fun parse(raw: String): Long? {
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
        cleaned = cleaned.removePrefix("-")
        if (cleaned.isEmpty()) return null

        val parts = cleaned.split(".")
        if (parts.size > 2) return null

        val wholeText = if (parts[0].isEmpty()) "0" else parts[0]
        val whole = wholeText.toLongOrNull() ?: return null

        val fracText = if (parts.size == 2) parts[1].padEnd(3, '0').substring(0, 3) else "000"
        val frac = fracText.toLongOrNull() ?: return null

        val total = whole * SCALE + frac
        return if (negative) -total else total
    }
}
