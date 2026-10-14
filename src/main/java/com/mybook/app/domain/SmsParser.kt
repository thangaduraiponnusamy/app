package com.mybook.app.domain

import com.mybook.app.data.ExpenseType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class SmsMessage(val sender: String, val body: String, val sentAt: Long)

/** A bank debit/credit detected in an SMS. [key] identifies the SMS so it is only imported once. */
data class SmsTransaction(
    val key: String,
    val type: ExpenseType,
    val amountMinor: Long,
    val title: String,
    val date: LocalDate,
    val categoryHint: String?,
    val body: String,
)

/** Heuristic parser for Indian bank / UPI transaction SMS. */
object SmsParser {
    private val amount = Regex("""(?i)(?:rs\.?|inr|₹)\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val debit = Regex("""(?i)\b(debited|spent|paid|withdrawn|purchased?|sent|debit|dr)\b""")
    private val credit = Regex("""(?i)\b(credited|received|deposited|refunded|credit(?!\s*card)|cr)\b""")
    private val account = Regex("""(?i)(a/c|acct|account|card|upi|bank|wallet|xx\d{2,}|\*\d{3,})""")
    private val ignore = Regex(
        """(?i)\b(otp|one time password|verification code|will be debited|will be credited|due on|due date|is due|""" +
            """minimum due|requested|collect request|declined|failed|unsuccessful|not processed|pending|offer|cashback up ?to|""" +
            """pre-?approved)\b""",
    )
    private val merchantPatterns = listOf(
        Regex("""(?i)\bat\s+([A-Za-z0-9][A-Za-z0-9 &._'-]{2,30}?)(?=\s+on\b|\s+for\b|\s+ref\b|\s+txn\b|\s+avl\b|[.,;]|$)"""),
        Regex("""(?i)\bto\s+(?:vpa\s+)?([A-Za-z0-9][A-Za-z0-9 &._@'-]{2,40}?)(?=\s+on\b|\s+ref\b|\s+upi\b|\s+via\b|[,;(]|\.\s|$)"""),
        Regex("""(?i)\bfrom\s+(?:vpa\s+)?([A-Za-z0-9][A-Za-z0-9 &._@'-]{2,40}?)(?=\s+on\b|\s+ref\b|\s+upi\b|\s+via\b|[,;(]|\.\s|$)"""),
        Regex("""(?i)\binfo[:\s]+([A-Za-z0-9][A-Za-z0-9 &._@/'-]{2,40})"""),
    )
    private val notMerchant = Regex("""(?i)^(your|you|a/c|acct|account|card|the|ac|xx|\*)""")

    private val categoryKeywords = listOf(
        "Food" to listOf("swiggy", "zomato", "restaurant", "cafe", "hotel", "dominos", "pizza", "kfc", "mcdonald"),
        "Groceries" to listOf("bigbasket", "dmart", "grocer", "blinkit", "zepto", "instamart", "jiomart", "supermarket", "more retail"),
        "Transport" to listOf("uber", "ola", "rapido", "fuel", "petrol", "hpcl", "bpcl", "iocl", "indian oil", "fastag", "metro"),
        "Travel" to listOf("irctc", "makemytrip", "goibibo", "indigo", "air india", "redbus", "cleartrip", "airline"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "croma", "reliance digital"),
        "Bills" to listOf("electricity", "bescom", "tneb", "airtel", "jio", "vodafone", "vi ", "bsnl", "recharge", "broadband", "gas", "water bill", "insurance", "emi"),
        "Rent" to listOf("rent"),
        "Health" to listOf("pharmacy", "hospital", "apollo", "medplus", "clinic", "1mg", "pharmeasy", "netmeds", "lab"),
        "Entertainment" to listOf("netflix", "bookmyshow", "hotstar", "spotify", "prime video", "pvr", "inox"),
        "Education" to listOf("school", "college", "tuition", "fees", "udemy", "byju"),
        "Salary" to listOf("salary", "sal cr", "payroll"),
        "Interest" to listOf("interest", "dividend"),
    )

    fun parse(sms: SmsMessage): SmsTransaction? {
        val body = sms.body.replace(Regex("\\s+"), " ").trim()
        if (body.isEmpty() || ignore.containsMatchIn(body) || !account.containsMatchIn(body)) return null
        val amountMatch = amount.find(body) ?: return null
        val minor = Money.parse(amountMatch.groupValues[1]) ?: return null

        val debitAt = debit.find(body)?.range?.first
        val creditAt = credit.find(body)?.range?.first
        val type = when {
            debitAt != null && (creditAt == null || debitAt < creditAt) -> ExpenseType.EXPENSE
            creditAt != null -> ExpenseType.INCOME
            else -> return null
        }

        val merchant = merchantPatterns.asSequence()
            .mapNotNull { it.find(body)?.groupValues?.get(1)?.trim()?.trimEnd('.', '-') }
            .firstOrNull { it.length >= 3 && !notMerchant.containsMatchIn(it) }
        val title = merchant?.take(MAX_TITLE) ?: if (type == ExpenseType.INCOME) "Bank credit" else "Bank debit"

        val lower = body.lowercase()
        val hint = categoryKeywords.firstOrNull { (_, words) -> words.any { it in lower } }?.first

        return SmsTransaction(
            key = key(sms),
            type = type,
            amountMinor = minor,
            title = title,
            date = Instant.ofEpochMilli(sms.sentAt).atZone(ZoneId.systemDefault()).toLocalDate(),
            categoryHint = hint,
            body = body,
        )
    }

    // Same key whether the SMS arrives live (receiver) or is read later from the inbox.
    fun key(sms: SmsMessage): String =
        "${sms.sender.uppercase()}|${sms.sentAt / 1000}|${sms.body.replace(Regex("\\s+"), " ").trim().hashCode()}"
}
