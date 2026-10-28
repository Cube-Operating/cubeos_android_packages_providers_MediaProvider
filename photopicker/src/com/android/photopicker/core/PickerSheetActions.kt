/*
 * Copyright 2026 The CubeOS Project
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

package com.android.photopicker.core

import androidx.compose.runtime.compositionLocalOf

/**
 * CubeOS: the iOS-style title bar of the full screen (bottom sheet) picker shows "Cancel" on the
 * left and "Add" on the right. These are the very same callbacks the sheet already used for a
 * swipe-down dismissal and for the selection bar's Done button, exposed to the navigation bar so
 * that no new behaviour is introduced.
 *
 * This is null in the embedded photopicker, where the host app owns those controls.
 *
 * @property onCancel dismisses the picker exactly like dragging the sheet away.
 * @property onConfirm confirms the current selection exactly like the selection bar's Done.
 */
class PickerSheetActions(val onCancel: () -> Unit, val onConfirm: () -> Unit)

val LocalPickerSheetActions = compositionLocalOf<PickerSheetActions?> { null }
