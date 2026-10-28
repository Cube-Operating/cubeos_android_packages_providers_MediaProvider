/*
 * Copyright 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.photopicker.features.navigationbar

import android.provider.MediaStore
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import com.android.photopicker.R
import com.android.photopicker.core.LocalPickerSheetActions
import com.android.photopicker.core.PickerSheetActions
import com.android.photopicker.core.StateSelector
import com.android.photopicker.core.animations.standardDecelerate
import com.android.photopicker.core.configuration.LocalPhotopickerConfiguration
import com.android.photopicker.core.embedded.LocalEmbeddedState
import com.android.photopicker.core.events.Event
import com.android.photopicker.core.events.LocalEvents
import com.android.photopicker.core.events.Telemetry
import com.android.photopicker.core.features.FeatureToken
import com.android.photopicker.core.features.LocalFeatureManager
import com.android.photopicker.core.features.Location
import com.android.photopicker.core.features.LocationParams
import com.android.photopicker.core.hideWhenState
import com.android.photopicker.core.navigation.LocalNavController
import com.android.photopicker.core.navigation.PhotopickerDestinations
import com.android.photopicker.core.selection.LocalSelection
import com.android.photopicker.data.model.Group
import com.android.photopicker.extensions.navigateToAlbumGrid
import com.android.photopicker.features.albumgrid.AlbumGridFeature
import com.android.photopicker.features.overflowmenu.OverflowMenuFeature
import com.android.photopicker.features.profileselector.ProfileSelectorFeature
import com.android.photopicker.features.search.SearchFeature
import com.android.photopicker.features.selectionbar.SelectionBarFeature
import com.android.photopicker.util.LocalLocalizationHelper
import kotlinx.coroutines.launch

/*
 * CubeOS: the navigation bar is laid out like the title bar of the iOS Photos picker:
 *
 *   Cancel            [ Photos | Albums ]            Add (n)
 *
 * The left and right slots share the remaining width equally so the segmented control is always
 * centred. All actions reuse the picker's existing callbacks (see [PickerSheetActions]).
 */

/* Navigation bar button measurements */
private val MEASUREMENT_ICON_BUTTON_WIDTH = 48.dp

/* Padding values around the edges of the NavigationBar */
private val MEASUREMENT_EDGE_PADDING = 4.dp
private val MEASUREMENT_TOP_PADDING = 4.dp
private val MEASUREMENT_BOT_PADDING = 10.dp

/* Minimum height for the NavigationBar (iOS navigation bar height) */
private val MEASUREMENT_MIN_HEIGHT = 44.dp

/* iOS segmented control */
private val MEASUREMENT_SEGMENTED_WIDTH = 200.dp
private val MEASUREMENT_SEGMENTED_HEIGHT = 32.dp
private val MEASUREMENT_SEGMENTED_TRACK_PADDING = 2.dp
private val SEGMENTED_TRACK_SHAPE = RoundedCornerShape(9.dp)
private val SEGMENTED_THUMB_SHAPE = RoundedCornerShape(7.dp)
private val SEGMENTED_LABEL_STYLE =
    TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)

/* iOS bar button text */
private val BAR_BUTTON_FONT_SIZE = 17.sp
private val BAR_BUTTON_PADDING = PaddingValues(horizontal = 12.dp, vertical = 0.dp)

/**
 * Top of the NavigationBar feature.
 *
 * Since NavigationBar doesn't register any routes its top composable is drawn at
 * [Location.NAVIGATION_BAR] which begins here.
 *
 * This composable provides a full width row for the navigation bar and calls the feature manager to
 * provide [NavigationBarButton]s for the row.
 *
 * If the search feature is enabled [Location.SEARCH_BAR] is drawn above the [NavigationBarButtons]
 * at the top.
 *
 * Additionally, the composable also calls for the [PROFILE_SELECTOR] and [OVERFLOW_MENU] locations.
 */
@Composable
fun NavigationBar(modifier: Modifier = Modifier, params: LocationParams) {

    val navController = LocalNavController.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val featureManager = LocalFeatureManager.current
    val searchFeatureEnabled = featureManager.isFeatureEnabled(SearchFeature::class.java)

    Row(
        modifier =
            modifier
                .padding(
                    start = MEASUREMENT_EDGE_PADDING,
                    end = MEASUREMENT_EDGE_PADDING,
                    top = MEASUREMENT_TOP_PADDING,
                    bottom = MEASUREMENT_BOT_PADDING,
                )
                .heightIn(min = MEASUREMENT_MIN_HEIGHT),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {

            // When inside an album display the album title and a back button,
            // instead of the normal navigation bar contents.
            currentRoute == PhotopickerDestinations.ALBUM_MEDIA_GRID.route ->
                NavigationBarForAlbum(modifier)

            // When search feature is enabled then display search bar along with profile selector,
            // overflow menu and the navigation buttons below it.
            searchFeatureEnabled -> NavigationBarWithSearch(modifier, params)

            // For all other routes, show the profile selector and the navigation buttons
            else -> BasicNavigationBar(modifier)
        }
    }
}

/**
 * Composable that can be used to build a consistent button for the [NavigationBarFeature]
 *
 * CubeOS: drawn as one segment of an iOS segmented control. The selected segment is a raised
 * rounded rectangle, the others are transparent over the control's track.
 *
 * @param onClick the handler to run when the button is clicked.
 * @param modifier A modifier which is applied directly to the button. This should be the modifier
 *   that is passed via the Location compose call.
 * @param isCurrentRoute a function which receives the current
 *   [NavController.currentDestination.route] and returns true if that route matches the route this
 *   button represents.
 * @param buttonContent A composable to render as the button's content. Should most likely be a
 *   string label.
 */
@Composable
fun NavigationBarButton(
    onClick: () -> Unit,
    modifier: Modifier,
    isCurrentRoute: (String) -> Boolean,
    buttonContent: @Composable () -> Unit,
) {
    val navController = LocalNavController.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val isSelected = isCurrentRoute(currentRoute ?: "")

    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .then(
                    if (isSelected) {
                        Modifier.shadow(elevation = 1.dp, shape = SEGMENTED_THUMB_SHAPE)
                            .background(
                                MaterialTheme.colorScheme.surfaceBright,
                                SEGMENTED_THUMB_SHAPE,
                            )
                    } else {
                        Modifier
                    }
                )
                .clip(SEGMENTED_THUMB_SHAPE)
                .semantics { selected = isSelected }
                .clickable(role = Role.Tab, onClick = onClick)
                .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides SEGMENTED_LABEL_STYLE,
            LocalContentColor provides MaterialTheme.colorScheme.onSurface,
        ) {
            buttonContent()
        }
    }
}

/**
 * Creates and positions any navigation buttons that have been registered for the
 * [NAVIGATION_BAR_NAV_BUTTON] location. Accepts a maximum of two buttons.
 *
 * CubeOS: the buttons sit inside an iOS segmented control track, sharing its width equally.
 */
@Composable
private fun NavigationBarButtons(modifier: Modifier) {
    Row(
        // Consume the incoming modifier to get the correct positioning.
        modifier =
            modifier
                .height(MEASUREMENT_SEGMENTED_HEIGHT)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest, SEGMENTED_TRACK_SHAPE)
                .padding(MEASUREMENT_SEGMENTED_TRACK_PADDING),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Buttons are provided by registered features, so request for the features to fill
        // this content.
        LocalFeatureManager.current.composeLocation(
            Location.NAVIGATION_BAR_NAV_BUTTON,
            maxSlots = 2,
            modifier = Modifier.weight(1f),
        )
    }
}

/** CubeOS: iOS "Cancel" bar button. Dismisses the picker exactly like a swipe down. */
@Composable
private fun CancelBarButton(actions: PickerSheetActions) {
    TextButton(onClick = actions.onCancel, contentPadding = BAR_BUTTON_PADDING) {
        Text(
            text = stringResource(android.R.string.cancel),
            fontSize = BAR_BUTTON_FONT_SIZE,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * Whether the confirm ("Add") action lives in the title bar. It does whenever the full screen
 * picker provides [PickerSheetActions], the selection bar feature is on, and search is off (with
 * search the title bar is occupied by the search field, so the selection bar keeps its Done).
 */
@Composable
fun isConfirmInTitleBar(): Boolean {
    val featureManager = LocalFeatureManager.current
    return LocalPickerSheetActions.current != null &&
        featureManager.isFeatureEnabled(SelectionBarFeature::class.java) &&
        !featureManager.isFeatureEnabled(SearchFeature::class.java)
}

/**
 * CubeOS: iOS "Add" bar button, shown under the same conditions as the selection bar's Done
 * button, and doing the same thing (same telemetry, same confirm callback).
 */
@Composable
private fun AddBarButton(actions: PickerSheetActions) {
    val currentSelection by LocalSelection.current.flow.collectAsStateWithLifecycle()
    val configuration = LocalPhotopickerConfiguration.current
    val events = LocalEvents.current
    val scope = rememberCoroutineScope()

    // Same visibility rule as the selection bar: ACTION_USER_SELECT_IMAGES_FOR_APP always allows
    // confirming, even with zero items, to let the user revoke all grants.
    val visible =
        currentSelection.isNotEmpty() ||
            MediaStore.ACTION_USER_SELECT_IMAGES_FOR_APP.equals(configuration.action)
    if (!visible) return

    val label = stringResource(R.string.photopicker_done_button_label)
    val count = LocalLocalizationHelper.current.getLocalizedCount(currentSelection.size)
    TextButton(
        onClick = {
            scope.launch {
                events.dispatch(
                    Event.LogPhotopickerUIEvent(
                        FeatureToken.SELECTION_BAR.token,
                        configuration.sessionId,
                        configuration.callingPackageUid ?: -1,
                        Telemetry.UiEvent.PICKER_CLICK_ADD_BUTTON,
                    )
                )
            }
            actions.onConfirm()
        },
        contentPadding = BAR_BUTTON_PADDING,
    ) {
        Text(
            text = if (currentSelection.isEmpty()) label else "$label ($count)",
            fontSize = BAR_BUTTON_FONT_SIZE,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * Composable that provides Navigation Bar when inside an album that displays the album title and a
 * back button
 *
 * @param modifier Modifier used to configure the layout of the navigation bar.
 */
@Composable
private fun NavigationBarForAlbum(modifier: Modifier) {
    val navController = LocalNavController.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val actions = LocalPickerSheetActions.current
    val confirmInTitleBar = isConfirmInTitleBar()
    val featureManager = LocalFeatureManager.current
    val overFlowMenuEnabled =
        remember(featureManager) {
            featureManager.isFeatureEnabled(OverflowMenuFeature::class.java)
        }
    val flow =
        navBackStackEntry?.savedStateHandle?.getStateFlow<Group.Album?>(AlbumGridFeature.ALBUM_KEY, null)
    val album = flow?.value

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // iOS back button: chevron + "Albums" in the accent colour.
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (album != null) {
                TextButton(
                    onClick = { navController.navigateToAlbumGrid() },
                    contentPadding = PaddingValues(start = 4.dp, end = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                        // For accessibility
                        contentDescription = stringResource(R.string.photopicker_back_option),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.photopicker_albums_nav_button_label),
                        fontSize = BAR_BUTTON_FONT_SIZE,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }

        // Album name, centred like an iOS navigation title.
        if (album != null) {
            Text(
                text = album.displayName,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                // Traversal index -1 forces TalkBack to focus on the album title first.
                modifier = Modifier.weight(1.2f).semantics { traversalIndex = -1f },
                textAlign = TextAlign.Center,
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (actions != null && confirmInTitleBar) {
                AddBarButton(actions)
            }
            if (overFlowMenuEnabled) {
                featureManager.composeLocation(
                    Location.OVERFLOW_MENU,
                    modifier = Modifier.width(MEASUREMENT_ICON_BUTTON_WIDTH),
                )
            }
        }
    }
}

/**
 * A composable function that displays a Navigation Bar with an integrated search bar which is
 * called when the search feature is enabled.
 *
 * This Navigation Bar also includes [PROFILE_SELECTOR] and [OVERFLOW_MENU]
 *
 * Navigation buttons are positioned below the search bar.
 */
@Composable
private fun NavigationBarWithSearch(modifier: Modifier, params: LocationParams) {
    val featureManager = LocalFeatureManager.current
    val actions = LocalPickerSheetActions.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
            if (actions != null) {
                CancelBarButton(actions)
            }
            featureManager.composeLocation(
                Location.SEARCH_BAR,
                maxSlots = 1,
                modifier = Modifier.weight(1f),
                params,
            )
            featureManager.composeLocation(
                Location.PROFILE_SELECTOR,
                maxSlots = 1,
                modifier = Modifier.padding(start = 8.dp),
            )
            val overFlowMenuEnabled =
                remember(featureManager) {
                    featureManager.isFeatureEnabled(OverflowMenuFeature::class.java)
                }
            if (
                overFlowMenuEnabled &&
                    LocalFeatureManager.current.getSizeOfLocationInRegistry(
                        Location.OVERFLOW_MENU_ITEMS
                    ) > 0
            ) {
                Row(modifier = Modifier, horizontalArrangement = Arrangement.End) {
                    featureManager.composeLocation(
                        Location.OVERFLOW_MENU,
                        modifier = Modifier.width(MEASUREMENT_ICON_BUTTON_WIDTH),
                    )
                }
            }
        }
        NavigationBarButtons(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp)
        )
    }
}

/**
 * A composable function that displays a default Navigation Bar. This includes a [PROFILE_SELECTOR]
 * and [OVERFLOW_MENU] along with navigation buttons.
 */
@Composable
private fun BasicNavigationBar(modifier: Modifier) {
    val featureManager = LocalFeatureManager.current
    val actions = LocalPickerSheetActions.current
    val confirmInTitleBar = isConfirmInTitleBar()
    val profileSelectorEnabled =
        remember(featureManager) {
            featureManager.isFeatureEnabled(ProfileSelectorFeature::class.java)
        }
    val overFlowMenuEnabled =
        remember(featureManager) {
            featureManager.isFeatureEnabled(OverflowMenuFeature::class.java)
        }
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Leading slot: Cancel, then the profile selector (work / personal) when present.
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (actions != null) {
                CancelBarButton(actions)
            }
            if (profileSelectorEnabled) {
                featureManager.composeLocation(
                    Location.PROFILE_SELECTOR,
                    maxSlots = 1,
                    modifier = Modifier.padding(start = 4.dp),
                )
            } else if (actions == null) {
                Spacer(Modifier.width(MEASUREMENT_ICON_BUTTON_WIDTH))
            }
        }
        hideWhenState(
            selector =
                object : StateSelector.AnimatedVisibilityInEmbedded {
                    override val visible = LocalEmbeddedState.current?.isExpanded ?: false
                    override val enter = expandVertically(animationSpec = standardDecelerate(150))
                    override val exit = shrinkVertically(animationSpec = standardDecelerate(100))
                }
        ) {
            NavigationBarButtons(Modifier.width(MEASUREMENT_SEGMENTED_WIDTH))
        }
        // Trailing slot: Add (n), then the overflow menu when present.
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (actions != null && confirmInTitleBar) {
                AddBarButton(actions)
            }
            if (overFlowMenuEnabled) {
                featureManager.composeLocation(
                    Location.OVERFLOW_MENU,
                    modifier = Modifier.width(MEASUREMENT_ICON_BUTTON_WIDTH),
                )
            } else if (actions == null) {
                Spacer(Modifier.width(MEASUREMENT_ICON_BUTTON_WIDTH))
            }
        }
    }
}
