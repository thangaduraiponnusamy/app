package com.mybook.app

import android.app.Application
import android.content.Context
import com.mybook.app.data.BookRepository
import com.mybook.app.data.MyBookDatabase
import com.mybook.app.data.Prefs
import java.io.File

class MyBookApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val prefs = Prefs(appContext)
    val repository = BookRepository(
        context = appContext,
        db = MyBookDatabase.create(appContext),
        prefs = prefs,
        receiptsDir = File(appContext.filesDir, "receipts"),
    )
}
