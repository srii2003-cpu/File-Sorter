package com.narasa.imageorganiser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.narasa.imageorganiser.IndexStatus
import com.narasa.imageorganiser.R
import com.narasa.imageorganiser.UiState
import com.narasa.imageorganiser.permission.PhotoAccess

/** Phase 0 stand-in for Home: shows access and index state. Real Home arrives in Phase 2. */
@Composable
fun HomePlaceholder(state: UiState, onAllowAll: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.access == PhotoAccess.PARTIAL) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .clickable(role = Role.Button, onClick = onAllowAll),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.partial_banner, state.selectedCount ?: 0), style = MaterialTheme.typography.titleSmall)
                        Text(stringResource(R.string.partial_banner_note), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(stringResource(R.string.home_access, state.access.name), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.home_index, stringResource(state.index.label())), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(R.string.home_phase_note), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun IndexStatus.label(): Int = when (this) {
    IndexStatus.OPENING -> R.string.index_opening
    IndexStatus.READY -> R.string.index_ready
    IndexStatus.REBUILT -> R.string.index_rebuilt
    IndexStatus.FAILED -> R.string.index_failed
}
