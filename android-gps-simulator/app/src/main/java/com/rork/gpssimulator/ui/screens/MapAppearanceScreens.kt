package com.rork.gpssimulator.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.data.CrosshairColor
import com.rork.gpssimulator.data.CrosshairShape
import com.rork.gpssimulator.data.MockMarkerColor
import com.rork.gpssimulator.i18n.AppLanguage
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.components.Crosshair
import com.rork.gpssimulator.ui.map.LocationDotPreview
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CrosshairAppearanceScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val strings = LocalStrings.current
    AppearancePage(strings[K.crosshair_appearance], onBack, compact = true) {
        MapPreview {
            Crosshair(
                color = Color(settings.crosshairColor.argb),
                crosshairSize = settings.crosshairSizeDp.dp,
                thickness = settings.crosshairThicknessDp.dp,
                opacity = settings.crosshairOpacity,
                shape = settings.crosshairShape,
            )
        }
        // ASCII digits are intentional for every language, including Arabic.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Text("${settings.crosshairSizeDp.roundToInt()} dp  ·  ${decimal(settings.crosshairThicknessDp)} dp  ·  ${(settings.crosshairOpacity * 100).roundToInt()}%",
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge)
        }
        Text(strings[K.crosshair_shape], style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CrosshairShape.entries.forEach { shape ->
                val selected = settings.crosshairShape == shape
                val label = strings[when (shape) {
                    CrosshairShape.MINIMAL -> K.crosshair_minimal
                    CrosshairShape.PRECISION -> K.crosshair_precision
                    CrosshairShape.CORNERS -> K.crosshair_corners
                }]
                Surface(
                    modifier = Modifier.weight(1f).selectable(selected, role = Role.RadioButton,
                        onClick = { viewModel.updateSettings { it.copy(crosshairShape = shape) } }),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(if (selected) 2.dp else 1.dp,
                        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column(Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Crosshair(color = MaterialTheme.colorScheme.onSurface, shape = shape, opacity = 1f)
                        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                        Box(Modifier.size(14.dp)) {
                            if (selected) Icon(Icons.Default.Check, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        Text(strings[K.appearance_color], style = MaterialTheme.typography.titleMedium)
        CrosshairColorStrip(settings.crosshairColor) { color ->
            viewModel.updateSettings { it.copy(crosshairColor = color) }
        }
        AppearanceSlider(strings[K.crosshair_size], settings.crosshairSizeDp,
            "${settings.crosshairSizeDp.roundToInt()} dp", 20f..80f, 59) {
            viewModel.updateSettings { old -> old.copy(crosshairSizeDp = it.roundToInt().toFloat()) }
        }
        AppearanceSlider(strings[K.crosshair_thickness], settings.crosshairThicknessDp,
            "${decimal(settings.crosshairThicknessDp)} dp", 1f..6f, 9) {
            viewModel.updateSettings { old -> old.copy(crosshairThicknessDp = (it * 2).roundToInt() / 2f) }
        }
        AppearanceSlider(strings[K.crosshair_opacity], settings.crosshairOpacity,
            "${(settings.crosshairOpacity * 100).roundToInt()}%", 0.2f..1f, 79) {
            viewModel.updateSettings { old -> old.copy(crosshairOpacity = (it * 100).roundToInt() / 100f) }
        }
    }
}

@Composable
fun MockMarkerColorScreen(viewModel: AppViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val strings = LocalStrings.current
    AppearancePage(strings[K.mock_marker_color], onBack) {
        MapPreview { LocationDotPreview(Color(settings.mockMarkerColor.argb)) }
        Text(strings[K.appearance_color], style = MaterialTheme.typography.titleMedium)
        ColorChoices(MockMarkerColor.entries.map { it.name to Color(it.argb) }, settings.mockMarkerColor.name) { name ->
            viewModel.updateSettings { it.copy(mockMarkerColor = MockMarkerColor.valueOf(name)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearancePage(title: String, onBack: () -> Unit, compact: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val strings = LocalStrings.current
    CompositionLocalProvider(LocalLayoutDirection provides
        if (strings.language == AppLanguage.ARABIC) LayoutDirection.Rtl else LayoutDirection.Ltr) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(title = { Text(title, style = MaterialTheme.typography.titleLarge) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings[K.close])
                        }
                    }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)
                .then(if (compact) Modifier else Modifier.verticalScroll(rememberScrollState()))
                .padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 16.dp), content = content)
        }
    }
}

@Composable
private fun MapPreview(content: @Composable BoxScope.() -> Unit) {
    val strings = LocalStrings.current
    val surface = MaterialTheme.colorScheme.surfaceVariant
    val road = MaterialTheme.colorScheme.outline.copy(alpha = 0.38f)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
        .background(surface)) {
        Box(Modifier.fillMaxWidth().height(92.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize()) {
                fun street(x1: Float, y1: Float, x2: Float, y2: Float) =
                    drawLine(road, Offset(size.width * x1, size.height * y1),
                        Offset(size.width * x2, size.height * y2), 3.dp.toPx())
                street(-0.1f, 0.2f, 1.1f, 0.85f)
                street(-0.1f, 0.95f, 1.1f, 0.1f)
                street(0.2f, -0.2f, 0.4f, 1.2f)
                street(0.73f, -0.2f, 0.87f, 1.2f)
            }
            content()
        }
        Text(strings[K.appearance_preview], color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
    }
}

@Composable
private fun CrosshairColorStrip(selected: CrosshairColor, onSelect: (CrosshairColor) -> Unit) {
    // A single compact row of 12 swatches, with no visible color names.
    Row(
        Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CrosshairColor.entries.forEach { color ->
            Box(
                modifier = Modifier.weight(1f).aspectRatio(1f)
                    .clip(CircleShape)
                    .background(Color(color.argb), CircleShape)
                    .selectable(selected == color, role = Role.RadioButton,
                        onClick = { onSelect(color) })
                    .semantics { contentDescription = color.name },
                contentAlignment = Alignment.Center,
            ) {
                if (selected == color) {
                    Icon(Icons.Default.Check, contentDescription = null,
                        tint = if (color == CrosshairColor.WHITE || color == CrosshairColor.YELLOW ||
                            color == CrosshairColor.LIME || color == CrosshairColor.CYAN)
                            Color(0xFF14202B) else Color.White,
                        modifier = Modifier.fillMaxSize(0.65f))
                }
            }
        }
    }
}

@Composable
private fun ColorChoices(colors: List<Pair<String, Color>>, selected: String, onSelect: (String) -> Unit) {
    val strings = LocalStrings.current
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        colors.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (name, color) ->
                    val label = strings[when (name) {
                        "WHITE" -> K.color_white
                        "LIGHT_BLUE" -> K.color_light_blue
                        "GREEN" -> K.color_green
                        "ORANGE" -> K.color_orange
                        "PURPLE" -> K.color_purple
                        "RED" -> K.color_red
                        "PINK" -> K.color_pink
                        else -> K.color_yellow
                    }]
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .selectable(selected == name, role = Role.RadioButton, onClick = { onSelect(name) })
                        .padding(2.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Box(Modifier.size(26.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
                            if (selected == name) Icon(Icons.Default.Check, contentDescription = null,
                                tint = Color(0xFF152031), modifier = Modifier.size(22.dp))
                        }
                        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSlider(label: String, value: Float, display: String, range: ClosedFloatingPointRange<Float>,
                             steps: Int, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Text(display, style = MaterialTheme.typography.labelLarge)
            }
        }
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange,
            valueRange = range, steps = steps, modifier = Modifier.fillMaxWidth().semantics { contentDescription = label })
    }
}

private fun decimal(value: Float): String = String.format(Locale.US, "%.1f", value)
