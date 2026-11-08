package com.narasa.imageorganiser

import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.narasa.imageorganiser.data.db.DatabaseProvider
import com.narasa.imageorganiser.permission.PhotoAccess
import com.narasa.imageorganiser.permission.PhotoPermissions
import com.narasa.imageorganiser.permission.SelectedPhotoCounter
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class IndexStatus { OPENING, READY, REBUILT, FAILED }

data class UiState(
    val access: PhotoAccess,
    val askedOnce: Boolean = false,
    val permanentlyDenied: Boolean = false,
    val selectedCount: Int? = null,
    val index: IndexStatus = IndexStatus.OPENING,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val databaseProvider: DatabaseProvider,
    private val photoCounter: SelectedPhotoCounter,
) : ViewModel() {
    private val _state = MutableStateFlow(UiState(access = PhotoAccess.NONE))
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) {
                runCatching {
                    databaseProvider.get()
                    if (databaseProvider.lastOpenRebuilt) IndexStatus.REBUILT else IndexStatus.READY
                }.getOrDefault(IndexStatus.FAILED)
            }
            _state.update { it.copy(index = status) }
        }
    }

    /** Called on every app open, so a permission revoked in Settings is noticed. */
    fun refreshAccess(isGranted: (String) -> Boolean) {
        val access = PhotoPermissions.access(Build.VERSION.SDK_INT, isGranted)
        _state.update { it.copy(access = access, permanentlyDenied = access == PhotoAccess.NONE && it.permanentlyDenied) }
        refreshCount(access)
    }

    /** [showRationale] false after a denial means Android will no longer show the dialog. */
    fun onPermissionResult(isGranted: (String) -> Boolean, showRationale: Boolean) {
        val access = PhotoPermissions.access(Build.VERSION.SDK_INT, isGranted)
        _state.update {
            it.copy(access = access, askedOnce = true, permanentlyDenied = access == PhotoAccess.NONE && !showRationale)
        }
        refreshCount(access)
    }

    private fun refreshCount(access: PhotoAccess) {
        if (access != PhotoAccess.PARTIAL) {
            _state.update { it.copy(selectedCount = null) }
            return
        }
        viewModelScope.launch {
            val count = photoCounter.count()
            _state.update { it.copy(selectedCount = count) }
        }
    }
}
