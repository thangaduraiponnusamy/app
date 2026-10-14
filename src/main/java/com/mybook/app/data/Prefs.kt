package com.mybook.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Prefs(context: Context) {
    private val prefs = context.getSharedPreferences("mybook_prefs", Context.MODE_PRIVATE)
    private val _activeBookId = MutableStateFlow(prefs.getString(KEY_ACTIVE_BOOK, null))
    val activeBookId: StateFlow<String?> = _activeBookId.asStateFlow()

    fun setActiveBook(id: String?) {
        prefs.edit().putString(KEY_ACTIVE_BOOK, id).apply()
        _activeBookId.value = id
    }

    private companion object {
        const val KEY_ACTIVE_BOOK = "active_book"
    }
}
