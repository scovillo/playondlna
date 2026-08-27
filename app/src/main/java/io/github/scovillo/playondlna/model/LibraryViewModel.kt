package io.github.scovillo.playondlna.model

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.scovillo.playondlna.persistence.LibraryManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(private val libraryManager: LibraryManager) : ViewModel() {
    private val _items = mutableStateOf<List<LibraryItem>>(emptyList())
    val items: State<List<LibraryItem>> = _items

    private val _isLoading = mutableStateOf(false)
    val isLoading: State<Boolean> = _isLoading

    fun loadLibrary() {
        viewModelScope.launch {
            _isLoading.value = true
            val result =
                withContext(Dispatchers.IO) {
                    libraryManager.fetchAllItems()
                }
            _items.value = result
            _isLoading.value = false
        }
    }
}
