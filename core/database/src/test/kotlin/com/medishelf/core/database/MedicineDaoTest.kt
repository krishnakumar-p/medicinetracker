package com.medishelf.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.medishelf.core.database.entity.ConditionSuggestionEntity
import com.medishelf.core.database.entity.FamilyMemberEntity
import com.medishelf.core.database.entity.MedicineEntity
import com.medishelf.core.database.entity.MedicineEventEntity
import com.medishelf.core.database.entity.MedicineMemberCrossRef
import com.medishelf.core.database.entity.MedicinePhotoEntity
import com.medishelf.core.database.entity.StorageLocationEntity
import com.medishelf.core.model.MedicineEventType
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.PhotoType
import com.medishelf.core.model.QuantityUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant
import java.time.LocalDate

/**
 * DAO behaviour on an in-memory Room database. Encryption is transparent to queries,
 * so SQLCipher is covered separately by an instrumented test.
 */
@RunWith(RobolectricTestRunner::class)
class MedicineDaoTest {
    private lateinit var db: MediShelfDatabase
    private val dao get() = db.medicineDao()
    private val today = LocalDate.of(2026, 1, 15)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MediShelfDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun medicine(
        brand: String = "Panadol",
        expiry: LocalDate = today.plusMonths(6),
        status: MedicineStatus = MedicineStatus.ACTIVE,
        barcode: String? = null,
        locationId: Long? = null,
        opened: LocalDate? = null,
        useWithin: Int? = null,
        updatedAt: Instant = Instant.EPOCH,
    ) = MedicineEntity(
        brandName = brand, genericName = "", strength = "500 mg", form = MedicineForm.TABLET, manufacturer = "",
        batchNumber = "", barcode = barcode, quantity = 10.0, unit = QuantityUnit.TABLETS, lowStockThreshold = null,
        purchaseDate = null, expiryDate = expiry, openedDate = opened, useWithinDaysAfterOpening = useWithin,
        prescribedBy = "", prescriptionDate = null, dosageInstructions = "", storageLocationId = locationId,
        isPrescription = false, notes = "", status = status, createdAt = Instant.EPOCH, updatedAt = updatedAt,
    )

    private fun event(type: MedicineEventType = MedicineEventType.CREATED) =
        MedicineEventEntity(medicineId = 0, type = type, quantityDelta = null, fromStatus = null, toStatus = null, note = null, at = Instant.EPOCH)

    private suspend fun member(name: String) =
        db.familyMemberDao().upsert(FamilyMemberEntity(name = name, relationship = "", dateOfBirth = null, allergies = null, avatarColor = 0))

    private suspend fun location(name: String) =
        db.storageLocationDao().upsert(StorageLocationEntity(name = name, room = "", notes = "", photoFileName = null, requiresRefrigeration = false))

    @Test
    fun save_insertsWithRelations_inOneTransaction() = runTest {
        val sara = member("Sara")
        val kitchen = location("Kitchen")

        val id = dao.save(medicine(locationId = kitchen), listOf(MedicineMemberCrossRef(0, sara, "Fever")), event())
        dao.insertPhoto(MedicinePhotoEntity(medicineId = id, type = PhotoType.BOX, fileName = "a.jpg", createdAt = Instant.EPOCH))

        val loaded = dao.get(id)!!
        assertThat(loaded.location?.name).isEqualTo("Kitchen")
        assertThat(loaded.assignments.map { it.member.name to it.crossRef.condition }).containsExactly("Sara" to "Fever")
        assertThat(loaded.photos.map { it.fileName }).containsExactly("a.jpg")
        assertThat(dao.observeEvents(id).first().single().medicineId).isEqualTo(id)
    }

    @Test
    fun save_onUpdate_replacesAssignments_andAppendsHistory() = runTest {
        val sara = member("Sara")
        val omar = member("Omar")
        val id = dao.save(medicine(), listOf(MedicineMemberCrossRef(0, sara, null)), event())

        dao.save(medicine(brand = "Panadol Extra").copy(id = id), listOf(MedicineMemberCrossRef(0, omar, "Pain")), event(MedicineEventType.EDITED))

        val loaded = dao.get(id)!!
        assertThat(loaded.medicine.brandName).isEqualTo("Panadol Extra")
        assertThat(loaded.assignments.map { it.member.name }).containsExactly("Omar")
        assertThat(dao.observeEvents(id).first().map { it.type })
            .containsExactly(MedicineEventType.EDITED, MedicineEventType.CREATED).inOrder()
    }

    @Test
    fun deletingLocation_unassignsMedicine_neverDeletesIt() = runTest {
        val kitchen = location("Kitchen")
        val id = dao.save(medicine(locationId = kitchen), emptyList(), event())

        db.storageLocationDao().delete(kitchen)

        val loaded = dao.get(id)!!
        assertThat(loaded.medicine.storageLocationId).isNull()
        assertThat(loaded.location).isNull()
    }

    @Test
    fun deletingMember_removesOnlyTheirLinks() = runTest {
        val sara = member("Sara")
        val omar = member("Omar")
        val id = dao.save(medicine(), listOf(MedicineMemberCrossRef(0, sara, null), MedicineMemberCrossRef(0, omar, null)), event())

        db.familyMemberDao().delete(sara)

        assertThat(dao.get(id)!!.assignments.map { it.member.name }).containsExactly("Omar")
    }

    @Test
    fun activeExpiredBefore_appliesOpenedRule_andIgnoresClosedStatuses() = runTest {
        val printedPast = dao.save(medicine(brand = "printed", expiry = today.minusDays(1)), emptyList(), event())
        val openedPast = dao.save(medicine(brand = "opened", opened = today.minusDays(30), useWithin = 28), emptyList(), event())
        dao.save(medicine(brand = "today", expiry = today), emptyList(), event())
        dao.save(medicine(brand = "openedOk", opened = today.minusDays(5), useWithin = 28), emptyList(), event())
        dao.save(medicine(brand = "finished", expiry = today.minusDays(9), status = MedicineStatus.FINISHED), emptyList(), event())

        val ids = dao.getActiveExpiredBefore(today.toEpochDay()).map { it.id }

        assertThat(ids).containsExactly(printedPast, openedPast)
    }

    @Test
    fun findLatestByBarcode_returnsMostRecentlyUpdated() = runTest {
        dao.save(medicine(brand = "old", barcode = "629", updatedAt = Instant.ofEpochSecond(1)), emptyList(), event())
        dao.save(medicine(brand = "new", barcode = "629", updatedAt = Instant.ofEpochSecond(5)), emptyList(), event())

        assertThat(dao.findLatestByBarcode("629")?.brandName).isEqualTo("new")
        assertThat(dao.findLatestByBarcode("000")).isNull()
    }

    @Test
    fun statusUpdateWithEvent_isAtomic() = runTest {
        val id = dao.save(medicine(), emptyList(), event())

        dao.updateQuantityAndStatusWithEvent(id, 0.0, MedicineStatus.FINISHED, event(MedicineEventType.QUANTITY_USED).copy(medicineId = id))

        val loaded = dao.get(id)!!.medicine
        assertThat(loaded.quantity).isEqualTo(0.0)
        assertThat(loaded.status).isEqualTo(MedicineStatus.FINISHED)
        assertThat(dao.observeEvents(id).first()).hasSize(2)
    }

    @Test
    fun conditionSuggestions_areCaseInsensitiveUnique_andRecentFirst() = runTest {
        val suggestions = db.conditionSuggestionDao()
        suggestions.upsert(listOf(ConditionSuggestionEntity("Asthma", Instant.ofEpochSecond(1))))
        suggestions.upsert(listOf(ConditionSuggestionEntity("Fever", Instant.ofEpochSecond(2))))
        suggestions.upsert(listOf(ConditionSuggestionEntity("asthma", Instant.ofEpochSecond(3))))

        assertThat(suggestions.observeRecent(10).first()).containsExactly("asthma", "Fever").inOrder()
    }
}
