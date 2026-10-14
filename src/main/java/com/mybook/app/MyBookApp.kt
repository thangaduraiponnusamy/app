package com.mybook.app

import android.app.Application
import android.content.Context
import com.mybook.app.data.BookRepository
import com.mybook.app.data.MyBookDatabase
import com.mybook.app.data.Prefs
import com.mybook.app.data.backup.BackupManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val prefs = Prefs(appContext)
    private val database = MyBookDatabase.create(appContext)
    val repository = BookRepository(
        context = appContext,
        db = database,
        prefs = prefs,
        receiptsDir = File(appContext.filesDir, "receipts"),
    )
    val backupManager = BackupManager(appContext, database, prefs)

    init {
        appScope.launch { repository.ensureDefaultCategories() }
        backupManager.applySchedule()
    }
}
