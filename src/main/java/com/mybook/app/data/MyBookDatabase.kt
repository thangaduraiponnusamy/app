package com.mybook.app.data

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
    entities = [
        BookEntity::class,
        MemberEntity::class,
        CategoryEntity::class,
        ExpenseEntity::class,
        ReminderEntity::class,
    ],
    version = 2,
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
        // POC: schema changes reset local data instead of migrating it.
        fun create(context: Context): MyBookDatabase =
            Room.databaseBuilder(context, MyBookDatabase::class.java, "mybook.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
