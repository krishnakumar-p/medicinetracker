package com.medishelf.core.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.medishelf.core.database.MediShelfDatabase
import com.medishelf.core.database.entity.ConditionSuggestionEntity
import com.medishelf.core.database.entity.FamilyMemberEntity
import com.medishelf.core.database.entity.MedicineEntity
import com.medishelf.core.database.entity.MedicineMemberCrossRef
import com.medishelf.core.database.entity.MedicinePhotoEntity
import com.medishelf.core.database.entity.StorageLocationEntity
import com.medishelf.core.domain.backup.ImportResult
import com.medishelf.core.domain.reminders.ReminderLedger
import com.medishelf.core.domain.repository.PhotoStore
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.PhotoType
import com.medishelf.core.model.QuantityUnit
import com.medishelf.core.testing.FakeDemoData
import com.medishelf.core.testing.FakeReminderLedgerRepository
import com.medishelf.core.testing.TestTimeProvider
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class ZipBackupServiceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, MediShelfDatabase::class.java).allowMainThreadQueries().build()
    private val photoDir = File(context.filesDir, "photos").apply { mkdirs() }
    private val photoStore = object : PhotoStore {
        override suspend fun import(sourceUri: String) = error("unused")
        override fun absolutePath(fileName: String) = File(photoDir, fileName).absolutePath
        override val directoryPath: String get() = photoDir.absolutePath
        override suspend fun delete(fileName: String) { File(photoDir, fileName).delete() }
    }
    private val ledger = FakeReminderLedgerRepository().apply { ledger = ReminderLedger(mapOf(1L to setOf("exp7"))) }
    private val demo = FakeDemoData()
    private val password = "correct horse".toCharArray()

    @After
    fun tearDown() = db.close()

    private fun service(dispatcher: kotlinx.coroutines.CoroutineDispatcher) =
        ZipBackupService(context, db.backupDao(), photoStore, ledger, demo, TestTimeProvider(), dispatcher)

    private suspend fun seed() {
        db.familyMemberDao().upsert(FamilyMemberEntity(7, "Sara", "Daughter", LocalDate.of(2017, 3, 1), "Penicillin", 0))
        db.storageLocationDao().upsert(StorageLocationEntity(3, "Fridge door", "Kitchen", "", null, true))
        val medicine = MedicineEntity(
            id = 11, brandName = "Ventolin", genericName = "Salbutamol", strength = "100 mcg", form = MedicineForm.INHALER,
            manufacturer = "", batchNumber = "B1", barcode = "629", quantity = 120.0, unit = QuantityUnit.DOSES,
            lowStockThreshold = 20.0, purchaseDate = null, expiryDate = LocalDate.of(2027, 5, 31), openedDate = null,
            useWithinDaysAfterOpening = null, prescribedBy = "Dr. Hana", prescriptionDate = null, dosageInstructions = "2 puffs",
            storageLocationId = 3, isPrescription = true, notes = "Naïve café ✓ 2×/day", status = MedicineStatus.ACTIVE,
            createdAt = Instant.ofEpochMilli(1), updatedAt = Instant.ofEpochMilli(2),
        )
        db.medicineDao().insert(medicine)
        db.medicineDao().insertAssignments(listOf(MedicineMemberCrossRef(11, 7, "Asthma")))
        db.medicineDao().insertPhoto(MedicinePhotoEntity(5, 11, PhotoType.BOX, "box.jpg", Instant.ofEpochMilli(3)))
        db.conditionSuggestionDao().upsert(listOf(ConditionSuggestionEntity("Asthma", Instant.ofEpochMilli(4))))
        File(photoDir, "box.jpg").writeBytes(byteArrayOf(1, 2, 3))
    }

    @Test
    fun roundTrip_restoresEverything_andRejectsBadInput() = runTest {
        val service = service(StandardTestDispatcher(testScheduler))
        seed()
        val archive = ByteArrayOutputStream().also { out ->
            val summary = service.export(out, password.copyOf())
            assertThat(summary.medicines).isEqualTo(1)
            assertThat(summary.photos).isEqualTo(1)
        }.toByteArray()
        assertThat(String(archive, Charsets.ISO_8859_1)).doesNotContain("Ventolin") // encrypted, not just zipped

        // Wipe and change things, then restore.
        db.backupDao().replaceAll(db.backupDao().snapshot().copy(members = emptyList(), assignments = emptyList(), medicines = emptyList(), photos = emptyList(), events = emptyList()))
        File(photoDir, "box.jpg").delete()
        File(photoDir, "stray.jpg").writeBytes(byteArrayOf(9))

        assertThat(service.import(ByteArrayInputStream(archive), "wrong password".toCharArray())).isEqualTo(ImportResult.WrongPassword)
        assertThat(service.import(ByteArrayInputStream("not a zip".toByteArray()), password.copyOf())).isEqualTo(ImportResult.NotABackup)
        assertThat(db.backupDao().medicines()).isEmpty() // failed imports changed nothing

        val result = service.import(ByteArrayInputStream(archive), password.copyOf())
        assertThat(result).isInstanceOf(ImportResult.Success::class.java)
        val restored = db.medicineDao().get(11)!!
        assertThat(restored.medicine.notes).isEqualTo("Naïve café ✓ 2×/day")
        assertThat(restored.assignments.single().crossRef.condition).isEqualTo("Asthma")
        assertThat(restored.location!!.requiresRefrigeration).isTrue()
        assertThat(File(photoDir, "box.jpg").readBytes()).isEqualTo(byteArrayOf(1, 2, 3))
        assertThat(File(photoDir, "stray.jpg").exists()).isFalse()
        assertThat(ledger.ledger).isEqualTo(ReminderLedger())
        assertThat(demo.forgotten).isTrue()
    }

    @Test
    fun zipSlipAndForeignArchives_areRejected() = runTest {
        val service = service(StandardTestDispatcher(testScheduler))
        fun archive(vararg entries: Pair<String, String>): ByteArray = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out, password.copyOf()).use { zip ->
                entries.forEach { (name, body) ->
                    zip.putNextEntry(ZipParameters().apply { fileNameInZip = name; isEncryptFiles = true; encryptionMethod = EncryptionMethod.AES })
                    zip.write(body.toByteArray())
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        val evil = archive(
            "manifest.json" to """{"format":"medishelf-backup","schemaVersion":1,"createdAtEpochMillis":0,"appVersion":"","medicines":0,"photos":0}""",
            "photos/../../shared_prefs/x.xml" to "pwned",
        )
        assertThat(service.import(ByteArrayInputStream(evil), password.copyOf())).isEqualTo(ImportResult.NotABackup)
        assertThat(File(context.filesDir.parentFile, "shared_prefs/x.xml").exists()).isFalse()

        val future = archive("manifest.json" to """{"format":"medishelf-backup","schemaVersion":99,"createdAtEpochMillis":0,"appVersion":"","medicines":0,"photos":0}""")
        assertThat(service.import(ByteArrayInputStream(future), password.copyOf())).isEqualTo(ImportResult.TooNew)

        val other = archive("readme.txt" to "hello")
        assertThat(service.import(ByteArrayInputStream(other), password.copyOf())).isEqualTo(ImportResult.NotABackup)
    }
}
