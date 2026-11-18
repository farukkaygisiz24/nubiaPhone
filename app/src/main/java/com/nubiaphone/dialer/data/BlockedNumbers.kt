package com.nubiaphone.dialer.data

import android.content.ContentValues
import android.content.Context
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers as Table

/**
 * System block list (the same one the system Telefon and Mesajlar use). Android only lets the
 * default phone or SMS app read or change it; every call here returns a safe value otherwise.
 * Blocking is reversible, so it is done without a confirmation and offers "Geri al".
 */
object BlockedNumbers {
    data class Entry(val id: Long, val number: String)

    fun available(context: Context): Boolean =
        runCatching { BlockedNumberContract.canCurrentUserBlockNumbers(context) }.getOrDefault(false) &&
            runCatching {
                context.contentResolver.query(Table.CONTENT_URI, arrayOf(Table.COLUMN_ID), null, null, null)?.close()
                true
            }.getOrDefault(false)

    fun list(context: Context): List<Entry> =
        runCatching {
            context.contentResolver.query(
                Table.CONTENT_URI,
                arrayOf(Table.COLUMN_ID, Table.COLUMN_ORIGINAL_NUMBER),
                null,
                null,
                "${Table.COLUMN_ID} DESC",
            )?.use { c ->
                buildList { while (c.moveToNext()) add(Entry(c.getLong(0), c.getString(1).orEmpty())) }
            }
        }.getOrNull().orEmpty()

    fun isBlocked(context: Context, number: String): Boolean =
        number.isNotBlank() && runCatching { BlockedNumberContract.isBlocked(context, number) }.getOrDefault(false)

    fun block(context: Context, number: String): Boolean =
        runCatching {
            context.contentResolver.insert(
                Table.CONTENT_URI,
                ContentValues().apply { put(Table.COLUMN_ORIGINAL_NUMBER, number) },
            ) != null
        }.getOrDefault(false)

    fun unblock(context: Context, number: String): Boolean =
        runCatching { BlockedNumberContract.unblock(context, number) > 0 }.getOrDefault(false)
}
