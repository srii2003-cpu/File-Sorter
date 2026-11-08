package com.narasa.imageorganiser.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.narasa.imageorganiser.R
import com.narasa.imageorganiser.UiState

/** Shown while the app has no photo access: one explanation, one button. */
@Composable
fun AccessScreen(state: UiState, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.access_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.access_body), style = MaterialTheme.typography.bodyLarge)
            val (label, action) = when {
                state.permanentlyDenied -> R.string.access_open_settings to onOpenSettings
                state.askedOnce -> R.string.access_try_again to onAllow
                else -> R.string.access_allow to onAllow
            }
            Button(onClick = action, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(stringResource(label))
            }
        }
    }
}
