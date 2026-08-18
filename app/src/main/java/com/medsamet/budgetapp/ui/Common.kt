package com.medsamet.budgetapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.medsamet.budgetapp.domain.TextFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun LabeledRow(label: String, value: String, valueColor: Color? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = valueColor ?: MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun ColorDot(hex: String, size: Int = 12) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(parseHexColor(hex))
    )
}

/** Barre de progression horizontale simple (sans dépendance supplémentaire). */
@Composable
fun ProportionBar(fraction: Float, color: Color, modifier: Modifier = Modifier) {
    val safe = when {
        fraction.isNaN() -> 0f
        fraction < 0f -> 0f
        fraction > 1f -> 1f
        else -> fraction
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(safe)
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(color)
        )
    }
}

/**
 * Saisie de date au clavier (JJ/MM/AAAA ou AAAA-MM-JJ) avec raccourcis.
 * Volontairement sans sélecteur graphique : plus rapide au pouce et
 * sans dépendance à une API expérimentale.
 */
@Composable
fun DateField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showShortcuts: Boolean = true
) {
    val parsed = TextFormat.parseDate(text)
    Column(modifier = modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            label = { Text(label) },
            singleLine = true,
            isError = text.isNotBlank() && parsed == null,
            supportingText = {
                if (text.isNotBlank() && parsed == null) {
                    Text("Format attendu : JJ/MM/AAAA")
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        if (showShortcuts) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { onTextChange(LocalDate.now().format(dayFormatter)) }) {
                    Text("Aujourd'hui")
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { onTextChange(LocalDate.now().minusDays(1).format(dayFormatter)) }) {
                    Text("Hier")
                }
            }
        }
    }
}
