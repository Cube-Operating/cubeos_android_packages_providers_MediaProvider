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

package com.android.photopicker.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isUnspecified
import androidx.compose.ui.platform.LocalContext
import com.android.modules.utils.build.SdkLevel
import com.android.photopicker.core.configuration.PhotopickerConfiguration
import com.android.photopicker.core.theme.typography.TypeScaleTokens
import com.android.photopicker.core.theme.typography.TypefaceNames
import com.android.photopicker.core.theme.typography.TypefaceTokens
import com.android.photopicker.core.theme.typography.TypographyTokens
import com.android.photopicker.core.theme.typography.photopickerTypography

/**
 * This composable generates all the theme related elements and creates the wrapping [MaterialTheme]
 * composable.
 *
 * Additionally, the PhotopickerTheme also creates a provider for [WindowSizeClass] allowing
 * composables downstream to react to WindowSize changes.
 */
@Composable
fun PhotopickerTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    config: PhotopickerConfiguration,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val accentColorHelper = AccentColorHelper(config.accentColor ?: -1)

    // If a custom accent color hasn't been set, use a dynamic theme for colors
    val accentColorIsNotSpecified = remember { accentColorHelper.getAccentColor().isUnspecified }

    // Assemble Light & Dark themes, both color sets are needed to generate the [FixedAccentColors].
    // CubeOS: the picker follows the system's iOS palette (accent blue, grouped greys) so that it
    // looks like the iOS Photos picker in both light and dark mode. Only colours change here.
    val darkTheme = remember {
        when (accentColorIsNotSpecified) {
            true ->
                if (SdkLevel.isAtLeastS()) dynamicDarkColorScheme(context) else darkColorScheme()
            false -> darkColorScheme()
        }.toCubeIosColors(isDark = true)
    }

    val lightTheme = remember {
        when (accentColorIsNotSpecified) {
            true ->
                if (SdkLevel.isAtLeastS()) dynamicLightColorScheme(context) else lightColorScheme()
            false -> lightColorScheme()
        }.toCubeIosColors(isDark = false)
    }

    // Choose which colorScheme to use based on if the device is in dark mode or not.
    val colorScheme =
        remember(isDarkTheme) {
            when (isDarkTheme) {
                true ->
                    if (accentColorHelper.getAccentColor().isUnspecified) {
                        darkTheme
                    } else {
                        // When an accent color has been specified, set primary and onPrimary
                        // in the theme to use the accent color.
                        darkTheme.copy(
                            primary = accentColorHelper.getAccentColor(),
                            onPrimary = accentColorHelper.getTextColorForAccentComponents()
                        )
                    }
                false ->
                    if (accentColorHelper.getAccentColor().isUnspecified) {
                        lightTheme
                    } else {
                        lightTheme.copy(
                            // When an accent color has been specified, set primary and onPrimary
                            // in the theme to use the accent color.
                            primary = accentColorHelper.getAccentColor(),
                            onPrimary = accentColorHelper.getTextColorForAccentComponents()
                        )
                    }
            }
        }
    val fixedAccentColors =
        FixedAccentColors.build(lightColors = lightTheme, darkColors = darkTheme)

    // Generate the typography for the theme based on context.
    val typefaceNames = remember(context) { TypefaceNames.get(context) }
    val typography =
        remember(typefaceNames) {
            photopickerTypography(TypographyTokens(TypeScaleTokens(TypefaceTokens(typefaceNames))))
        }

    // Calculate the current screen size
    val windowSizeClass: WindowSizeClass = calculateWindowSizeClass()

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
    ) {
        CompositionLocalProvider(
            LocalWindowSizeClass provides windowSizeClass,
            LocalFixedAccentColors provides fixedAccentColors,
            CustomAccentColorScheme provides
                AccentColorScheme(accentColorHelper = accentColorHelper),
        ) {
            content()
        }
    }
}

/*
 * CubeOS iOS palette. Values mirror the iOS system colours used across CubeOS:
 * accent systemBlue, systemGroupedBackground / secondarySystemGroupedBackground, fills and
 * secondary labels. Applied once per theme (remembered), so there is no per-frame cost.
 */
private val IOS_BLUE_DARK = Color(0xFF0A84FF)
private val IOS_BLUE_LIGHT = Color(0xFF007AFF)

private fun ColorScheme.toCubeIosColors(isDark: Boolean): ColorScheme =
    if (isDark) {
        copy(
            primary = IOS_BLUE_DARK,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF0A3A6B),
            onPrimaryContainer = Color.White,
            secondary = IOS_BLUE_DARK,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFF2C2C2E),
            onSecondaryContainer = Color.White,
            background = Color(0xFF000000),
            onBackground = Color.White,
            surface = Color(0xFF1C1C1E),
            onSurface = Color.White,
            // iOS secondaryLabel (#EBEBF5 at 60%) flattened on the dark sheet.
            onSurfaceVariant = Color(0xFF98989F),
            surfaceVariant = Color(0xFF2C2C2E),
            surfaceTint = Color.Transparent,
            surfaceDim = Color(0xFF000000),
            // Selected segment of the segmented control.
            surfaceBright = Color(0xFF636366),
            surfaceContainerLowest = Color(0xFF000000),
            surfaceContainerLow = Color(0xFF1C1C1E),
            // The sheet itself.
            surfaceContainer = Color(0xFF1C1C1E),
            // Grouped cells / banners on the sheet.
            surfaceContainerHigh = Color(0xFF2C2C2E),
            // Segmented-control track and empty thumbnail placeholders (tertiarySystemFill).
            surfaceContainerHighest = Color(0xFF323235),
            outline = Color(0xFF545458),
            outlineVariant = Color(0xFF38383A),
        )
    } else {
        copy(
            primary = IOS_BLUE_LIGHT,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD6E8FF),
            onPrimaryContainer = Color(0xFF00284F),
            secondary = IOS_BLUE_LIGHT,
            onSecondary = Color.White,
            secondaryContainer = Color(0xFFE5E5EA),
            onSecondaryContainer = Color.Black,
            background = Color(0xFFF2F2F7),
            onBackground = Color.Black,
            surface = Color(0xFFFFFFFF),
            onSurface = Color.Black,
            // iOS secondaryLabel (#3C3C43 at 60%) flattened on white.
            onSurfaceVariant = Color(0xFF8A8A8E),
            surfaceVariant = Color(0xFFF2F2F7),
            surfaceTint = Color.Transparent,
            surfaceDim = Color(0xFFF2F2F7),
            surfaceBright = Color(0xFFFFFFFF),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF2F2F7),
            surfaceContainer = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFF2F2F7),
            surfaceContainerHighest = Color(0xFFEEEEEF),
            outline = Color(0xFFC6C6C8),
            outlineVariant = Color(0xFFE5E5EA),
        )
    }
