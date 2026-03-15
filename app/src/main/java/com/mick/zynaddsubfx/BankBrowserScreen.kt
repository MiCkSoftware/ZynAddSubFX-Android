package com.mick.zynaddsubfx

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun BankBrowserScreen(
    packagedPresets: List<SynthEngine.PresetAsset>,
    scanResults: Map<String, PresetScanRecord>,
    presetRatings: Map<String, Int>,
    currentPresetPath: String?,
    presetLoading: Boolean,
    catalogLoading: Boolean,
    expandedSections: Map<String, Boolean>,
    onLoadPreset: (SynthEngine.PresetAsset) -> Unit,
    onToggleSectionExpanded: (String) -> Unit,
    onSetPresetRating: (String, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val presetsBySection = remember(packagedPresets) { packagedPresets.groupBy { it.section } }
    val sortedSections = remember(packagedPresets, presetRatings) {
        presetsBySection.entries.sortedWith(
            compareBy<Map.Entry<String, List<SynthEngine.PresetAsset>>> { entry ->
                entry.value.minOfOrNull { preset ->
                    val rating = presetRatings[preset.assetPath] ?: 0
                    when {
                        rating in 1..3 -> 0
                        rating in 4..5 -> 1
                        else -> 2
                    }
                } ?: Int.MAX_VALUE
            }.thenBy { it.key.lowercase() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        if (catalogLoading) {
            Text("Loading catalog…", color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(10.dp))
        }

        sortedSections.forEach { (section, presets) ->
            val sectionExpanded = expandedSections[section] ?: false
            val sectionHasCurrentPreset =
                currentPresetPath != null && presets.any { it.assetPath == currentPresetPath }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = if (sectionExpanded) "[-]" else "[+]",
                        modifier = Modifier.clickable { onToggleSectionExpanded(section) },
                        color = if (sectionHasCurrentPreset) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = section,
                        color = if (sectionHasCurrentPreset) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = "${presets.size} presets",
                        color = if (sectionHasCurrentPreset) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                if (sectionExpanded) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val sortedPresets = presets.sortedBy { it.displayName.lowercase() }
                    sortedPresets.forEachIndexed { index, preset ->
                        val isLast = index == sortedPresets.lastIndex
                        val prefix = if (isLast) "└─" else "├─"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (preset.assetPath == currentPresetPath) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                    RoundedCornerShape(4.dp)
                                )
                                .clickable(enabled = !presetLoading) { onLoadPreset(preset) }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("│", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(prefix, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            PresetTypeBadge(preset.assetPath)
                            Text(
                                text = preset.displayName,
                                color = when {
                                    preset.assetPath == currentPresetPath -> MaterialTheme.colorScheme.onPrimaryContainer
                                    presetLoading -> MaterialTheme.colorScheme.onSurfaceVariant
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                            )
                            PresetStarRating(
                                rating = (presetRatings[preset.assetPath] ?: 0).coerceIn(0, 5),
                                onSetRating = { onSetPresetRating(preset.assetPath, it) }
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))
    }
}

@Composable
private fun PresetTypeBadge(assetPath: String) {
    val lower = assetPath.lowercase()
    val label = when {
        lower.endsWith(".xmz") -> "XMZ"
        lower.endsWith(".xiz") -> "XIZ"
        else -> "?"
    }
    val bg = when (label) {
        "XMZ" -> MaterialTheme.colorScheme.secondaryContainer
        "XIZ" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (label) {
        "XMZ" -> MaterialTheme.colorScheme.onSecondaryContainer
        "XIZ" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Text(
        text = label,
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 1.dp)
    )
}

@Composable
fun PresetStarRating(
    rating: Int,
    onSetRating: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "○",
            color = if (rating == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.clickable { onSetRating(0) }
        )
        (1..5).forEach { star ->
            Text(
                text = if (star <= rating) "★" else "☆",
                color = if (star <= rating) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable { onSetRating(star) }
            )
        }
    }
}
