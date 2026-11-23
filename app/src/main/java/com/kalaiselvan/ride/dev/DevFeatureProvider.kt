package com.kalaiselvan.ride.dev

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Pluggable provider for Developer features.
 * In production builds (public releases), [isDevFeatureSupported] is false and [RenderDevScreen] is a no-op.
 * In internal builds, it unlocks the developer reverse-engineering suite.
 */
interface DevFeatureProvider {
    val isDevFeatureSupported: Boolean
    @Composable
    fun RenderDevScreen(modifier: Modifier)
}
