package com.mybook.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mybook.app.domain.Period
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale

/** Lets the user jump straight to a week (date), month (month grid) or year. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodPickerDialog(period: Period, anchor: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    when (period) {
        Period.WEEK -> {
            val state = rememberDatePickerState(initialSelectedDateMillis = anchor.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            DatePickerDialog(
                onDismissRequest = onDismiss,
                confirmButton = {
                    TextButton(onClick = {
                        state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
                    }) { Text("Show week") }
                },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            ) { DatePicker(state = state) }
        }
        Period.MONTH -> {
            var year by remember { mutableIntStateOf(anchor.year) }
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { year-- }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous year") }
                        Text(year.toString(), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { year++ }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next year") }
                    }
                },
                text = {
                    PickerGrid(
                        items = (1..12).toList(),
                        label = { Month.of(it).getDisplayName(TextStyle.SHORT, Locale.getDefault()) },
                        isSelected = { year == anchor.year && it == anchor.monthValue },
                        onClick = { onPick(LocalDate.of(year, it, 1)) },
                    )
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
        Period.YEAR -> {
            val current = LocalDate.now().year
            AlertDialog(
                onDismissRequest = onDismiss,
                title = { Text("Choose year") },
                text = {
                    PickerGrid(
                        items = ((current - 10)..(current + 1)).toList().reversed(),
                        label = { it.toString() },
                        isSelected = { it == anchor.year },
                        onClick = { onPick(LocalDate.of(it, 1, 1)) },
                    )
                },
                confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
            )
        }
    }
}

@Composable
private fun PickerGrid(items: List<Int>, label: (Int) -> String, isSelected: (Int) -> Boolean, onClick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { value ->
                    val selected = isSelected(value)
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { onClick(value) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label(value),
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                repeat(3 - row.size) { Box(Modifier.weight(1f)) }
            }
        }
    }
}
