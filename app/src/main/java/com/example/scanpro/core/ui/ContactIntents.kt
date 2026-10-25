package com.example.scanpro.core.ui

import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.ContactsContract
import android.widget.Toast

/** What is known about a person or company, to fill the system "new contact" form. */
data class ContactDraft(
    val name: String? = null,
    val company: String? = null,
    val jobTitle: String? = null,
    val phones: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val website: String? = null,
    val address: String? = null,
    val notes: String? = null
)

/**
 * Opens the phone's own "new contact" form pre-filled with [draft]. The user reviews and saves it in the
 * Contacts app, so ScanPro needs no contacts permission and never writes contacts itself.
 */
fun openNewContactForm(context: Context, draft: ContactDraft) {
    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
        type = ContactsContract.RawContacts.CONTENT_TYPE
        draft.name?.let { putExtra(ContactsContract.Intents.Insert.NAME, it) }
        draft.company?.let { putExtra(ContactsContract.Intents.Insert.COMPANY, it) }
        draft.jobTitle?.let { putExtra(ContactsContract.Intents.Insert.JOB_TITLE, it) }
        draft.address?.let { putExtra(ContactsContract.Intents.Insert.POSTAL, it) }
        draft.notes?.let { putExtra(ContactsContract.Intents.Insert.NOTES, it) }
        // The extras hold only one phone and one email, so every value goes in as a data row instead.
        val rows = ArrayList<ContentValues>()
        draft.phones.distinct().take(4).forEach { phone ->
            rows += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Phone.NUMBER, phone)
                put(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_WORK)
            }
        }
        draft.emails.distinct().take(3).forEach { email ->
            rows += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Email.ADDRESS, email)
                put(ContactsContract.CommonDataKinds.Email.TYPE, ContactsContract.CommonDataKinds.Email.TYPE_WORK)
            }
        }
        draft.website?.let { site ->
            rows += ContentValues().apply {
                put(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Website.CONTENT_ITEM_TYPE)
                put(ContactsContract.CommonDataKinds.Website.URL, site)
                put(ContactsContract.CommonDataKinds.Website.TYPE, ContactsContract.CommonDataKinds.Website.TYPE_WORK)
            }
        }
        if (rows.isNotEmpty()) putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, rows)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No contacts app found", Toast.LENGTH_SHORT).show()
    }
}
