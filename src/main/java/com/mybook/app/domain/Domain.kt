package com.mybook.app.domain

import com.mybook.app.data.CategoryEntity
import com.mybook.app.data.CategoryKind
import com.mybook.app.data.ExpenseEntity
import com.mybook.app.data.ExpenseType
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Month
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID
import kotlin.math.abs

const val MAX_TITLE = 120
const val MAX_NOTE = 500
const val MAX_NAME = 40

/** All amounts are INR, stored as Long paise to avoid floating point errors. */
object Money {
    private val india = Locale("en", "IN")

    fun format(amountMinor: Long): String =
        NumberFormat.getCurrencyInstance(india).format(BigDecimal.valueOf(amountMinor, 2))

    /** Short form for tight spaces: ₹950, ₹3.2k, ₹4.5L, ₹1.2Cr. */
    fun compact(amountMinor: Long): String {
        val rupees = abs(amountMinor) / 100.0
        val sign = if (amountMinor < 0) "-" else ""
        val text = when {
            rupees >= 1_00_00_000 -> "%.1fCr".format(Locale.US, rupees / 1_00_00_000)
            rupees >= 1_00_000 -> "%.1fL".format(Locale.US, rupees / 1_00_000)
            rupees >= 1_000 -> "%.1fk".format(Locale.US, rupees / 1_000)
            else -> "%.0f".format(Locale.US, rupees)
        }
        return "$sign₹${text.replace(".0", "")}"
    }

    fun toPlain(amountMinor: Long): String = BigDecimal.valueOf(amountMinor, 2).toPlainString()

    fun parse(input: String): Long? {
        val normalized = input.trim().replace(",", "")
        return runCatching {
            BigDecimal(normalized).setScale(2, RoundingMode.HALF_UP).unscaledValue().longValueExact()
        }.getOrNull()?.takeIf { it > 0 }
    }
}

object DefaultCategories {
    private val expense = listOf(
        Triple("Food", "🍔", 0xFF10B981),
        Triple("Groceries", "🛒", 0xFF22C55E),
        Triple("Transport", "🚗", 0xFFF59E0B),
        Triple("Shopping", "🛍️", 0xFFEC4899),
        Triple("Bills", "🧾", 0xFF3B82F6),
        Triple("Rent", "🏠", 0xFF6366F1),
        Triple("Health", "💊", 0xFFEF4444),
        Triple("Entertainment", "🎬", 0xFF8B5CF6),
        Triple("Travel", "✈️", 0xFF06B6D4),
        Triple("Education", "📚", 0xFFA16207),
        Triple("Gifts", "🎁", 0xFFF472B6),
        Triple("Other", "📌", 0xFF94A3B8),
    )
    private val income = listOf(
        Triple("Salary", "💼", 0xFF22C55E),
        Triple("Business", "🏪", 0xFF3B82F6),
        Triple("Rent received", "🏘️", 0xFF8B5CF6),
        Triple("Interest", "📈", 0xFF06B6D4),
        Triple("Gift", "🎁", 0xFFF472B6),
        Triple("Other income", "💰", 0xFF94A3B8),
    )

    fun all(): List<CategoryEntity> =
        expense.mapIndexed { i, (name, emoji, color) -> category(name, emoji, color, CategoryKind.EXPENSE, i) } +
            income.mapIndexed { i, (name, emoji, color) -> category(name, emoji, color, CategoryKind.INCOME, 100 + i) }

    private fun category(name: String, emoji: String, color: Long, kind: CategoryKind, order: Int) =
        CategoryEntity(UUID.randomUUID().toString(), name, emoji, color, kind, order)
}

/** Colours offered when creating or editing a category. */
val CategoryPalette = listOf(
    0xFF10B981, 0xFF22C55E, 0xFFF59E0B, 0xFFEF4444, 0xFFEC4899, 0xFF8B5CF6,
    0xFF6366F1, 0xFF3B82F6, 0xFF06B6D4, 0xFFA16207, 0xFFF472B6, 0xFF94A3B8,
)

enum class Period(val label: String) {
    WEEK("Week"),
    MONTH("Month"),
    YEAR("Year");

    fun range(anchor: LocalDate): ClosedRange<LocalDate> = when (this) {
        WEEK -> anchor.with(java.time.DayOfWeek.MONDAY).let { it..it.plusDays(6) }
        MONTH -> anchor.withDayOfMonth(1)..anchor.withDayOfMonth(anchor.lengthOfMonth())
        YEAR -> anchor.withDayOfYear(1)..anchor.withDayOfYear(anchor.lengthOfYear())
    }

    fun shift(anchor: LocalDate, delta: Long): LocalDate = when (this) {
        WEEK -> anchor.plusWeeks(delta)
        MONTH -> anchor.plusMonths(delta)
        YEAR -> anchor.plusYears(delta)
    }

    fun title(anchor: LocalDate): String = when (this) {
        WEEK -> range(anchor).let { "${it.start.format(DAY_MONTH)} – ${it.endInclusive.format(DAY_MONTH)}" }
        MONTH -> anchor.format(MONTH_YEAR)
        YEAR -> anchor.year.toString()
    }

    private companion object {
        val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM")
        val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy")
    }
}

data class Transfer(val from: String, val to: String, val amountMinor: Long)

object Balances {
    /** Keyed by member id. Positive = gets money back; negative = owes. Income is ignored. */
    fun compute(expenses: List<ExpenseEntity>): Map<String, Long> {
        val balances = mutableMapOf<String, Long>()
        fun add(memberId: String, amount: Long) {
            balances[memberId] = (balances[memberId] ?: 0L) + amount
        }
        for (expense in expenses) {
            if (expense.type == ExpenseType.INCOME) continue
            val people = expense.splitWith.ifEmpty { listOf(expense.paidBy) }.sorted()
            add(expense.paidBy, expense.amountMinor)
            val share = expense.amountMinor / people.size
            val remainder = expense.amountMinor % people.size
            people.forEachIndexed { index, person -> add(person, -(share + if (index < remainder) 1 else 0)) }
        }
        return balances
    }

    /** Greedy minimal list of payments that settles all balances. */
    fun settleUp(balances: Map<String, Long>): List<Transfer> {
        class Party(val id: String, var amount: Long)
        val creditors = balances.filterValues { it > 0 }.map { Party(it.key, it.value) }.sortedByDescending { it.amount }
        val debtors = balances.filterValues { it < 0 }.map { Party(it.key, -it.value) }.sortedByDescending { it.amount }
        val transfers = mutableListOf<Transfer>()
        var i = 0
        var j = 0
        while (i < debtors.size && j < creditors.size) {
            val pay = minOf(debtors[i].amount, creditors[j].amount)
            if (pay > 0) transfers += Transfer(debtors[i].id, creditors[j].id, pay)
            debtors[i].amount -= pay
            creditors[j].amount -= pay
            if (debtors[i].amount == 0L) i++
            if (creditors[j].amount == 0L) j++
        }
        return transfers
    }
}

data class CategoryTotal(val category: CategoryEntity?, val amountMinor: Long) {
    val name: String get() = category?.name ?: "Uncategorised"
    val emoji: String get() = category?.emoji ?: "📌"
    val color: Long get() = category?.color ?: 0xFF94A3B8
}

object Reports {
    const val MIN_YEARS = 5

    fun spending(expenses: List<ExpenseEntity>) = expenses.filter { it.type == ExpenseType.EXPENSE }

    fun income(expenses: List<ExpenseEntity>) = expenses.filter { it.type == ExpenseType.INCOME }

    fun byCategory(expenses: List<ExpenseEntity>, categories: Map<String, CategoryEntity>): List<CategoryTotal> =
        expenses.groupBy { it.categoryId }
            .map { (id, items) -> CategoryTotal(categories[id], items.sumOf { it.amountMinor }) }
            .sortedByDescending { it.amountMinor }

    fun byPayer(expenses: List<ExpenseEntity>, names: Map<String, String>): List<Pair<String, Long>> =
        expenses.groupBy { names[it.paidBy] ?: "Unknown" }
            .map { (name, items) -> name to items.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }

    fun byMonth(expenses: List<ExpenseEntity>, year: Int): List<Pair<String, Long>> =
        (1..12).map { month ->
            Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault()) to
                expenses.filter { it.date.year == year && it.date.monthValue == month }.sumOf { it.amountMinor }
        }

    /** At least the last [MIN_YEARS] years, extended back to the oldest entry. */
    fun yearRange(expenses: List<ExpenseEntity>, currentYear: Int): IntRange {
        val oldest = expenses.minOfOrNull { it.date.year } ?: currentYear
        return minOf(oldest, currentYear - MIN_YEARS + 1)..currentYear
    }

    fun byYear(expenses: List<ExpenseEntity>, years: IntRange): List<Pair<String, Long>> =
        years.map { year -> year.toString() to expenses.filter { it.date.year == year }.sumOf { it.amountMinor } }
}

object CsvExporter {
    fun write(
        out: OutputStream,
        expenses: List<ExpenseEntity>,
        names: Map<String, String>,
        categories: Map<String, String>,
    ) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.write("\uFEFF") // BOM so Excel detects UTF-8
        writer.write(
            listOf("Date", "Year", "Month", "Type", "Title", "Category", "Amount (INR)", "Paid / received by", "Split with", "Note", "Receipt")
                .joinToString(","),
        )
        writer.newLine()
        expenses.sortedBy { it.date }.forEach { e ->
            val row = listOf(
                e.date.toString(),
                e.date.year.toString(),
                e.date.monthValue.toString(),
                e.type.name.lowercase(),
                text(e.title),
                text(categories[e.categoryId].orEmpty()),
                Money.toPlain(e.amountMinor),
                text(names[e.paidBy].orEmpty()),
                text(e.splitWith.joinToString("; ") { names[it].orEmpty() }),
                text(e.note),
                if (e.receiptPath != null) "yes" else "",
            )
            writer.write(row.joinToString(",") { escape(it) })
            writer.newLine()
        }
        writer.flush()
    }

    // Neutralise spreadsheet formulas in user-entered text (CSV injection).
    private fun text(value: String): String {
        val first = value.firstOrNull() ?: return value
        return if (first in "=+-@\t\r") "'$value" else value
    }

    private fun escape(value: String) =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}
