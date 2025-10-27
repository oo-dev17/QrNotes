package com.oo_dev17.qrnotes

// In a new file: SharedViewModel.kt
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
enum class NoteAction {
    SCAN_NEW_QR_CODE, // The new action
    NONE // Default state
}
class SharedViewModel : ViewModel() {
    // LiveData to hold the ID and new gallery pic name of the note to refresh.
    private val _noteToRefresh = MutableLiveData<Pair<String, String>?>()
    val noteToRefresh: LiveData<Pair<String, String>?> = _noteToRefresh

    // Called from SecondFragment after a successful update.
    fun requestThumbnailRefresh(noteId: String, newGalleryPicName: String) {
        _noteToRefresh.value = Pair(noteId, newGalleryPicName)
    }

    // Called from FirstFragment after the refresh is handled.
    fun onThumbnailRefreshHandled() {
        _noteToRefresh.value = null
    }
}
class SecondSharedViewModel : ViewModel() {
    private val _requestedAction = MutableLiveData<NoteAction>()
    val requestedAction: LiveData<NoteAction> = _requestedAction

    fun requestAction(action: NoteAction) {
        _requestedAction.value = action
    }

    // Call this from SecondFragment after the action has been handled
    fun onActionHandled() {
        _requestedAction.value = NoteAction.NONE
    }
}