package com.aurum.expense

import android.app.Application
import android.content.Context
import com.aurum.expense.data.SessionStore
import com.aurum.expense.data.drive.DriveApi
import com.aurum.expense.data.drive.DriveAuth
import com.aurum.expense.data.local.AurumDatabase
import com.aurum.expense.data.repo.ExpenseRepository
import com.aurum.expense.data.repo.TenantRepository
import com.aurum.expense.data.sync.SyncManager
import com.aurum.expense.data.sync.SyncScheduler
import java.io.File

class AurumApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncScheduler.schedulePeriodic(this)
    }
}

class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val receiptsDir = File(appContext.filesDir, "receipts")

    val session = SessionStore(appContext)
    val database = AurumDatabase.create(appContext)
    val driveAuth = DriveAuth(appContext, session)
    val drive = DriveApi { driveAuth.accessToken() }
    val syncManager = SyncManager(database, drive, session)
    val tenantRepository = TenantRepository(database, drive, session, syncManager, receiptsDir)
    val expenseRepository = ExpenseRepository(appContext, database, session, receiptsDir)
}
