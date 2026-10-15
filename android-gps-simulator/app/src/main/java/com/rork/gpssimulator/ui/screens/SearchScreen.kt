package com.rork.gpssimulator.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rork.gpssimulator.i18n.K
import com.rork.gpssimulator.i18n.LocalStrings
import com.rork.gpssimulator.ui.AppViewModel
import com.rork.gpssimulator.ui.map.MapMarker
import com.rork.gpssimulator.ui.map.MapView
import com.rork.gpssimulator.ui.map.MarkerStyle
import com.rork.gpssimulator.ui.map.rememberMapCameraState
import com.rork.gpssimulator.ui.theme.LocalAppColors
import com.rork.gpssimulator.ui.theme.MonoValueStyle
import com.rork.gpssimulator.util.Format

/**
 * Map search presented as a floating capsule above the map, matching the visual
 * language used by the onboarding artwork instead of replacing the map with a
 * separate full-screen search page.
 */
@Composable
fun SearchScreen(
    viewModel: AppViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = LocalStrings.current
    val appColors = LocalAppColors.current
    val settings by viewModel.settings.collectAsState()
    val results by viewModel.searchResults.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val recents by viewModel.recentSearches.collectAsState()
    val selected by viewModel.selected.collectAsState()
    val realSample by viewModel.realSample.collectAsState()

    var query by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val camera = rememberMapCameraState(
        initialCenter = selected?.point
            ?: realSample?.point
            ?: com.rork.gpssimulator.data.model.LatLng(settings.lastCameraLat, settings.lastCameraLng),
        initialZoom = if (settings.rememberLastPosition) settings.lastCameraZoom else settings.defaultZoom,
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(modifier = modifier.fillMaxSize()) {
        MapView(
            camera = camera,
            mapType = settings.mapType,
            markers = buildList {
                realSample?.let {
                    add(
                        MapMarker(
                            point = it.point,
                            color = appColors.success,
                            radiusMeters = it.accuracy.toDouble(),
                            style = MarkerStyle.REAL,
                        ),
                    )
                }
                selected?.let {
                    add(
                        MapMarker(
                            point = it.point,
                            color = MaterialTheme.colorScheme.primary,
                            style = MarkerStyle.CANDIDATE,
                        ),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            ) {
                TextField(
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.search(it)
                    },
                    placeholder = {
                        Text(
                            text = strings[K.search_hint],
                            color = appColors.muted,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(25.dp),
                        )
                    },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (query.isNotEmpty()) {
                                    query = ""
                                    viewModel.clearSearchResults()
                                } else {
                                    keyboard?.hide()
                                    onBack()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = strings[K.close],
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            viewModel.search(query)
                            keyboard?.hide()
                        },
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )
            }

            if (isSearching) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    shadowElevation = 8.dp,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(strings[K.searching], style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else if (results.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 290.dp)
                        .shadow(10.dp, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                ) {
                    Column {
                        results.take(4).forEach { place ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) {
                                        viewModel.commitSearch(query)
                                        viewModel.selectPoint(place.point)
                                        keyboard?.hide()
                                        onBack()
                                        viewModel.moveCamera(place.point, 16f)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                                ) {
                                    Box(
                                        modifier = Modifier.size(46.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(27.dp),
                                        )
                                    }
                                }
                                Spacer(Modifier.width(13.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = place.name.ifBlank { strings[K.place_unknown] },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (place.address.isNotBlank()) {
                                        Text(
                                            text = place.address,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = appColors.muted,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    Text(
                                        text = Format.coordPair(place.point),
                                        style = MonoValueStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize),
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (query.isBlank() && recents.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(8.dp, RoundedCornerShape(22.dp)),
                    shape = RoundedCornerShape(22.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                ) {
                    Column {
                        recents.take(4).forEach { recent ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(role = Role.Button) {
                                        query = recent
                                        viewModel.search(recent)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Default.History,
                                    contentDescription = null,
                                    tint = appColors.muted,
                                    modifier = Modifier.size(21.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = recent,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
