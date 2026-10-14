package com.mybook.app.domain

import com.mybook.app.data.ExpenseEntity
import com.mybook.app.data.ExpenseType
import java.io.OutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.Month
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

const val MAX_TITLE = 120
const val MAX_NOTE = 500
const val MAX_NAME = 40

enum class Category(val label: String, val emoji: String) {
    FOOD("Groceries", "🛒"),
    DINING("Dining out", "🍽️"),
    HOUSING("Rent & housing", "🏠"),
    UTILITIES("Utilities", "💡"),
    TRANSPORT("Transport", "🚗"),
    HEALTH("Health", "💊"),
    SHOPPING("Shopping", "🛍️"),
    ENTERTAINMENT("Entertainment", "🎬"),
    TRAVEL("Travel", "✈️"),
    EDUCATION("Education", "📚"),
    KIDS("Kids", "🧸"),
    GIFTS("Gifts", "🎁"),
    OTHER("Other", "📌");

    companion object {
        fun from(name: String): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

/** Amounts are stored as Long minor units (e.g. paise/cents) to avoid floating point errors. */
object Money {
    const val DEFAULT_CURRENCY = "INR"

    fun format(amountMinor: Long, currencyCode: String): String {
        val currency = currencyOf(currencyCode)
        val digits = digits(currency)
        val formatter = NumberFormat.getCurrencyInstance().apply {
            this.currency = currency
            minimumFractionDigits = digits
            maximumFractionDigits = digits
        }
        return formatter.format(BigDecimal.valueOf(amountMinor, digits))
    }

    fun toPlain(amountMinor: Long, currencyCode: String): String =
        BigDecimal.valueOf(amountMinor, digits(currencyOf(currencyCode))).toPlainString()

    fun parse(input: String, currencyCode: String): Long? {
        val trimmed = input.trim()
        val normalized = if (trimmed.contains('.')) trimmed.replace(",", "") else trimmed.replace(',', '.')
        return runCatching {
            BigDecimal(normalized)
                .setScale(digits(currencyOf(currencyCode)), RoundingMode.HALF_UP)
                .unscaledValue()
                .longValueExact()
        }.getOrNull()?.takeIf { it > 0 }
    }

    fun isValidCurrency(code: String): Boolean = runCatching { Currency.getInstance(code) }.isSuccess

    private fun currencyOf(code: String): Currency = runCatching { Currency.getInstance(code) }.getOrElse { Currency.getInstance(DEFAULT_CURRENCY) }

    private fun digits(currency: Currency) = currency.defaultFractionDigits.coerceAtLeast(0)
}

data class Transfer(val from: String, val to: String, val amountMinor: Long)

object Balances {
    /** Keyed by member id. Positive = gets money back; negative = owes. */
    fun compute(expenses: List<ExpenseEntity>): Map<String, Long> {
        val balances = mutableMapOf<String, Long>()
        fun add(memberId: String, amount: Long) {
            balances[memberId] = (balances[memberId] ?: 0L) + amount
        }
        for (expense in expenses) {
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

object Reports {
    const val MIN_YEARS = 5

    fun spending(expenses: List<ExpenseEntity>) = expenses.filter { it.type == ExpenseType.EXPENSE }

    fun byCategory(expenses: List<ExpenseEntity>): List<Pair<String, Long>> =
        expenses.groupBy { Category.from(it.category) }
            .map { (category, items) -> "${category.emoji} ${category.label}" to items.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }

    fun byPayer(expenses: List<ExpenseEntity>, names: Map<String, String>): List<Pair<String, Long>> =
        expenses.groupBy { names[it.paidBy] ?: "Unknown" }
            .map { (name, items) -> name to items.sumOf { it.amountMinor } }
            .sortedByDescending { it.second }

    fun byMonth(expenses: List<ExpenseEntity>, year: Int): List<Pair<String, Long>> =
        (1..12).map { month ->
            Month.of(month).getDisplayName(TextStyle.SHORT, Locale.getDefault()) to
                expenses.filter { it.date.year == year && it.date.monthValue == month }.sumOf { it.amountMinor }
        }

    /** At least the last [MIN_YEARS] years, extended back to the oldest expense. */
    fun yearRange(expenses: List<ExpenseEntity>, currentYear: Int): IntRange {
        val oldest = expenses.minOfOrNull { it.date.year } ?: currentYear
        return minOf(oldest, currentYear - MIN_YEARS + 1)..currentYear
    }

    fun byYear(expenses: List<ExpenseEntity>, years: IntRange): List<Pair<String, Long>> =
        years.map { year -> year.toString() to expenses.filter { it.date.year == year }.sumOf { it.amountMinor } }
}

object CsvExporter {
    fun write(out: OutputStream, expenses: List<ExpenseEntity>, currency: String, names: Map<String, String>) {
        val writer = out.bufferedWriter(Charsets.UTF_8)
        writer.write("\uFEFF") // BOM so Excel detects UTF-8
        writer.write(
            listOf("Date", "Year", "Month", "Type", "Title", "Category", "Amount", "Currency", "Paid by", "Split with", "Note", "Receipt")
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
                Category.from(e.category).label,
                Money.toPlain(e.amountMinor, currency),
                currency,
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
