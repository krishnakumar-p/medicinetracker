package com.medishelf.core.database

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.medishelf.core.database.entity.DoseLogEntity
import com.medishelf.core.database.entity.DoseScheduleEntity
import com.medishelf.core.database.entity.FamilyMemberEntity
import com.medishelf.core.database.entity.MedicineEntity
import com.medishelf.core.database.entity.MedicineEventEntity
import com.medishelf.core.model.DoseScheduleKind
import com.medishelf.core.model.MedicineEventType
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.QuantityUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class MigrationAndDoseDaoTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), MediShelfDatabase::class.java)

    private val today = LocalDate.of(2026, 1, 15)

    @Test
    fun migrate1To2_keepsMedicines_andDefaultsNewColumns() {
        helper.createDatabase(DB, 1).use { db ->
            db.execSQL(
                """
                INSERT INTO medicines (id, brand_name, generic_name, strength, form, manufacturer, batch_number, barcode,
                  quantity, unit, low_stock_threshold, purchase_date, expiry_date, opened_date,
                  use_within_days_after_opening, prescribed_by, prescription_date, dosage_instructions,
                  storage_location_id, is_prescription, notes, status, created_at, updated_at)
                VALUES (1, 'Panadol', '', '500 mg', 'TABLET', '', '', NULL, 20, 'TABLETS', NULL, NULL, 20500, NULL,
                  NULL, 'Dr. Saleh', NULL, '', NULL, 1, '', 'ACTIVE', 0, 0)
                """.trimIndent(),
            )
        }
        helper.runMigrationsAndValidate(DB, 2, true).use { db ->
            db.query("SELECT brand_name, prescribed_by, prescribed_for_member_id, prescribed_for_reason, prescription_end_date FROM medicines").use { c ->
                assertThat(c.moveToFirst()).isTrue()
                assertThat(c.getString(0)).isEqualTo("Panadol")
                assertThat(c.getString(1)).isEqualTo("Dr. Saleh")
                assertThat(c.isNull(2)).isTrue()
                assertThat(c.getString(3)).isEmpty()
                assertThat(c.isNull(4)).isTrue()
            }
            db.query("SELECT COUNT(*) FROM dose_schedules").use { c -> c.moveToFirst(); assertThat(c.getInt(0)).isEqualTo(0) }
        }
    }

    @Test
    fun takeDose_lowersStockOnce_andUndoPutsItBack() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MediShelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val medicineId = db.medicineDao().insert(medicine())
            val dao = db.doseDao()
            val scheduleId = dao.insertSchedule(
                DoseScheduleEntity(
                    medicineId = medicineId, memberId = null, kind = DoseScheduleKind.CONTINUOUS, dosePerIntake = 1.0,
                    timesPerDay = 2, startDate = today, endDate = null, createdAt = Instant.EPOCH,
                ),
            )
            val log = DoseLogEntity(scheduleId = scheduleId, date = today, slot = 0, quantity = 1.0, takenAt = Instant.EPOCH)
            val event = MedicineEventEntity(
                medicineId = medicineId, type = MedicineEventType.DOSE_TAKEN, quantityDelta = -1.0,
                fromStatus = null, toStatus = null, note = null, at = Instant.EPOCH,
            )

            assertThat(dao.takeDose(log, medicineId, 9.0, MedicineStatus.ACTIVE, event)).isTrue()
            // Same slot again: rejected, stock untouched.
            assertThat(dao.takeDose(log, medicineId, 8.0, MedicineStatus.ACTIVE, event)).isFalse()
            assertThat(db.medicineDao().get(medicineId)!!.medicine.quantity).isEqualTo(9.0)

            val stored = dao.findLog(scheduleId, today, 0)!!
            dao.undoDose(stored.id, medicineId, 10.0, MedicineStatus.ACTIVE, event.copy(type = MedicineEventType.DOSE_UNDONE))
            assertThat(dao.findLog(scheduleId, today, 0)).isNull()
            assertThat(db.medicineDao().get(medicineId)!!.medicine.quantity).isEqualTo(10.0)
            assertThat(dao.observeLogs(today, today).first()).isEmpty()
        } finally {
            db.close()
        }
    }

    @Test
    fun deletingMember_clearsPrescribedFor_andScheduleMember() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MediShelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val memberId = db.familyMemberDao().upsert(FamilyMemberEntity(name = "Sara", relationship = "", dateOfBirth = null, allergies = null, avatarColor = 0))
            val medicineId = db.medicineDao().insert(medicine().copy(prescribedForMemberId = memberId))
            val scheduleId = db.doseDao().insertSchedule(
                DoseScheduleEntity(
                    medicineId = medicineId, memberId = memberId, kind = DoseScheduleKind.PRESCRIPTION, dosePerIntake = 1.0,
                    timesPerDay = 1, startDate = today, endDate = today.plusDays(5), createdAt = Instant.EPOCH,
                ),
            )
            assertThat(db.medicineDao().get(medicineId)!!.prescribedFor!!.name).isEqualTo("Sara")

            db.familyMemberDao().delete(memberId)

            assertThat(db.medicineDao().get(medicineId)!!.medicine.prescribedForMemberId).isNull()
            assertThat(db.doseDao().getSchedule(scheduleId)!!.memberId).isNull()
        } finally {
            db.close()
        }
    }

    private fun medicine() = MedicineEntity(
        brandName = "Panadol", genericName = "", strength = "500 mg", form = MedicineForm.TABLET, manufacturer = "",
        batchNumber = "", barcode = null, quantity = 10.0, unit = QuantityUnit.TABLETS, lowStockThreshold = null,
        purchaseDate = null, expiryDate = today.plusMonths(6), openedDate = null, useWithinDaysAfterOpening = null,
        prescribedBy = "", prescriptionDate = null, dosageInstructions = "", storageLocationId = null,
        isPrescription = false, notes = "", status = MedicineStatus.ACTIVE, createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH,
    )

    private companion object {
        const val DB = "migration-test.db"
    }
}
