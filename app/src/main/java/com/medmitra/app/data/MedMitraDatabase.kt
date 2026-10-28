package com.medmitra.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "medicines")
data class MedicineSchedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dosage: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true,
    val lastTakenAtMillis: Long? = null,
    @ColumnInfo(defaultValue = "''") val instructions: String = "",
    val snoozedUntilMillis: Long? = null
)

@Entity(tableName = "medicine_dose_history")
data class MedicineDoseEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medicineId: Long,
    val medicineName: String,
    val dosage: String,
    val scheduledAtMillis: Long,
    val actionAtMillis: Long,
    val status: String
)

@Entity(tableName = "emergency_contacts")
data class EmergencyContact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phoneE164: String,
    val enabled: Boolean = true
)

@Entity(tableName = "emergency_history")
data class EmergencyHistoryEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredAtMillis: Long,
    val eventType: String,
    val locationStatus: String = "NOT_REQUESTED",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationAccuracyMeters: Float? = null,
    val smsAttemptStatus: String = "NOT_ATTEMPTED",
    val callAttemptStatus: String = "NOT_ATTEMPTED",
    val notes: String = ""
)

@Dao
interface MedicineDao {
    @Query("SELECT * FROM medicines ORDER BY hour, minute, name")
    fun observeAll(): Flow<List<MedicineSchedule>>

    @Query("SELECT * FROM medicines WHERE enabled = 1 ORDER BY hour, minute")
    suspend fun getEnabled(): List<MedicineSchedule>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(schedule: MedicineSchedule): Long

    @Update
    suspend fun update(schedule: MedicineSchedule)

    @Query("UPDATE medicines SET lastTakenAtMillis = :takenAtMillis WHERE id = :id")
    suspend fun markTaken(id: Long, takenAtMillis: Long)

    @Query("UPDATE medicines SET snoozedUntilMillis = :untilMillis WHERE id = :id")
    suspend fun setSnoozedUntil(id: Long, untilMillis: Long?)

    @Query("DELETE FROM medicines WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM medicines WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MedicineSchedule?
}

@Dao
interface MedicineDoseHistoryDao {
    @Query("SELECT * FROM medicine_dose_history ORDER BY actionAtMillis DESC")
    fun observeAll(): Flow<List<MedicineDoseEvent>>

    @Insert
    suspend fun insert(event: MedicineDoseEvent)
}

@Dao
interface EmergencyContactDao {
    @Query("SELECT * FROM emergency_contacts ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<EmergencyContact>>

    @Query("SELECT * FROM emergency_contacts WHERE enabled = 1 ORDER BY name COLLATE NOCASE")
    suspend fun getEnabled(): List<EmergencyContact>

    @Query("SELECT * FROM emergency_contacts WHERE phoneE164 = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): EmergencyContact?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(contact: EmergencyContact): Long

    @Update
    suspend fun update(contact: EmergencyContact)

    @Query("DELETE FROM emergency_contacts WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface EmergencyHistoryDao {
    @Query("SELECT * FROM emergency_history ORDER BY occurredAtMillis DESC")
    fun observeAll(): Flow<List<EmergencyHistoryEvent>>

    @Query("SELECT * FROM emergency_history WHERE eventType = 'FALL_ALERT_PENDING' ORDER BY occurredAtMillis DESC LIMIT 1")
    suspend fun latestPendingFallAlert(): EmergencyHistoryEvent?

    @Insert
    suspend fun insert(event: EmergencyHistoryEvent): Long

    @Query("UPDATE emergency_history SET eventType = :eventType, notes = :notes WHERE id = :id")
    suspend fun updateEvent(id: Long, eventType: String, notes: String)

    @Query("UPDATE emergency_history SET eventType = :eventType, notes = :notes WHERE id = (SELECT id FROM emergency_history WHERE eventType = 'FALL_ALERT_PENDING' ORDER BY occurredAtMillis DESC LIMIT 1)")
    suspend fun resolveLatestPendingFallAlert(eventType: String, notes: String)

    @Query("UPDATE emergency_history SET locationStatus = :status, latitude = :latitude, longitude = :longitude, locationAccuracyMeters = :accuracyMeters WHERE id = :id")
    suspend fun updateLocation(id: Long, status: String, latitude: Double?, longitude: Double?, accuracyMeters: Float?)

    @Query("UPDATE emergency_history SET smsAttemptStatus = :status WHERE id = :id")
    suspend fun updateSmsAttempt(id: Long, status: String)

    @Query("UPDATE emergency_history SET callAttemptStatus = :status WHERE id = :id")
    suspend fun updateCallAttempt(id: Long, status: String)
}

@Database(
    entities = [MedicineSchedule::class, MedicineDoseEvent::class, EmergencyContact::class, EmergencyHistoryEvent::class],
    version = 2,
    exportSchema = false
)
abstract class MedMitraDatabase : RoomDatabase() {
    abstract fun medicines(): MedicineDao
    abstract fun medicineDoseHistory(): MedicineDoseHistoryDao
    abstract fun contacts(): EmergencyContactDao
    abstract fun history(): EmergencyHistoryDao

    companion object {
        @Volatile private var instance: MedMitraDatabase? = null

        fun get(context: Context): MedMitraDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                MedMitraDatabase::class.java,
                "medmitra-local.db"
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE medicines ADD COLUMN instructions TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE medicines ADD COLUMN snoozedUntilMillis INTEGER")
                database.execSQL("CREATE TABLE IF NOT EXISTS medicine_dose_history (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, medicineId INTEGER NOT NULL, medicineName TEXT NOT NULL, dosage TEXT NOT NULL, scheduledAtMillis INTEGER NOT NULL, actionAtMillis INTEGER NOT NULL, status TEXT NOT NULL)")
            }
        }
    }
}
