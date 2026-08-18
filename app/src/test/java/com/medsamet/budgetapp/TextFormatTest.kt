package com.medsamet.budgetapp

import com.medsamet.budgetapp.domain.BudgetData
import com.medsamet.budgetapp.domain.Category
import com.medsamet.budgetapp.domain.EventItem
import com.medsamet.budgetapp.domain.EventKind
import com.medsamet.budgetapp.domain.Expense
import com.medsamet.budgetapp.domain.Income
import com.medsamet.budgetapp.domain.IncomeSource
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
            Category(code = "alimentation", name = "Alimentation", colorHex = "#E4A11B", monthlyBudgetMillimes = 400000L),
            Category(code = "loisirs", name = "Loisirs", colorHex = "#4C956C", monthlyBudgetMillimes = null)
        ),
        sources = listOf(
            IncomeSource(code = "salaire", name = "Salaire", colorHex = "#2E7D8F"),
            IncomeSource(code = "vente", name = "Vente", colorHex = "#8A6552")
        ),
        expenses = listOf(
            Expense(
                date = LocalDate.of(2026, 8, 17),
                amountMillimes = 42500L,
                categoryCode = "alimentation",
                label = "Courses",
                method = "Carte",
                notes = ""
            ),
            Expense(
                date = LocalDate.of(2026, 8, 18),
                amountMillimes = 12000L,
                categoryCode = "loisirs",
                label = "Cinéma",
                method = "",
                notes = "séance de 20h"
            )
        ),
        incomes = listOf(
            Income(
                date = LocalDate.of(2026, 8, 1),
                amountMillimes = 2450000L,
                sourceCode = "salaire",
                label = "Salaire août",
                method = "Virement",
                notes = ""
            ),
            Income(
                date = LocalDate.of(2026, 8, 12),
                amountMillimes = 380500L,
                sourceCode = "vente",
                label = "Vente ancien téléphone",
                method = "Espèces",
                notes = "à un collègue"
            )
        ),
        events = listOf(
            EventItem(
                date = LocalDate.of(2026, 9, 12),
                title = "Anniversaire Lina",
                kind = EventKind.ANNIVERSAIRE,
                recurrence = Recurrence.ANNUELLE,
                reminderDays = 14,
                amountMillimes = 50000L,
                notes = "prévoir le gâteau",
                lastCompleted = null
            ),
            EventItem(
                date = LocalDate.of(2026, 11, 3),
                title = "Renouvellement licence",
                kind = EventKind.LICENCE,
                recurrence = Recurrence.ANNUELLE,
                reminderDays = 30,
                amountMillimes = 89900L,
                notes = "",
                lastCompleted = LocalDate.of(2025, 11, 3)
            )
        )
    )

    // ------------------------------------------------------------ montants

    @Test
    fun formatsMillimesWithThreeDecimals() {
        assertEquals("42.500", Money.format(42500L))
        assertEquals("0.005", Money.format(5L))
        assertEquals("0.750", Money.format(750L))
        assertEquals("1200.000", Money.format(1200000L))
        assertEquals("-12.300", Money.format(-12300L))
    }

    @Test
    fun displaysAmountsInDinars() {
        assertEquals("42,500 DT", Money.display(42500L))
        assertEquals("0,750 DT", Money.display(750L))
        assertEquals("+2450,000 DT", Money.displaySigned(2450000L))
        assertEquals("-120,000 DT", Money.displaySigned(-120000L))
    }

    @Test
    fun parsesTheAmountFormatsAUserMightType() {
        assertEquals(42500L, Money.parse("42.500"))
        assertEquals(42500L, Money.parse("42,5"))
        assertEquals(42000L, Money.parse("42"))
        assertEquals(750L, Money.parse("0,750"))
        assertEquals(1234750L, Money.parse("1 234,750"))
        assertEquals(12500L, Money.parse("12,500 DT"))
        assertEquals(-12300L, Money.parse("-12.300"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1.2.3"))
    }

    @Test
    fun parsesAmountsSeparatedByNonBreakingSpaces() {
        // Char(160) = espace insécable, Char(8239) = espace fine insécable :
        // ce que produisent les claviers et les copier-coller de sites bancaires.
        assertEquals(1234560L, Money.parse("1" + Char(160) + "234,56"))
        assertEquals(1234560L, Money.parse("1" + Char(8239) + "234,56"))
    }

    @Test
    fun truncatesBeyondThreeDecimals() {
        assertEquals(1234L, Money.parse("1,2345"))
        assertEquals(1200L, Money.parse("1,2"))
    }

    @Test
    fun amountRoundTripIsStable() {
        val values = listOf(0L, 1L, 999L, 1000L, 42500L, 2450000L, -42500L)
        for (millimes in values) {
            assertEquals(millimes, Money.parse(Money.format(millimes)))
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
            original.sources.sortedBy { it.code },
            report.data.sources.sortedBy { it.code }
        )
        assertEquals(
            original.expenses.sortedBy { it.date },
            report.data.expenses.sortedBy { it.date }
        )
        assertEquals(
            original.incomes.sortedBy { it.date },
            report.data.incomes.sortedBy { it.date }
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
                    amountMillimes = 1000L,
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
                    amountMillimes = 1000L,
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
            # BudgetApp export v2

            [CATEGORIES]
            alimentation | Alimentation | #E4A11B |

            [DEPENSES]
            2026-08-17 | 42.500 | alimentation | Courses | Carte |
            pas-une-date | 10.000 | alimentation | Erreur | |
            2026-08-19 | montant-invalide | alimentation | Erreur | |
            2026-08-20 | 7.300 | alimentation | Pain | Espèces |
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
            2026-08-17 | 42.500 | inconnue | Courses | |
        """.trimIndent()

        val report = TextFormat.parse(text)
        assertEquals(1, report.data.expenses.size)
        assertEquals(1, report.data.categories.size)
        assertEquals("inconnue", report.data.categories[0].code)
        assertTrue(report.warnings.isNotEmpty())
    }

    @Test
    fun createsMissingSourcesRatherThanLosingIncomes() {
        val text = """
            [REVENUS]
            2026-08-01 | 2450.000 | prime-exceptionnelle | Prime | Virement |
        """.trimIndent()

        val report = TextFormat.parse(text)
        assertEquals(1, report.data.incomes.size)
        assertEquals(1, report.data.sources.size)
        assertEquals("prime-exceptionnelle", report.data.sources[0].code)
        assertTrue(report.warnings.isNotEmpty())
    }

    @Test
    fun rejectsAnIncomeWithoutASource() {
        val text = """
            [REVENUS]
            2026-08-01 | 2450.000 | | Salaire | |
        """.trimIndent()

        val report = TextFormat.parse(text)
        assertEquals(0, report.data.incomes.size)
        assertEquals(1, report.errors.size)
        assertTrue(report.errors[0].contains("source"))
    }

    @Test
    fun readsVersionOneFilesWithoutIncomeSections() {
        val text = """
            # BudgetApp export v1

            [CATEGORIES]
            alimentation | Alimentation | #E4A11B | 400.00

            [DEPENSES]
            2026-08-17 | 42.50 | alimentation | Courses | Carte |
        """.trimIndent()

        val report = TextFormat.parse(text)
        assertTrue(report.errors.isEmpty())
        assertEquals(1, report.data.expenses.size)
        assertEquals(0, report.data.incomes.size)
        // « 42.50 » se lit comme 42 dinars et 500 millimes.
        assertEquals(42500L, report.data.expenses[0].amountMillimes)
        assertEquals(400000L, report.data.categories[0].monthlyBudgetMillimes)
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
            2026-08-17 | 42.500 | divers | Test | |
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
