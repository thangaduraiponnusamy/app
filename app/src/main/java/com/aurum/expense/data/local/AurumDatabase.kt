package com.aurum.expense.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import java.time.LocalDate

class Converters {
    @TypeConverter
    fun fromDate(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun toDate(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun fromList(value: List<String>): String = value.joinToString("\n")

    @TypeConverter
    fun toList(value: String): List<String> = if (value.isEmpty()) emptyList() else value.split("\n")
}

@Database(
    entities = [TenantEntity::class, MemberEntity::class, ExpenseEntity::class, SyncFileEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AurumDatabase : RoomDatabase() {
    abstract fun tenantDao(): TenantDao
    abstract fun memberDao(): MemberDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun syncFileDao(): SyncFileDao

    companion object {
        fun create(context: Context): AurumDatabase =
            Room.databaseBuilder(context, AurumDatabase::class.java, "aurum.db").build()
    }
}
