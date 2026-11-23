package com.kalaiselvan.ride.dev

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

object DevFeatureProviderImpl : DevFeatureProvider {
    override val isDevFeatureSupported: Boolean = false

    @Composable
    override fun RenderDevScreen(modifier: Modifier) {
        // No-op for production releases
    }
}
