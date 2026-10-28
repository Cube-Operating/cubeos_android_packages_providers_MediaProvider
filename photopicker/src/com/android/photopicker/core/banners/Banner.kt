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

package com.android.photopicker.core.banners

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.android.photopicker.R
import com.android.photopicker.core.configuration.LocalPhotopickerConfiguration
import com.android.photopicker.core.events.Event
import com.android.photopicker.core.events.LocalEvents
import com.android.photopicker.core.events.Telemetry.BannerType
import com.android.photopicker.core.events.Telemetry.UserBannerInteraction
import com.android.photopicker.core.features.FeatureToken.CORE
import kotlinx.coroutines.launch

/**
 * Object interface to generate a banner element for the UI. This abstracts the appearance of the
 * banner away from individual banner owners, and provides a common api for passing around a banner
 * implementation between classes. Ultimately, these objects are used to build the banner in the UI,
 * but leaves the actual UI implementation of the banner to the call site.
 */
interface Banner {

    /** The [BannerDeclaration] of the banner. */
    val declaration: BannerDeclaration

    /**
     * [Composable] function that returns a localized title string for the banner.
     *
     * @see [stringResource] to fetch a localized resource from a composable.
     */
    @Composable fun buildTitle(): String

    /**
     * [Composable] function that returns a localized message string for the banner.
     *
     * @see [stringResource] to fetch a localized resource from a composable.
     */
    @Composable fun buildMessage(): String

    /**
     * [Composable] function that returns a localized action string for the banner. This is the
     * action that is associated with the banner, but it's display implementation and exact
     * placement is up to the implementer.
     *
     * @see [stringResource] to fetch a localized resource from a composable.
     */
    @Composable
    fun actionLabel(): String? {
        return null
    }

    /**
     * A callback for when the banner's action is invoked by the user.
     *
     * @param context The current context is provided to this callback.
     */
    fun onAction(context: Context) {}

    /**
     * An (optional) icon that may be associated with the Banner. Exact display details are up to
     * the implementation.
     */
    @Composable
    fun getIcon(): ImageVector? {
        return null
    }

    /**
     * [Composable] function that returns an optional localized content description for the provided
     * icon. This has no effect if no icon is provided.
     */
    @Composable
    fun iconContentDescription(): String? {
        return null
    }
}

private val MEASUREMENT_BANNER_CARD_INTERNAL_PADDING =
    PaddingValues(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 8.dp)
private val MEASUREMENT_BANNER_ICON_GAP_SIZE = 16.dp
private val MEASUREMENT_BANNER_ICON_SIZE = 32.dp
private val MEASUREMENT_BANNER_ICON_PADDING = 4.dp
private val MEASUREMENT_BANNER_BUTTON_ROW_SPACING = 8.dp
private val MEASUREMENT_BANNER_TITLE_BOTTOM_SPACING = 6.dp
private val MEASUREMENT_BANNER_TEXT_END_PADDING = 8.dp

/**
 * A default compose implementation that relies on the [Banner] interface for all backing data.
 *
 * @param banner The [Banner] to display
 * @param modifier A UI modifier for positioning the element which is applied to the top level card
 *   that wraps the entire banner
 * @param onDismiss Called when the banner is dismissed by the user. This has no effect if the
 *   underlying [Bannerdeclaration] does not allow for a dismissable banner.
 */
@Composable
fun Banner(
    banner: Banner,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
) {

    val config = LocalPhotopickerConfiguration.current
    val events = LocalEvents.current

    // CubeOS: the privacy note is shown like iOS does it — a small secondary footnote, not a card.
    if (banner.declaration == BannerDefinitions.PRIVACY_EXPLAINER) {
        PrivacyFootnote(banner, modifier, onDismiss)
    } else {
        Card(
            // Consume the incoming modifier for positioning the banner.
            modifier = modifier,
            colors =
                CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
            // CubeOS: iOS grouped cell corner radius.
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(Modifier.padding(MEASUREMENT_BANNER_CARD_INTERNAL_PADDING)) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(MEASUREMENT_BANNER_ICON_GAP_SIZE),
                ) {
                    // Not all Banners provide an Icon
                    banner.getIcon()?.let {
                        Icon(
                            it,
                            contentDescription = banner.iconContentDescription(),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier =
                                Modifier.size(MEASUREMENT_BANNER_ICON_SIZE)
                                    .padding(MEASUREMENT_BANNER_ICON_PADDING)
                        )
                    }

                    // Stack the title and message vertically in the same horizontal container
                    // weight(1f) is used to ensure that the other siblings in this row are displayed,
                    // and this column will fill any remaining space.
                    Column(
                        modifier =
                            Modifier.padding(PaddingValues(end = MEASUREMENT_BANNER_TEXT_END_PADDING))
                                .weight(1f)
                    ) {
                        if (banner.buildTitle().isNotEmpty()) {
                            Text(
                                text = banner.buildTitle(),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier =
                                    Modifier.align(Alignment.Start)
                                        .padding(bottom = MEASUREMENT_BANNER_TITLE_BOTTOM_SPACING)
                            )
                        }
                        Text(
                            text = banner.buildMessage(),
                            fontSize = 13.sp,
                            modifier = Modifier.align(Alignment.Start),
                        )
                    }
                }

                // The action Row, which sometimes may be empty if the banner is not dismissable and
                // does not provide its own Action
                if (banner.declaration.dismissable || banner.actionLabel() != null) {
                    val scope = rememberCoroutineScope()

                    Row(
                        horizontalArrangement =
                            Arrangement.spacedBy(MEASUREMENT_BANNER_BUTTON_ROW_SPACING),
                        modifier = Modifier.align(Alignment.End)
                    ) {

                        // It's possible that a Banner provides an onAction implementation, but does not
                        // provide an actionLabel() implementation. Since the banner has no idea what
                        // the action might do, there's no way to guess at a label, so only show the
                        // additional action button when the label has been set.
                        banner.actionLabel()?.let {
                            val context = LocalContext.current
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        events.dispatch(
                                            Event.LogPhotopickerBannerInteraction(
                                                dispatcherToken = CORE.token,
                                                sessionId = config.sessionId,
                                                bannerType =
                                                    BannerType.fromBannerDeclaration(
                                                        banner.declaration
                                                    ),
                                                userInteraction =
                                                    UserBannerInteraction.CLICK_BANNER_ACTION_BUTTON
                                            )
                                        )
                                    }
                                    banner.onAction(context)
                                },
                            ) {
                                Text(it)
                            }
                        }

                        // If the banner can be dismissed per the [BannerDeclaration], then the dismiss
                        // button needs to be shown to the user. What happens when the dismiss button is
                        // clicked is up to the caller. A core string is used here to ensure consistency
                        // between banners.
                        if (banner.declaration.dismissable) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        events.dispatch(
                                            Event.LogPhotopickerBannerInteraction(
                                                dispatcherToken = CORE.token,
                                                sessionId = config.sessionId,
                                                bannerType =
                                                    BannerType.fromBannerDeclaration(
                                                        banner.declaration
                                                    ),
                                                userInteraction =
                                                    UserBannerInteraction.CLICK_BANNER_DISMISS_BUTTON
                                            )
                                        )
                                    }
                                    onDismiss()
                                }
                            ) {
                                Text(stringResource(R.string.photopicker_dismiss_banner_button_label))
                            }
                        }
                    }
                }
            }
        }
    }

    // Add a log that the banner was shown.
    LaunchedEffect(banner) {
        events.dispatch(
            Event.LogPhotopickerBannerInteraction(
                dispatcherToken = CORE.token,
                sessionId = config.sessionId,
                bannerType = BannerType.fromBannerDeclaration(banner.declaration),
                // TODO(b/357010907): Add banner shown interaction when the atom exists.
                userInteraction = UserBannerInteraction.UNSET_BANNER_INTERACTION
            )
        )
    }
}

/**
 * CubeOS: the privacy explainer as an iOS-style footnote — small secondary text with an optional
 * small dismiss glyph. Same dismiss behaviour and telemetry as the card's Dismiss button.
 */
@Composable
private fun PrivacyFootnote(banner: Banner, modifier: Modifier, onDismiss: () -> Unit) {
    val config = LocalPhotopickerConfiguration.current
    val events = LocalEvents.current
    val scope = rememberCoroutineScope()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = banner.buildMessage(),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        if (banner.declaration.dismissable) {
            IconButton(
                modifier = Modifier.size(32.dp),
                onClick = {
                    scope.launch {
                        events.dispatch(
                            Event.LogPhotopickerBannerInteraction(
                                dispatcherToken = CORE.token,
                                sessionId = config.sessionId,
                                bannerType = BannerType.fromBannerDeclaration(banner.declaration),
                                userInteraction = UserBannerInteraction.CLICK_BANNER_DISMISS_BUTTON
                            )
                        )
                    }
                    onDismiss()
                },
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription =
                        stringResource(R.string.photopicker_dismiss_banner_button_label),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
