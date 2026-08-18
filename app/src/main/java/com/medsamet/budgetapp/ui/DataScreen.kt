package com.medsamet.budgetapp.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.medsamet.budgetapp.domain.Category
import com.medsamet.budgetapp.domain.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

@Composable
fun DataScreen(viewModel: BudgetViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val data = viewModel.data

    var replaceOnImport by remember { mutableStateOf(false) }
    var previewVisible by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri: Uri? ->
        if (uri != null) {
            val text = viewModel.buildExport()
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openOutputStream(uri)?.use { stream ->
                            stream.write(text.toByteArray(Charsets.UTF_8))
                        }
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                viewModel.message = if (ok) "Export enregistré" else "Échec de l'export"
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) {
                    try {
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            stream.bufferedReader(Charsets.UTF_8).readText()
                        }
                    } catch (_: Exception) {
                        null
                    }
                }
                if (text == null) {
                    viewModel.message = "Impossible de lire ce fichier"
                } else {
                    viewModel.importText(text, replaceOnImport)
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Données",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            SectionCard(title = "Exporter") {
                Text(
                    "Un fichier texte lisible, modifiable dans n'importe quel éditeur, " +
                        "et réimportable tel quel dans l'application.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                LabeledRow("Dépenses", data.expenses.size.toString())
                LabeledRow("Catégories", data.categories.size.toString())
                LabeledRow("Événements", data.events.size.toString())
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        exportLauncher.launch("budget-" + LocalDate.now().toString() + ".txt")
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Enregistrer le fichier")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(viewModel.buildExport()))
                        viewModel.message = "Export copié dans le presse-papiers"
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copier dans le presse-papiers")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { previewVisible = !previewVisible },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (previewVisible) "Masquer l'aperçu" else "Voir l'aperçu du format")
                }
                if (previewVisible) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = viewModel.buildExport().take(2000),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        item {
            SectionCard(title = "Importer") {
                Text(
                    "Choisis un fichier exporté par l'application (ou écrit à la main " +
                        "dans le même format).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!replaceOnImport) {
                        Button(onClick = { replaceOnImport = false }) { Text("Fusionner") }
                    } else {
                        OutlinedButton(onClick = { replaceOnImport = false }) { Text("Fusionner") }
                    }
                    Spacer(Modifier.width(8.dp))
                    if (replaceOnImport) {
                        Button(onClick = { replaceOnImport = true }) { Text("Remplacer tout") }
                    } else {
                        OutlinedButton(onClick = { replaceOnImport = true }) { Text("Remplacer tout") }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (replaceOnImport) {
                        "Toutes les données actuelles seront effacées avant l'import."
                    } else {
                        "Les données importées s'ajoutent aux données existantes."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (replaceOnImport) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { importLauncher.launch(arrayOf("text/*", "*/*")) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Choisir un fichier")
                }
            }
        }

        val report = viewModel.lastImportReport
        if (report != null) {
            item {
                SectionCard(title = "Dernier import") {
                    LabeledRow("Contenu", report.summary)
                    if (report.errors.isEmpty() && report.warnings.isEmpty()) {
                        Text(
                            "Aucune anomalie détectée.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (report.errors.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Erreurs (" + report.errors.size + ")",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        for (error in report.errors.take(30)) {
                            Text(
                                "· $error",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    if (report.warnings.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Avertissements (" + report.warnings.size + ")",
                            style = MaterialTheme.typography.labelLarge
                        )
                        for (warning in report.warnings.take(30)) {
                            Text("· $warning", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        item { CategoryManager(viewModel) }

        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CategoryManager(viewModel: BudgetViewModel) {
    val data = viewModel.data
    var newCode by remember { mutableStateOf("") }
    var newName by remember { mutableStateOf("") }
    var newBudget by remember { mutableStateOf("") }

    SectionCard(title = "Catégories") {
        for (category in data.categories) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ColorDot(category.colorHex)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = category.name,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = category.monthlyBudgetCents
                            ?.let { Money.display(it) } ?: "pas de budget",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                HorizontalDivider()
            }
        }

        Spacer(Modifier.height(12.dp))
        Text("Ajouter une catégorie", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = newName,
            onValueChange = {
                newName = it
                newCode = it.trim().lowercase().replace(" ", "-")
            },
            label = { Text("Nom") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = newBudget,
            onValueChange = { newBudget = it },
            label = { Text("Budget mensuel (facultatif)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (newName.isNotBlank()) {
                    viewModel.saveCategory(
                        Category(
                            code = newCode.ifEmpty { newName.trim().lowercase() },
                            name = newName.trim(),
                            monthlyBudgetCents = Money.parseToCents(newBudget)
                        )
                    )
                    newName = ""
                    newCode = ""
                    newBudget = ""
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Ajouter")
        }
    }
}
