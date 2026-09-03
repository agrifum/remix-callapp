package com.example.system.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import androidx.core.content.ContextCompat
import com.example.core.phone.PhoneNumberNormalizer
import com.example.data.dao.ClientDao
import com.example.data.dao.NoteDao
import com.example.data.entity.ClientEntity
import com.example.ui.model.CallRowDirection
import com.example.ui.model.CallRowUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.UUID

class CallLogRepository(
    private val context: Context,
    private val clientDao: ClientDao,
    private val noteDao: NoteDao
) {

    private val refreshTrigger = MutableStateFlow(System.currentTimeMillis())

    fun hasCallLogPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun refresh() {
        refreshTrigger.value = System.currentTimeMillis()
    }

    private fun callLogChangeFlow(): Flow<Unit> = callbackFlow {
        if (!hasCallLogPermission()) {
            trySend(Unit)
            close()
            return@callbackFlow
        }

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                CallLog.Calls.CONTENT_URI,
                true,
                observer
            )
            trySend(Unit)
        } catch (_: Exception) {
            trySend(Unit)
        }

        awaitClose {
            try {
                context.contentResolver.unregisterContentObserver(observer)
            } catch (_: Exception) {
            }
        }
    }

    fun observeFilteredCallLogs(): Flow<List<CallRowUiModel>> {
        return combine(
            refreshTrigger,
            clientDao.getAllClients(),
            noteDao.getAllPhoneKeysWithNotes()
        ) { _, clients, phoneKeysWithNotes ->
            queryFilteredCallsSync(clients, phoneKeysWithNotes.toSet())
        }.flowOn(Dispatchers.IO)
    }

    suspend fun queryFilteredCalls(): List<CallRowUiModel> = withContext(Dispatchers.IO) {
        val clients = clientDao.getAllClientsSync()
        val phoneKeysWithNotes = noteDao.getAllPhoneKeysWithNotesSync().toSet()
        queryFilteredCallsSync(clients, phoneKeysWithNotes)
    }

    private fun queryFilteredCallsSync(
        clients: List<ClientEntity>,
        phoneKeysWithNotes: Set<String>
    ): List<CallRowUiModel> {
        if (!hasCallLogPermission()) {
            return emptyList()
        }

        val clientMap = clients.associateBy { it.phoneKey }
        val results = mutableListOf<CallRowUiModel>()

        val projection = arrayOf(
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.TYPE,
            CallLog.Calls.CACHED_NAME
        )
        val sortOrder = "${CallLog.Calls.DATE} DESC"

        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(CallLog.Calls._ID)
                val numberIndex = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val dateIndex = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIndex = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val typeIndex = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val nameIndex = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)

                while (cursor.moveToNext()) {
                    val rawNumber = if (numberIndex >= 0) cursor.getString(numberIndex) else null
                    val phoneKey = PhoneNumberNormalizer.normalizeKey(rawNumber)
                    if (phoneKey.isBlank()) continue

                    val client = clientMap[phoneKey]
                    val hasNotes = phoneKeysWithNotes.contains(phoneKey)

                    // Filtering rule:
                    // Displayed only when normalized phoneKey satisfies at least one:
                    // 1) a ClientEntity exists for that phoneKey, OR
                    // 2) at least one NoteEntity exists for that phoneKey (including archived unless soft-deleted)
                    if (client == null && !hasNotes) {
                        continue
                    }

                    val id = if (idIndex >= 0) {
                        cursor.getString(idIndex) ?: UUID.randomUUID().toString()
                    } else {
                        UUID.randomUUID().toString()
                    }
                    val date = if (dateIndex >= 0) cursor.getLong(dateIndex) else System.currentTimeMillis()
                    val duration = if (durationIndex >= 0) cursor.getLong(durationIndex) else 0L
                    val type = if (typeIndex >= 0) cursor.getInt(typeIndex) else CallLog.Calls.INCOMING_TYPE
                    val cachedName = if (nameIndex >= 0) cursor.getString(nameIndex) else null

                    val direction = when (type) {
                        CallLog.Calls.OUTGOING_TYPE -> CallRowDirection.OUTGOING
                        CallLog.Calls.MISSED_TYPE, CallLog.Calls.REJECTED_TYPE -> CallRowDirection.MISSED
                        else -> CallRowDirection.INCOMING
                    }

                    val displayName = client?.displayName ?: cachedName

                    results.add(
                        CallRowUiModel(
                            id = id,
                            phoneKey = phoneKey,
                            displayNumber = PhoneNumberNormalizer.formatDisplay(phoneKey),
                            contactOrClientDisplayName = displayName,
                            timestamp = date,
                            direction = direction,
                            durationSeconds = duration,
                            isClient = client != null,
                            hasNotes = hasNotes,
                            clientId = client?.id
                        )
                    )
                }
            }
        } catch (_: SecurityException) {
            return emptyList()
        } catch (_: Exception) {
            return emptyList()
        }

        return results
    }
}
