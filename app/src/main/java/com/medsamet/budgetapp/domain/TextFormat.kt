package com.medsamet.budgetapp.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Format d'échange texte de l'application (« BudgetApp export v2 »).
 *
 * Objectifs :
 *  - lisible et modifiable dans n'importe quel éditeur de texte ;
 *  - réimportable sans perte (aller-retour exact) ;
 *  - tolérant : les lignes vides, les commentaires (#) et les sections
 *    inconnues sont ignorés, et chaque ligne fautive est signalée avec
 *    son numéro plutôt que de faire échouer tout l'import.
 *
 * Structure :
 *
 *     [CATEGORIES]
 *     code | nom | couleur | budget_mensuel
 *
 *     [SOURCES]
 *     code | nom | couleur
 *
 *     [DEPENSES]
 *     date | montant | categorie | libelle | moyen | notes
 *
 *     [REVENUS]
 *     date | montant | source | libelle | moyen | notes
 *
 *     [EVENEMENTS]
 *     date | titre | type | recurrence | rappel_jours | montant | notes | derniere_occurrence
 *
 * Montants en dinars, séparateur décimal « . », trois décimales (millimes).
 *
 * Échappement à l'intérieur d'une cellule : `\|` pour une barre verticale,
 * `\n` pour un retour à la ligne, `\\` pour une barre oblique inverse.
 *
 * Les fichiers produits par la version 1 (sans sections [SOURCES] ni
 * [REVENUS]) restent lisibles : les sections absentes donnent simplement
 * des listes vides.
 */
object TextFormat {

    const val VERSION = 2
    const val HEADER = "# BudgetApp export v$VERSION"

    private const val SECTION_CATEGORIES = "CATEGORIES"
    private const val SECTION_SOURCES = "SOURCES"
    private const val SECTION_EXPENSES = "DEPENSES"
    private const val SECTION_INCOMES = "REVENUS"
    private const val SECTION_EVENTS = "EVENEMENTS"

    private val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    // ---------------------------------------------------------------- export

    fun export(data: BudgetData, generatedOn: LocalDate = LocalDate.now()): String {
        val sb = StringBuilder()

        sb.appendLine(HEADER)
        sb.appendLine("# Genere le ${generatedOn.format(ISO)}")
        sb.appendLine("# Colonnes separees par « | ». Lignes vides et lignes # ignorees a l'import.")
        sb.appendLine("# Echappement : \\| barre verticale, \\n retour a la ligne, \\\\ antislash.")
        sb.appendLine("# Montants en dinars (3 decimales, millimes). Dates au format AAAA-MM-JJ.")
        sb.appendLine()

        sb.appendLine("[$SECTION_CATEGORIES]")
        sb.appendLine("# code | nom | couleur | budget_mensuel")
        for (c in data.categories.sortedBy { it.code }) {
            sb.appendLine(
                row(
                    c.code,
                    c.name,
                    c.colorHex,
                    c.monthlyBudgetMillimes?.let { Money.format(it) } ?: ""
                )
            )
        }
        sb.appendLine()

        sb.appendLine("[$SECTION_SOURCES]")
        sb.appendLine("# code | nom | couleur")
        for (s in data.sources.sortedBy { it.code }) {
            sb.appendLine(row(s.code, s.name, s.colorHex))
        }
        sb.appendLine()

        sb.appendLine("[$SECTION_EXPENSES]")
        sb.appendLine("# date | montant | categorie | libelle | moyen | notes")
        for (e in data.expenses.sortedWith(compareBy({ it.date }, { it.id }))) {
            sb.appendLine(
                row(
                    e.date.format(ISO),
                    Money.format(e.amountMillimes),
                    e.categoryCode,
                    e.label,
                    e.method,
                    e.notes
                )
            )
        }
        sb.appendLine()

        sb.appendLine("[$SECTION_INCOMES]")
        sb.appendLine("# date | montant | source | libelle | moyen | notes")
        for (i in data.incomes.sortedWith(compareBy({ it.date }, { it.id }))) {
            sb.appendLine(
                row(
                    i.date.format(ISO),
                    Money.format(i.amountMillimes),
                    i.sourceCode,
                    i.label,
                    i.method,
                    i.notes
                )
            )
        }
        sb.appendLine()

        sb.appendLine("[$SECTION_EVENTS]")
        sb.appendLine("# date | titre | type | recurrence | rappel_jours | montant | notes | derniere_occurrence")
        for (ev in data.events.sortedWith(compareBy({ it.date }, { it.id }))) {
            sb.appendLine(
                row(
                    ev.date.format(ISO),
                    ev.title,
                    ev.kind.code,
                    ev.recurrence.code,
                    ev.reminderDays.toString(),
                    ev.amountMillimes?.let { Money.format(it) } ?: "",
                    ev.notes,
                    ev.lastCompleted?.format(ISO) ?: ""
                )
            )
        }
        sb.appendLine()

        sb.appendLine(
            "# Fin — ${data.categories.size} categories, ${data.sources.size} sources, " +
                "${data.expenses.size} depenses, ${data.incomes.size} revenus, " +
                "${data.events.size} evenements."
        )
        return sb.toString()
    }

    private fun row(vararg cells: String): String =
        cells.joinToString(" | ") { escape(it) }

    private fun escape(value: String): String =
        value
            .replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace("\r\n", "\\n")
            .replace("\n", "\\n")
            .replace("\r", "\\n")

    // ---------------------------------------------------------------- import

    data class ImportReport(
        val data: BudgetData,
        val errors: List<String> = emptyList(),
        val warnings: List<String> = emptyList()
    ) {
        val isEmpty: Boolean
            get() = data.categories.isEmpty() && data.sources.isEmpty() &&
                data.expenses.isEmpty() && data.incomes.isEmpty() && data.events.isEmpty()

        val summary: String
            get() = "${data.expenses.size} dépense(s), ${data.incomes.size} revenu(s), " +
                "${data.categories.size} catégorie(s), ${data.sources.size} source(s), " +
                "${data.events.size} événement(s)"
    }

    fun parse(text: String): ImportReport {
        val categories = ArrayList<Category>()
        val sources = ArrayList<IncomeSource>()
        val expenses = ArrayList<Expense>()
        val incomes = ArrayList<Income>()
        val events = ArrayList<EventItem>()
        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()

        var section = ""
        var lineNumber = 0

        for (rawLine in text.split("\n")) {
            lineNumber++
            val line = rawLine.trim().removeSuffix("\r")
            if (line.isEmpty() || line.startsWith("#")) continue

            if (line.startsWith("[") && line.endsWith("]")) {
                section = line.substring(1, line.length - 1).trim().uppercase()
                if (section != SECTION_CATEGORIES &&
                    section != SECTION_SOURCES &&
                    section != SECTION_EXPENSES &&
                    section != SECTION_INCOMES &&
                    section != SECTION_EVENTS
                ) {
                    warnings.add("Ligne $lineNumber : section inconnue « $section », ignorée.")
                }
                continue
            }

            val cells = splitCells(line)

            when (section) {
                SECTION_CATEGORIES -> parseCategory(cells, lineNumber, errors)?.let { categories.add(it) }
                SECTION_SOURCES -> parseSource(cells, lineNumber, errors)?.let { sources.add(it) }
                SECTION_EXPENSES -> parseExpense(cells, lineNumber, errors)?.let { expenses.add(it) }
                SECTION_INCOMES -> parseIncome(cells, lineNumber, errors)?.let { incomes.add(it) }
                SECTION_EVENTS -> parseEvent(cells, lineNumber, errors)?.let { events.add(it) }
                "" -> warnings.add("Ligne $lineNumber : donnée hors de toute section, ignorée.")
                else -> Unit // section inconnue déjà signalée
            }
        }

        // Catégories manquantes : on les crée à la volée pour ne perdre aucune dépense.
        val knownCategories = HashSet<String>()
        for (c in categories) knownCategories.add(c.code.lowercase())
        val missingCategories = LinkedHashSet<String>()
        for (e in expenses) {
            if (e.categoryCode.isNotEmpty() && !knownCategories.contains(e.categoryCode.lowercase())) {
                missingCategories.add(e.categoryCode)
            }
        }
        for (code in missingCategories) {
            categories.add(Category(code = code, name = code.replaceFirstChar { it.uppercase() }))
            warnings.add("Catégorie « $code » absente du fichier : elle a été créée automatiquement.")
        }

        // Même principe pour les sources de revenus.
        val knownSources = HashSet<String>()
        for (s in sources) knownSources.add(s.code.lowercase())
        val missingSources = LinkedHashSet<String>()
        for (i in incomes) {
            if (i.sourceCode.isNotEmpty() && !knownSources.contains(i.sourceCode.lowercase())) {
                missingSources.add(i.sourceCode)
            }
        }
        for (code in missingSources) {
            sources.add(IncomeSource(code = code, name = code.replaceFirstChar { it.uppercase() }))
            warnings.add("Source « $code » absente du fichier : elle a été créée automatiquement.")
        }

        return ImportReport(
            data = BudgetData(
                categories = categories,
                sources = sources,
                expenses = expenses,
                incomes = incomes,
                events = events
            ),
            errors = errors,
            warnings = warnings
        )
    }

    private fun parseCategory(cells: List<String>, line: Int, errors: MutableList<String>): Category? {
        if (cells.size < 2) {
            errors.add("Ligne $line : catégorie incomplète (attendu au moins « code | nom »).")
            return null
        }
        val code = cells[0].trim()
        if (code.isEmpty()) {
            errors.add("Ligne $line : code de catégorie vide.")
            return null
        }
        val color = cells.getOrElse(2) { "" }.trim().ifEmpty { "#7A7A7A" }
        val budgetText = cells.getOrElse(3) { "" }.trim()
        val budget = if (budgetText.isEmpty()) null else Money.parse(budgetText)
        if (budgetText.isNotEmpty() && budget == null) {
            errors.add("Ligne $line : budget mensuel illisible « $budgetText ».")
        }
        return Category(
            code = code,
            name = cells[1].trim().ifEmpty { code },
            colorHex = color,
            monthlyBudgetMillimes = budget
        )
    }

    private fun parseSource(cells: List<String>, line: Int, errors: MutableList<String>): IncomeSource? {
        if (cells.size < 2) {
            errors.add("Ligne $line : source incomplète (attendu au moins « code | nom »).")
            return null
        }
        val code = cells[0].trim()
        if (code.isEmpty()) {
            errors.add("Ligne $line : code de source vide.")
            return null
        }
        return IncomeSource(
            code = code,
            name = cells[1].trim().ifEmpty { code },
            colorHex = cells.getOrElse(2) { "" }.trim().ifEmpty { "#4C956C" }
        )
    }

    private fun parseExpense(cells: List<String>, line: Int, errors: MutableList<String>): Expense? {
        if (cells.size < 3) {
            errors.add("Ligne $line : dépense incomplète (attendu « date | montant | categorie »).")
            return null
        }
        val date = parseDate(cells[0])
        if (date == null) {
            errors.add("Ligne $line : date illisible « ${cells[0]} ».")
            return null
        }
        val amount = Money.parse(cells[1])
        if (amount == null) {
            errors.add("Ligne $line : montant illisible « ${cells[1]} ».")
            return null
        }
        return Expense(
            date = date,
            amountMillimes = amount,
            categoryCode = cells[2].trim(),
            label = cells.getOrElse(3) { "" }.trim(),
            method = cells.getOrElse(4) { "" }.trim(),
            notes = cells.getOrElse(5) { "" }.trim()
        )
    }

    private fun parseIncome(cells: List<String>, line: Int, errors: MutableList<String>): Income? {
        if (cells.size < 3) {
            errors.add("Ligne $line : revenu incomplet (attendu « date | montant | source »).")
            return null
        }
        val date = parseDate(cells[0])
        if (date == null) {
            errors.add("Ligne $line : date de revenu illisible « ${cells[0]} ».")
            return null
        }
        val amount = Money.parse(cells[1])
        if (amount == null) {
            errors.add("Ligne $line : montant de revenu illisible « ${cells[1]} ».")
            return null
        }
        val source = cells[2].trim()
        if (source.isEmpty()) {
            errors.add("Ligne $line : source de revenu vide.")
            return null
        }
        return Income(
            date = date,
            amountMillimes = amount,
            sourceCode = source,
            label = cells.getOrElse(3) { "" }.trim(),
            method = cells.getOrElse(4) { "" }.trim(),
            notes = cells.getOrElse(5) { "" }.trim()
        )
    }

    private fun parseEvent(cells: List<String>, line: Int, errors: MutableList<String>): EventItem? {
        if (cells.size < 2) {
            errors.add("Ligne $line : événement incomplet (attendu « date | titre »).")
            return null
        }
        val date = parseDate(cells[0])
        if (date == null) {
            errors.add("Ligne $line : date d'événement illisible « ${cells[0]} ».")
            return null
        }
        val title = cells[1].trim()
        if (title.isEmpty()) {
            errors.add("Ligne $line : titre d'événement vide.")
            return null
        }
        val reminderText = cells.getOrElse(4) { "" }.trim()
        val reminder = if (reminderText.isEmpty()) 7 else (reminderText.toIntOrNull() ?: 7)
        if (reminderText.isNotEmpty() && reminderText.toIntOrNull() == null) {
            errors.add("Ligne $line : délai de rappel illisible « $reminderText », 7 jours appliqués.")
        }
        val amountText = cells.getOrElse(5) { "" }.trim()
        val amount = if (amountText.isEmpty()) null else Money.parse(amountText)
        if (amountText.isNotEmpty() && amount == null) {
            errors.add("Ligne $line : montant d'événement illisible « $amountText ».")
        }
        val lastText = cells.getOrElse(7) { "" }.trim()
        val last = if (lastText.isEmpty()) null else parseDate(lastText)
        if (lastText.isNotEmpty() && last == null) {
            errors.add("Ligne $line : dernière occurrence illisible « $lastText ».")
        }
        return EventItem(
            date = date,
            title = title,
            kind = EventKind.fromCode(cells.getOrElse(2) { "" }),
            recurrence = Recurrence.fromCode(cells.getOrElse(3) { "" }),
            reminderDays = if (reminder < 0) 0 else reminder,
            amountMillimes = amount,
            notes = cells.getOrElse(6) { "" }.trim(),
            lastCompleted = last
        )
    }

    // --------------------------------------------------------------- helpers

    /** Découpe une ligne sur les « | » non échappés et applique l'échappement inverse. */
    internal fun splitCells(line: String): List<String> {
        val cells = ArrayList<String>()
        val current = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\\' && i + 1 < line.length) {
                when (line[i + 1]) {
                    '|' -> current.append('|')
                    'n' -> current.append('\n')
                    '\\' -> current.append('\\')
                    else -> {
                        current.append(c)
                        current.append(line[i + 1])
                    }
                }
                i += 2
            } else if (c == '|') {
                cells.add(current.toString().trim())
                current.setLength(0)
                i++
            } else {
                current.append(c)
                i++
            }
        }
        cells.add(current.toString().trim())
        return cells
    }

    /** Accepte AAAA-MM-JJ, JJ/MM/AAAA et JJ.MM.AAAA. */
    fun parseDate(raw: String): LocalDate? {
        val value = raw.trim()
        if (value.isEmpty()) return null
        try {
            return LocalDate.parse(value, ISO)
        } catch (_: Exception) {
            // on tente les autres formats ci-dessous
        }
        val separator = when {
            value.contains('/') -> '/'
            value.contains('.') -> '.'
            else -> return null
        }
        val parts = value.split(separator)
        if (parts.size != 3) return null
        val day = parts[0].trim().toIntOrNull() ?: return null
        val month = parts[1].trim().toIntOrNull() ?: return null
        val year = parts[2].trim().toIntOrNull() ?: return null
        return try {
            LocalDate.of(year, month, day)
        } catch (_: Exception) {
            null
        }
    }
}
