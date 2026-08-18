package com.medsamet.budgetapp.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.medsamet.budgetapp.data.BudgetRepository
import com.medsamet.budgetapp.domain.BudgetData
import com.medsamet.budgetapp.domain.Category
import com.medsamet.budgetapp.domain.EventItem
import com.medsamet.budgetapp.domain.Expense
import com.medsamet.budgetapp.domain.TextFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class BudgetViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = BudgetRepository(application)

    var data by mutableStateOf(BudgetData())
        private set

    var isLoading by mutableStateOf(true)
        private set

    /** Message transitoire affiché dans une snackbar. */
    var message by mutableStateOf<String?>(null)

    /** Rapport du dernier import, affiché en détail dans l'écran Données. */
    var lastImportReport by mutableStateOf<TextFormat.ImportReport?>(null)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            isLoading = true
            val loaded = withContext(Dispatchers.IO) { repository.loadAll() }
            data = loaded
            isLoading = false
        }
    }

    fun consumeMessage() {
        message = null
    }

    // ------------------------------------------------------------- dépenses

    fun saveExpense(expense: Expense) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.upsertExpense(expense) }
            message = if (expense.id > 0L) "Dépense modifiée" else "Dépense enregistrée"
            refresh()
        }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteExpense(id) }
            message = "Dépense supprimée"
            refresh()
        }
    }

    // ---------------------------------------------------------- catégories

    fun saveCategory(category: Category) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.upsertCategory(category) }
            message = "Catégorie enregistrée"
            refresh()
        }
    }

    fun deleteCategory(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteCategory(id) }
            message = "Catégorie supprimée"
            refresh()
        }
    }

    // ---------------------------------------------------------- événements

    fun saveEvent(event: EventItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.upsertEvent(event) }
            message = if (event.id > 0L) "Événement modifié" else "Événement enregistré"
            refresh()
        }
    }

    fun deleteEvent(id: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.deleteEvent(id) }
            message = "Événement supprimé"
            refresh()
        }
    }

    /** Marque l'occurrence courante comme traitée (maintenance faite, licence renouvelée…). */
    fun markEventDone(event: EventItem, on: LocalDate = LocalDate.now()) {
        saveEvent(event.copy(lastCompleted = on))
    }

    // ------------------------------------------------------ export / import

    fun buildExport(): String = TextFormat.export(data, LocalDate.now())

    fun importText(text: String, replaceExisting: Boolean) {
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) {
                val parsed = TextFormat.parse(text)
                if (!parsed.isEmpty) {
                    if (replaceExisting) {
                        repository.replaceAll(parsed.data)
                    } else {
                        repository.mergeAll(parsed.data)
                    }
                }
                parsed
            }
            lastImportReport = report
            message = when {
                report.isEmpty -> "Aucune donnée exploitable dans ce fichier"
                report.errors.isEmpty() -> "Import réussi : ${report.summary}"
                else -> "Import partiel : ${report.summary}, ${report.errors.size} ligne(s) en erreur"
            }
            refresh()
        }
    }
}
