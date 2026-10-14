package com.mybook.app.data

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.mybook.app.MyBookApp
import com.mybook.app.domain.SmsMessage
import com.mybook.app.domain.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Reads bank SMS from the phone's inbox. Messages are only parsed locally and never leave the device. */
class SmsReader(private val context: Context) {
    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    suspend fun readInbox(days: Int): List<SmsMessage> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val since = System.currentTimeMillis() - days * 86_400_000L
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT)
        val messages = mutableListOf<SmsMessage>()
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(since.toString()),
            "${Telephony.Sms.DATE} DESC",
        )?.use { cursor ->
            val address = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val body = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val date = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            val dateSent = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE_SENT)
            while (cursor.moveToNext()) {
                val sent = cursor.getLong(dateSent).takeIf { it > 0 } ?: cursor.getLong(date)
                messages += SmsMessage(cursor.getString(address).orEmpty(), cursor.getString(body).orEmpty(), sent)
            }
        }
        messages
    }
}

/** Adds bank transactions from incoming SMS to the open book when auto-add is switched on. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val container = (context.applicationContext as MyBookApp).container
        if (!container.prefs.smsAutoAdd.value) return
        val bookId = container.prefs.activeBookId.value ?: return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val messages = parts.groupBy { it.originatingAddress.orEmpty() }.map { (sender, pieces) ->
            SmsMessage(sender, pieces.joinToString("") { it.messageBody.orEmpty() }, pieces.first().timestampMillis)
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val transactions = messages.mapNotNull(SmsParser::parse)
                if (transactions.isNotEmpty()) container.repository.importSmsTransactions(bookId, transactions)
            } finally {
                pending.finish()
            }
        }
    }
}
