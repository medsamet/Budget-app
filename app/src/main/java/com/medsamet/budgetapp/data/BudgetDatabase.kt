package com.medsamet.budgetapp.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.medsamet.budgetapp.domain.BudgetData
import com.medsamet.budgetapp.domain.Category
import com.medsamet.budgetapp.domain.EventItem
import com.medsamet.budgetapp.domain.EventKind
import com.medsamet.budgetapp.domain.Expense
import com.medsamet.budgetapp.domain.Income
import com.medsamet.budgetapp.domain.IncomeSource
import com.medsamet.budgetapp.domain.Recurrence
import java.time.LocalDate

/**
 * Persistance SQLite écrite à la main.
 *
 * Choix délibéré : pas de Room ni d'annotation processing, afin de réduire
 * au minimum les dépendances de compilation (le projet est compilé
 * exclusivement par GitHub Actions).
 *
 * Schéma version 2 : montants en millimes, tables des sources et des revenus.
 */
class BudgetDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE categories (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                color TEXT NOT NULL,
                budget INTEGER
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE sources (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                code TEXT NOT NULL UNIQUE,
                name TEXT NOT NULL,
                color TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE expenses (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                amount INTEGER NOT NULL,
                category TEXT NOT NULL,
                label TEXT NOT NULL DEFAULT '',
                method TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_expenses_date ON expenses(date)")
        db.execSQL(
            """
            CREATE TABLE incomes (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                amount INTEGER NOT NULL,
                source TEXT NOT NULL,
                label TEXT NOT NULL DEFAULT '',
                method TEXT NOT NULL DEFAULT '',
                notes TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_incomes_date ON incomes(date)")
        db.execSQL(
            """
            CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                date TEXT NOT NULL,
                title TEXT NOT NULL,
                kind TEXT NOT NULL,
                recurrence TEXT NOT NULL,
                reminder_days INTEGER NOT NULL DEFAULT 7,
                amount INTEGER,
                notes TEXT NOT NULL DEFAULT '',
                last_completed TEXT
            )
            """.trimIndent()
        )
        seedDefaultCategories(db)
        seedDefaultSources(db)
    }

    /**
     * Version 1 -> 2 : les montants étaient stockés en centimes d'euro.
     * Ils sont multipliés par 10 pour devenir des millimes, ce qui conserve
     * la valeur numérique affichée (42,50 devient 42,500).
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("UPDATE expenses SET amount = amount * 10")
            db.execSQL("UPDATE categories SET budget = budget * 10 WHERE budget IS NOT NULL")
            db.execSQL("UPDATE events SET amount = amount * 10 WHERE amount IS NOT NULL")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sources (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    code TEXT NOT NULL UNIQUE,
                    name TEXT NOT NULL,
                    color TEXT NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS incomes (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    date TEXT NOT NULL,
                    amount INTEGER NOT NULL,
                    source TEXT NOT NULL,
                    label TEXT NOT NULL DEFAULT '',
                    method TEXT NOT NULL DEFAULT '',
                    notes TEXT NOT NULL DEFAULT ''
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_incomes_date ON incomes(date)")
            seedDefaultSources(db)
        }
    }

    private fun seedDefaultCategories(db: SQLiteDatabase) {
        val defaults = listOf(
            Triple("alimentation", "Alimentation", "#E4A11B"),
            Triple("logement", "Logement", "#2E7D8F"),
            Triple("transport", "Transport", "#7C6BB0"),
            Triple("sante", "Santé", "#C1666B"),
            Triple("loisirs", "Loisirs", "#4C956C"),
            Triple("abonnements", "Abonnements", "#8A6552"),
            Triple("divers", "Divers", "#7A7A7A")
        )
        for ((code, name, color) in defaults) {
            val values = ContentValues()
            values.put("code", code)
            values.put("name", name)
            values.put("color", color)
            db.insertWithOnConflict("categories", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    private fun seedDefaultSources(db: SQLiteDatabase) {
        val defaults = listOf(
            Triple("salaire", "Salaire", "#2E7D8F"),
            Triple("prime", "Prime", "#4C956C"),
            Triple("vente", "Vente", "#8A6552"),
            Triple("emprunt", "Emprunt", "#C1666B"),
            Triple("aide", "Aide / don", "#7C6BB0"),
            Triple("autre", "Autre", "#7A7A7A")
        )
        for ((code, name, color) in defaults) {
            val values = ContentValues()
            values.put("code", code)
            values.put("name", name)
            values.put("color", color)
            db.insertWithOnConflict("sources", null, values, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }

    companion object {
        private const val DB_NAME = "budget.db"
        private const val DB_VERSION = 2
    }
}

/** Accès aux données. Toutes les méthodes sont bloquantes : à appeler hors du thread principal. */
class BudgetRepository(context: Context) {

    private val helper = BudgetDatabase(context.applicationContext)

    // ------------------------------------------------------------ lecture

    fun loadAll(): BudgetData = BudgetData(
        categories = loadCategories(),
        sources = loadSources(),
        expenses = loadExpenses(),
        incomes = loadIncomes(),
        events = loadEvents()
    )

    fun loadCategories(): List<Category> {
        val result = ArrayList<Category>()
        helper.readableDatabase.query(
            "categories", null, null, null, null, null, "name ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    Category(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        code = cursor.getString(cursor.getColumnIndexOrThrow("code")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        colorHex = cursor.getString(cursor.getColumnIndexOrThrow("color")),
                        monthlyBudgetMillimes = cursor.getLongOrNull("budget")
                    )
                )
            }
        }
        return result
    }

    fun loadSources(): List<IncomeSource> {
        val result = ArrayList<IncomeSource>()
        helper.readableDatabase.query(
            "sources", null, null, null, null, null, "name ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(
                    IncomeSource(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        code = cursor.getString(cursor.getColumnIndexOrThrow("code")),
                        name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                        colorHex = cursor.getString(cursor.getColumnIndexOrThrow("color"))
                    )
                )
            }
        }
        return result
    }

    fun loadExpenses(): List<Expense> {
        val result = ArrayList<Expense>()
        helper.readableDatabase.query(
            "expenses", null, null, null, null, null, "date DESC, id DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val date = parseDateOrNull(cursor.getString(cursor.getColumnIndexOrThrow("date")))
                    ?: continue
                result.add(
                    Expense(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        date = date,
                        amountMillimes = cursor.getLong(cursor.getColumnIndexOrThrow("amount")),
                        categoryCode = cursor.getString(cursor.getColumnIndexOrThrow("category")),
                        label = cursor.getString(cursor.getColumnIndexOrThrow("label")),
                        method = cursor.getString(cursor.getColumnIndexOrThrow("method")),
                        notes = cursor.getString(cursor.getColumnIndexOrThrow("notes"))
                    )
                )
            }
        }
        return result
    }

    fun loadIncomes(): List<Income> {
        val result = ArrayList<Income>()
        helper.readableDatabase.query(
            "incomes", null, null, null, null, null, "date DESC, id DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val date = parseDateOrNull(cursor.getString(cursor.getColumnIndexOrThrow("date")))
                    ?: continue
                result.add(
                    Income(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        date = date,
                        amountMillimes = cursor.getLong(cursor.getColumnIndexOrThrow("amount")),
                        sourceCode = cursor.getString(cursor.getColumnIndexOrThrow("source")),
                        label = cursor.getString(cursor.getColumnIndexOrThrow("label")),
                        method = cursor.getString(cursor.getColumnIndexOrThrow("method")),
                        notes = cursor.getString(cursor.getColumnIndexOrThrow("notes"))
                    )
                )
            }
        }
        return result
    }

    fun loadEvents(): List<EventItem> {
        val result = ArrayList<EventItem>()
        helper.readableDatabase.query(
            "events", null, null, null, null, null, "date ASC, id ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val date = parseDateOrNull(cursor.getString(cursor.getColumnIndexOrThrow("date")))
                    ?: continue
                result.add(
                    EventItem(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        date = date,
                        title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                        kind = EventKind.fromCode(cursor.getString(cursor.getColumnIndexOrThrow("kind"))),
                        recurrence = Recurrence.fromCode(
                            cursor.getString(cursor.getColumnIndexOrThrow("recurrence"))
                        ),
                        reminderDays = cursor.getInt(cursor.getColumnIndexOrThrow("reminder_days")),
                        amountMillimes = cursor.getLongOrNull("amount"),
                        notes = cursor.getString(cursor.getColumnIndexOrThrow("notes")),
                        lastCompleted = cursor.getStringOrNull("last_completed")
                            ?.let { parseDateOrNull(it) }
                    )
                )
            }
        }
        return result
    }

    // ------------------------------------------------------------ écriture

    fun upsertCategory(category: Category): Long {
        val values = ContentValues()
        values.put("code", category.code)
        values.put("name", category.name)
        values.put("color", category.colorHex)
        val budget = category.monthlyBudgetMillimes
        if (budget != null) values.put("budget", budget) else values.putNull("budget")
        val db = helper.writableDatabase
        return if (category.id > 0L) {
            db.update("categories", values, "id = ?", arrayOf(category.id.toString()))
            category.id
        } else {
            db.insertWithOnConflict("categories", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun deleteCategory(id: Long) {
        helper.writableDatabase.delete("categories", "id = ?", arrayOf(id.toString()))
    }

    fun upsertSource(source: IncomeSource): Long {
        val values = ContentValues()
        values.put("code", source.code)
        values.put("name", source.name)
        values.put("color", source.colorHex)
        val db = helper.writableDatabase
        return if (source.id > 0L) {
            db.update("sources", values, "id = ?", arrayOf(source.id.toString()))
            source.id
        } else {
            db.insertWithOnConflict("sources", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    fun deleteSource(id: Long) {
        helper.writableDatabase.delete("sources", "id = ?", arrayOf(id.toString()))
    }

    fun upsertExpense(expense: Expense): Long {
        val values = ContentValues()
        values.put("date", expense.date.toString())
        values.put("amount", expense.amountMillimes)
        values.put("category", expense.categoryCode)
        values.put("label", expense.label)
        values.put("method", expense.method)
        values.put("notes", expense.notes)
        val db = helper.writableDatabase
        return if (expense.id > 0L) {
            db.update("expenses", values, "id = ?", arrayOf(expense.id.toString()))
            expense.id
        } else {
            db.insert("expenses", null, values)
        }
    }

    fun deleteExpense(id: Long) {
        helper.writableDatabase.delete("expenses", "id = ?", arrayOf(id.toString()))
    }

    fun upsertIncome(income: Income): Long {
        val values = ContentValues()
        values.put("date", income.date.toString())
        values.put("amount", income.amountMillimes)
        values.put("source", income.sourceCode)
        values.put("label", income.label)
        values.put("method", income.method)
        values.put("notes", income.notes)
        val db = helper.writableDatabase
        return if (income.id > 0L) {
            db.update("incomes", values, "id = ?", arrayOf(income.id.toString()))
            income.id
        } else {
            db.insert("incomes", null, values)
        }
    }

    fun deleteIncome(id: Long) {
        helper.writableDatabase.delete("incomes", "id = ?", arrayOf(id.toString()))
    }

    fun upsertEvent(event: EventItem): Long {
        val values = ContentValues()
        values.put("date", event.date.toString())
        values.put("title", event.title)
        values.put("kind", event.kind.code)
        values.put("recurrence", event.recurrence.code)
        values.put("reminder_days", event.reminderDays)
        val eventAmount = event.amountMillimes
        if (eventAmount != null) values.put("amount", eventAmount) else values.putNull("amount")
        values.put("notes", event.notes)
        val lastCompleted = event.lastCompleted
        if (lastCompleted != null) {
            values.put("last_completed", lastCompleted.toString())
        } else {
            values.putNull("last_completed")
        }
        val db = helper.writableDatabase
        return if (event.id > 0L) {
            db.update("events", values, "id = ?", arrayOf(event.id.toString()))
            event.id
        } else {
            db.insert("events", null, values)
        }
    }

    fun deleteEvent(id: Long) {
        helper.writableDatabase.delete("events", "id = ?", arrayOf(id.toString()))
    }

    // ------------------------------------------------------------ import

    /** Remplace intégralement le contenu de la base par [data]. */
    fun replaceAll(data: BudgetData) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.delete("expenses", null, null)
            db.delete("incomes", null, null)
            db.delete("events", null, null)
            db.delete("categories", null, null)
            db.delete("sources", null, null)
            writeAll(db, data)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Ajoute [data] aux données existantes, sans supprimer ce qui est déjà là. */
    fun mergeAll(data: BudgetData) {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            writeAll(db, data)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun writeAll(db: SQLiteDatabase, data: BudgetData) {
        for (category in data.categories) {
            val values = ContentValues()
            values.put("code", category.code)
            values.put("name", category.name)
            values.put("color", category.colorHex)
            val budget = category.monthlyBudgetMillimes
            if (budget != null) values.put("budget", budget) else values.putNull("budget")
            db.insertWithOnConflict("categories", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        for (source in data.sources) {
            val values = ContentValues()
            values.put("code", source.code)
            values.put("name", source.name)
            values.put("color", source.colorHex)
            db.insertWithOnConflict("sources", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        }
        for (expense in data.expenses) {
            val values = ContentValues()
            values.put("date", expense.date.toString())
            values.put("amount", expense.amountMillimes)
            values.put("category", expense.categoryCode)
            values.put("label", expense.label)
            values.put("method", expense.method)
            values.put("notes", expense.notes)
            db.insert("expenses", null, values)
        }
        for (income in data.incomes) {
            val values = ContentValues()
            values.put("date", income.date.toString())
            values.put("amount", income.amountMillimes)
            values.put("source", income.sourceCode)
            values.put("label", income.label)
            values.put("method", income.method)
            values.put("notes", income.notes)
            db.insert("incomes", null, values)
        }
        for (event in data.events) {
            val values = ContentValues()
            values.put("date", event.date.toString())
            values.put("title", event.title)
            values.put("kind", event.kind.code)
            values.put("recurrence", event.recurrence.code)
            values.put("reminder_days", event.reminderDays)
            val eventAmount = event.amountMillimes
            if (eventAmount != null) values.put("amount", eventAmount) else values.putNull("amount")
            values.put("notes", event.notes)
            val lastCompleted = event.lastCompleted
            if (lastCompleted != null) {
                values.put("last_completed", lastCompleted.toString())
            } else {
                values.putNull("last_completed")
            }
            db.insert("events", null, values)
        }
    }
}

private fun parseDateOrNull(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return try {
        LocalDate.parse(value)
    } catch (_: Exception) {
        null
    }
}

private fun Cursor.getLongOrNull(columnName: String): Long? {
    val index = getColumnIndex(columnName)
    if (index < 0 || isNull(index)) return null
    return getLong(index)
}

private fun Cursor.getStringOrNull(columnName: String): String? {
    val index = getColumnIndex(columnName)
    if (index < 0 || isNull(index)) return null
    return getString(index)
}
