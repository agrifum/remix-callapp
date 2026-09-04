package com.example.data.database

import android.content.Context
import androidx.room3.Database
import androidx.room3.Room
import androidx.room3.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AiSuggestionDao
import com.example.data.dao.CallDraftDao
import com.example.data.dao.ClientDao
import com.example.data.dao.JobAnalysisWindowDao
import com.example.data.dao.JobDao
import com.example.data.dao.NoteDao
import com.example.data.dao.ReengagementEventDao
import com.example.data.dao.ServiceDao
import com.example.data.dao.SmsTemplateDao
import com.example.data.dao.SmsTriggerDao
import com.example.data.dao.TaskDao
import com.example.data.entity.AiSuggestionEntity
import com.example.data.entity.CallDraftEntity
import com.example.data.entity.ClientEntity
import com.example.data.entity.JobAnalysisWindowEntity
import com.example.data.entity.JobEntity
import com.example.data.entity.NoteEntity
import com.example.data.entity.ReengagementEventEntity
import com.example.data.entity.ServiceEntity
import com.example.data.entity.SmsTemplateEntity
import com.example.data.entity.SmsTriggerEntity
import com.example.data.entity.TaskEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

@Database(
    entities = [
        ClientEntity::class,
        NoteEntity::class,
        TaskEntity::class,
        ServiceEntity::class,
        JobEntity::class,
        JobAnalysisWindowEntity::class,
        AiSuggestionEntity::class,
        SmsTriggerEntity::class,
        ReengagementEventEntity::class,
        SmsTemplateEntity::class,
        CallDraftEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CallUppDatabase : RoomDatabase() {

    abstract fun clientDao(): ClientDao
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun serviceDao(): ServiceDao
    abstract fun jobDao(): JobDao
    abstract fun jobAnalysisWindowDao(): JobAnalysisWindowDao
    abstract fun aiSuggestionDao(): AiSuggestionDao
    abstract fun smsTriggerDao(): SmsTriggerDao
    abstract fun reengagementEventDao(): ReengagementEventDao
    abstract fun smsTemplateDao(): SmsTemplateDao
    abstract fun callDraftDao(): CallDraftDao

    companion object {
        @Volatile
        private var INSTANCE: CallUppDatabase? = null

        fun getInstance(context: Context): CallUppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CallUppDatabase::class.java,
                    "callupp.db"
                ).addCallback(object : Callback() {
                    override suspend fun onCreate(connection: androidx.sqlite.SQLiteConnection) {
                        super.onCreate(connection)
                        CoroutineScope(Dispatchers.IO).launch {
                            seedDefaults(getInstance(context))
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }

        suspend fun seedDefaults(db: CallUppDatabase) {
            if (db.serviceDao().getServiceCount() == 0) {
                val services = listOf(
                    ServiceEntity(name = "Diagnoza / Wycena", defaultPriceMinor = 10000L, sortOrder = 1),
                    ServiceEntity(name = "Naprawa standardowa", defaultPriceMinor = 25000L, sortOrder = 2),
                    ServiceEntity(name = "Montaż / Instalacja", defaultPriceMinor = 35000L, sortOrder = 3),
                    ServiceEntity(name = "Serwis / Konserwacja", defaultPriceMinor = 20000L, sortOrder = 4),
                    ServiceEntity(name = "Konsultacja techniczna", defaultPriceMinor = 15000L, sortOrder = 5)
                )
                services.forEach { db.serviceDao().insertService(it) }
            }

            if (db.smsTemplateDao().getTemplateCount() == 0) {
                val templates = listOf(
                    SmsTemplateEntity(
                        name = "Potwierdzenie terminu",
                        body = "Dzień dobry {name}, potwierdzam termin zlecenia: {date} o godz. {time}. Adres: {address}. Pozdrawiam!",
                        sortOrder = 1
                    ),
                    SmsTemplateEntity(
                        name = "Będę za chwilę",
                        body = "Dzień dobry, jestem w drodze pod adres: {address}. Przewidywany czas dojazdu: {travel_time}, będę ok. {arrival_time}.",
                        sortOrder = 2
                    ),
                    SmsTemplateEntity(
                        name = "Podsumowanie zlecenia",
                        body = "Dzień dobry {name}, dziękuję za współpracę. Zlecenie: {service}, do zapłaty: {price}. Pozdrawiam!",
                        sortOrder = 3
                    ),
                    SmsTemplateEntity(
                        name = "Opóźnienie",
                        body = "Dzień dobry {name}, z przyczyn losowych będę ok. {arrival_time}. Przepraszam za opóźnienie!",
                        sortOrder = 4
                    ),
                    SmsTemplateEntity(
                        name = "Prośba o kontakt",
                        body = "Dzień dobry, próbowałem się dodzwonić w sprawie zlecenia. Proszę o telefon zwrotny w dogodnej chwili.",
                        sortOrder = 5
                    )
                )
                templates.forEach { db.smsTemplateDao().insertTemplate(it) }
            }
        }
    }
}
