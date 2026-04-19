package com.aurapdf.app.presentation.viewer.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aurapdf.app.domain.prefs.SmartReadingPrefs
import com.aurapdf.app.domain.translation.TranslationPrefs
import java.util.Locale

/**
 * Modal dialog that lets the user configure which content types are
 * included or skipped by the Smart Content Filter, and pick a target
 * language for translation.
 */
@Composable
fun SmartReadingSettingsDialog(
    prefs: SmartReadingPrefs,
    translationPrefs: TranslationPrefs,
    availableLanguages: List<String>,
    onPrefsChanged: (SmartReadingPrefs) -> Unit,
    onTranslationPrefsChanged: (TranslationPrefs) -> Unit,
    onDismiss: () -> Unit,
) {
    // Local copies — only committed on "Apply"
    var local by remember(prefs) { mutableStateOf(prefs) }
    var localTranslation by remember(translationPrefs) { mutableStateOf(translationPrefs) }
    var showLanguagePicker by remember { mutableStateOf(false) }

    if (showLanguagePicker) {
        LanguagePickerDialog(
            languages       = availableLanguages,
            selectedCode    = localTranslation.targetLanguage,
            onLanguageSelected = { code ->
                localTranslation = localTranslation.copy(targetLanguage = code)
                showLanguagePicker = false
            },
            onDismiss = { showLanguagePicker = false },
        )
        return
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text  = "Settings",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // ── Smart Reading section ────────────────────────────────────
                Text(
                    text  = "Smart Reading",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Choose what the reader skips when reading aloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                ToggleRow(
                    label    = "Skip page headers",
                    subtitle = "Running titles at the top of each page",
                    checked  = local.skipHeaders,
                    onCheckedChange = { local = local.copy(skipHeaders = it) },
                )
                ToggleRow(
                    label    = "Skip page footers",
                    subtitle = "Running text at the bottom of each page",
                    checked  = local.skipFooters,
                    onCheckedChange = { local = local.copy(skipFooters = it) },
                )
                ToggleRow(
                    label    = "Skip page numbers",
                    subtitle = "Standalone number patterns (e.g. \"42\", \"Page 5\")",
                    checked  = local.skipPageNumbers,
                    onCheckedChange = { local = local.copy(skipPageNumbers = it) },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                ToggleRow(
                    label    = "Skip footnotes",
                    subtitle = "Small-font text in the bottom margin",
                    checked  = local.skipFootnotes,
                    onCheckedChange = { local = local.copy(skipFootnotes = it) },
                )
                ToggleRow(
                    label    = "Skip figure captions",
                    subtitle = "Labels under images and tables",
                    checked  = local.skipCaptions,
                    onCheckedChange = { local = local.copy(skipCaptions = it) },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // ── Translation section ──────────────────────────────────────
                Text(
                    text  = "Translation",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Double-press Volume Down during TTS to translate the current segment.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))

                // Target language selector
                Row(
                    modifier           = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguagePicker = true }
                        .padding(vertical = 10.dp),
                    verticalAlignment  = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text(
                            text  = "Target language",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text  = "Language to translate into",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text  = if (localTranslation.targetLanguage.isBlank()) "Not set"
                                else languageDisplayName(localTranslation.targetLanguage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPrefsChanged(local)
                onTranslationPrefsChanged(localTranslation)
                onDismiss()
            }) {
                Text("Apply")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Full-screen-ish language picker dialog.
 */
@Composable
private fun LanguagePickerDialog(
    languages: List<String>,
    selectedCode: String,
    onLanguageSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sortedLanguages = remember(languages) {
        languages.sortedBy { languageDisplayName(it) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select language", style = MaterialTheme.typography.titleMedium) },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
            ) {
                // "None" option to disable translation
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected("") }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text  = "None (disable translation)",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedCode.isBlank()) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                items(sortedLanguages) { code ->
                    val displayName = languageDisplayName(code)
                    val isSelected = code == selectedCode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLanguageSelected(code) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text  = displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun ToggleRow(
    label: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text  = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text  = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked         = checked,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun languageDisplayName(code: String): String =
    Locale(code).displayLanguage.replaceFirstChar { it.uppercase() }
