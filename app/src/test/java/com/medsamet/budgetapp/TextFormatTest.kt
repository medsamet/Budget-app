package com.medsamet.budgetapp

import com.medsamet.budgetapp.domain.BudgetData
import com.medsamet.budgetapp.domain.Category
import com.medsamet.budgetapp.domain.EventItem
import com.medsamet.budgetapp.domain.EventKind
import com.medsamet.budgetapp.domain.Expense
import com.medsamet.budgetapp.domain.Money
import com.medsamet.budgetapp.domain.Recurrence
import com.medsamet.budgetapp.domain.TextFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TextFormatTest {

    private fun sampleData(): BudgetData = BudgetData(
        categories = listOf(
            Category(code = "alimentation", name = "Alimentation", colorHex = "#E4A11B", monthlyBudgetCents = 40000L),
            Category(code = "loisirs", name = "Loisirs", colorHex = "#4C956C", monthlyBudgetCents = null)
        ),
        expenses = listOf(
            Expense(
                date = LocalDate.of(2026, 8, 17),
                amountCents = 4250L,
                categoryCode = "alimentation",
                label = "Courses",
                method = "CB",
                notes = ""
            ),
            Expense(
                date = LocalDate.of(2026, 8, 18),
                amountCents = 1200L,
                categoryCode = "loisirs",
                label = "Cinéma",
                method = "",
                notes = "séance de 20h"
            )
        ),
        events = listOf(
            EventItem(
                date = LocalDate.of(2026, 9, 12),
                title = "Anniversaire Lina",
                kind = EventKind.ANNIVERSAIRE,
                recurrence = Recurrence.ANNUELLE,
                reminderDays = 14,
                amountCents = 5000L,
                notes = "prévoir le gâteau",
                lastCompleted = null
            ),
            EventItem(
                date = LocalDate.of(2026, 11, 3),
                title = "Renouvellement licence",
                kind = EventKind.LICENCE,
                recurrence = Recurrence.ANNUELLE,
                reminderDays = 30,
                amountCents = 8990L,
                notes = "",
                lastCompleted = LocalDate.of(2025, 11, 3)
            )
        )
    )

    // ------------------------------------------------------------ montants

    @Test
    fun formatsCentsWithTwoDecimals() {
        assertEquals("42.50", Money.format(4250L))
        assertEquals("0.05", Money.format(5L))
        assertEquals("1200.00", Money.format(120000L))
        assertEquals("-12.30", Money.format(-1230L))
    }

    @Test
    fun parsesTheAmountFormatsAUserMightType() {
        assertEquals(4250L, Money.parseToCents("42.50"))
        assertEquals(4250L, Money.parseToCents("42,50"))
        assertEquals(4200L, Money.parseToCents("42"))
        assertEquals(123456L, Money.parseToCents("1 234,56"))
        assertEquals(1250L, Money.parseToCents("12,50 €"))
        assertEquals(-1230L, Money.parseToCents("-12.30"))
        assertNull(Money.parseToCents(""))
        assertNull(Money.parseToCents("abc"))
        assertNull(Money.parseToCents("1.2.3"))
    }

    @Test
    fun parsesAmountsSeparatedByNonBreakingSpaces() {
        // Char(160) = espace insécable, Char(8239) = espace fine insécable :
        // ce que produisent les claviers et les copier-coller de sites bancaires.
        assertEquals(123456L, Money.parseToCents("1" + Char(160) + "234,56"))
        assertEquals(123456L, Money.parseToCents("1" + Char(8239) + "234,56"))
        assertEquals(123456L, Money.parseToCents("1" + Char(160) + "234,56 " + Char(8364)))
    }

    @Test
    fun amountRoundTripIsStable() {
        val values = listOf(0L, 1L, 99L, 100L, 4250L, 999999L, -4250L)
        for (cents in values) {
            assertEquals(cents, Money.parseToCents(Money.format(cents)))
        }
    }

    // ------------------------------------------------------- aller-retour

    @Test
    fun exportThenImportRestoresTheSameData() {
        val original = sampleData()
        val text = TextFormat.export(original, LocalDate.of(2026, 8, 18))
        val report = TextFormat.parse(text)

        assertTrue("erreurs inattendues : " + report.errors, report.errors.isEmpty())

        assertEquals(
            original.categories.sortedBy { it.code },
            report.data.categories.sortedBy { it.code }
        )
        assertEquals(
            original.expenses.sortedBy { it.date },
            report.data.expenses.sortedBy { it.date }
        )
        assertEquals(
            original.events.sortedBy { it.date },
            report.data.events.sortedBy { it.date }
        )
    }

    @Test
    fun exportThenImportIsIdempotent() {
        val original = sampleData()
        val first = TextFormat.export(original, LocalDate.of(2026, 8, 18))
        val reparsed = TextFormat.parse(first).data
        val second = TextFormat.export(reparsed, LocalDate.of(2026, 8, 18))
        assertEquals(first, second)
    }

    // ------------------------------------------------------- échappement

    @Test
    fun preservesSeparatorsAndNewlinesInsideText() {
        val data = BudgetData(
            categories = listOf(Category(code = "divers", name = "Divers")),
            expenses = listOf(
                Expense(
                    date = LocalDate.of(2026, 1, 5),
                    amountCents = 1000L,
                    categoryCode = "divers",
                    label = "Achat A | B",
                    notes = "ligne 1\nligne 2"
                )
            )
        )
        val report = TextFormat.parse(TextFormat.export(data, LocalDate.of(2026, 1, 5)))
        assertTrue(report.errors.isEmpty())
        assertEquals(1, report.data.expenses.size)
        assertEquals("Achat A | B", report.data.expenses[0].label)
        assertEquals("ligne 1\nligne 2", report.data.expenses[0].notes)
    }

    @Test
    fun preservesBackslashes() {
        val data = BudgetData(
            categories = listOf(Category(code = "divers", name = "Divers")),
            expenses = listOf(
                Expense(
                    date = LocalDate.of(2026, 1, 5),
                    amountCents = 1000L,
                    categoryCode = "divers",
                    label = "C:\\chemin\\fichier"
                )
            )
        )
        val report = TextFormat.parse(TextFormat.export(data, LocalDate.of(2026, 1, 5)))
        assertEquals("C:\\chemin\\fichier", report.data.expenses[0].label)
    }

    // ------------------------------------------------------------ robustesse

    @Test
    fun keepsValidLinesAndReportsFaultyOnes() {
        val text = """
            # BudgetApp export v1

            [CATEGORIES]
            alimentation | Alimentation | #E4A11B |

            [DEPENSES]
            2026-08-17 | 42.50 | alimentation | Courses | CB |
            pas-une-date | 10.00 | alimentation | Erreur | |
            2026-08-19 | montant-invalide | alimentation | Erreur | |
            2026-08-20 | 7.30 | alimentation | Pain | espèces |
        """.trimIndent()

        val report = TextFormat.parse(text)
        assertEquals(2, report.data.expenses.size)
        assertEquals(2, report.errors.size)
        assertTrue(report.errors[0].contains("date"))
        assertTrue(report.errors[1].contains("montant"))
    }

    @Test
    fun createsMissingCategoriesRatherThanLosingExpenses() {
        val text = """
            [DEPENSES]
            2026-08-17 | 42.50 | inconnue | Courses | |
        """.trimIndent()

        val report = TextFormat.parse(text)
        assertEquals(1, report.data.expenses.size)
        assertEquals(1, report.data.categories.size)
        assertEquals("inconnue", report.data.categories[0].code)
        assertTrue(report.warnings.isNotEmpty())
    }

    @Test
    fun acceptsAlternativeDateFormats() {
        assertEquals(LocalDate.of(2026, 8, 17), TextFormat.parseDate("2026-08-17"))
        assertEquals(LocalDate.of(2026, 8, 17), TextFormat.parseDate("17/08/2026"))
        assertEquals(LocalDate.of(2026, 8, 17), TextFormat.parseDate("17.08.2026"))
        assertNull(TextFormat.parseDate("32/13/2026"))
        assertNull(TextFormat.parseDate(""))
    }

    @Test
    fun ignoresCommentsBlankLinesAndUnknownSections() {
        val text = """
            # un commentaire

            [INCONNUE]
            n'importe quoi | vraiment

            [DEPENSES]
            # date | montant | categorie
            2026-08-17 | 42.50 | divers | Test | |
        """.trimIndent()

        val report = TextFormat.parse(text)
        assertEquals(1, report.data.expenses.size)
        assertTrue(report.errors.isEmpty())
    }

    @Test
    fun emptyInputProducesEmptyReportWithoutCrashing() {
        val report = TextFormat.parse("")
        assertTrue(report.isEmpty)
        assertTrue(report.errors.isEmpty())
    }
}
