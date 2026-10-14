package com.mybook.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    entities = [
        BookEntity::class,
        MemberEntity::class,
        CategoryEntity::class,
        ExpenseEntity::class,
        ReminderEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class MyBookDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun memberDao(): MemberDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun reminderDao(): ReminderDao

    companion object {
        // v3 briefly added an SMS import table; v4 removes it again.
        private val MIGRATION_2_4 = object : Migration(2, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `sms_imports`")
            }
        }

        // POC: schema changes without a migration reset local data.
        fun create(context: Context): MyBookDatabase =
            Room.databaseBuilder(context, MyBookDatabase::class.java, "mybook.db")
                .addMigrations(MIGRATION_2_4, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
