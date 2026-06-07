package com.Zero23.countdown.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.Zero23.countdown.R

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ColorPickerDialog(
    initialColorHex: String?,
    showFollowSystem: Boolean = false,
    showFollowBackground: Boolean = false,
    isBackgroundSet: Boolean = false,
    savedColors: List<String> = emptyList(),
    onDeleteSavedColor: (String) -> Unit = {},
    onSaveColor: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onColorSelected: (String?) -> Unit
) {
    val defaultColor = String.format("#%06X", (0xFFFFFF and MaterialTheme.colorScheme.primary.toArgb()))
    val startColor = initialColorHex ?: defaultColor
    var hexInput by remember { mutableStateOf(startColor.replace("#", "")) }
    var rInput by remember { mutableStateOf(((if (startColor.startsWith("#")) startColor else "#$startColor").toColorInt() shr 16 and 0xFF).toString()) }
    var gInput by remember { mutableStateOf(((if (startColor.startsWith("#")) startColor else "#$startColor").toColorInt() shr 8 and 0xFF).toString()) }
    var bInput by remember { mutableStateOf(((if (startColor.startsWith("#")) startColor else "#$startColor").toColorInt() and 0xFF).toString()) }

    val presets = listOf("#2196F3", "#F44336", "#4CAF50", "#FFEB3B", "#9C27B0", "#FF9800", "#795548", "#607D8B", "#000000", "#FFFFFF")
    val specialPresets = listOf("#88dd44", "#ffccaa", "#99ccff", "#ffaacc", "#99eedd")

    fun updateFromRgb() {
        try {
            val r = rInput.toInt().coerceIn(0, 255)
            val g = gInput.toInt().coerceIn(0, 255)
            val b = bInput.toInt().coerceIn(0, 255)
            val hex = String.format("%02X%02X%02X", r, g, b)
            hexInput = hex
        } catch (_: Exception) {}
    }

    fun updateFromHex() {
        try {
            if (hexInput.length == 6) {
                val color = "#$hexInput".toColorInt()
                rInput = (color shr 16 and 0xFF).toString()
                gInput = (color shr 8 and 0xFF).toString()
                bInput = (color and 0xFF).toString()
            }
        } catch (_: Exception) {}
    }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(stringResource(R.string.custom_color), style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(8.dp)).background(
                    try { Color("#$hexInput".toColorInt()) } catch(_: Exception) { Color.Gray }
                ))

                Spacer(modifier = Modifier.height(16.dp))
                Text(stringResource(R.string.preset_color), style = MaterialTheme.typography.labelLarge)
                LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.height(95.dp)) {
                    items(presets) { color ->
                        Box(modifier = Modifier.padding(4.dp).size(36.dp).clip(CircleShape).background(Color(color.toColorInt())).clickable {
                            hexInput = color.replace("#", "")
                            updateFromHex()
                        })
                    }
                }

                val filteredSavedColors = remember(savedColors, presets, specialPresets) {
                    savedColors.filter { color ->
                        presets.none { it.equals(color, ignoreCase = true) } &&
                        specialPresets.none { it.equals(color, ignoreCase = true) }
                    }
                }
                
                if (filteredSavedColors.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.saved_colors), style = MaterialTheme.typography.labelLarge)
                    LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.heightIn(max = 95.dp)) {
                        items(filteredSavedColors) { color ->
                            Box(modifier = Modifier
                                .padding(4.dp)
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(color.toColorInt()))
                                .combinedClickable(
                                    onClick = {
                                        hexInput = color.replace("#", "")
                                        updateFromHex()
                                    },
                                    onLongClick = {
                                        onDeleteSavedColor(color)
                                    }
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.clover_pre), style = MaterialTheme.typography.labelLarge)
                LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.height(50.dp)) {
                    items(specialPresets) { color ->
                        Box(modifier = Modifier.padding(4.dp).size(36.dp).clip(CircleShape).background(Color(color.toColorInt())).clickable {
                            hexInput = color.replace("#", "")
                            updateFromHex()
                        })
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                Text(stringResource(R.string.rgb_edit), style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("R" to rInput, "G" to gInput, "B" to bInput).forEachIndexed { index, pair ->
                        OutlinedTextField(
                            value = pair.second,
                            onValueChange = {
                                if (index == 0) rInput = it else if (index == 1) gInput = it else bInput = it
                                updateFromRgb()
                            },
                            label = { Text(pair.first) },
                            modifier = Modifier.weight(1f),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.hex_edit), style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = {
                        hexInput = it.take(6).uppercase()
                        updateFromHex()
                    },
                    label = { Text("#") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Row {
                        if (showFollowSystem) {
                            TextButton(onClick = {
                                onColorSelected(null)
                                onDismiss()
                            }) { Text(stringResource(R.string.theme_follow_system)) }
                        }
                        if (showFollowBackground) {
                            TextButton(
                                onClick = {
                                    onColorSelected("FOLLOW_BG")
                                    onDismiss()
                                },
                                enabled = isBackgroundSet
                            ) { Text(stringResource(R.string.follow_bg)) }
                        }
                    }
                }
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        val finalHex = "#$hexInput"
                        val isPreset = presets.any { it.equals(finalHex, ignoreCase = true) } ||
                                       specialPresets.any { it.equals(finalHex, ignoreCase = true) }
                        if (!isPreset) {
                            onSaveColor(finalHex)
                        }
                        onColorSelected(finalHex)
                        onDismiss()
                    }) { Text(stringResource(R.string.confirm)) }
                }
            }
        }
    }
}
