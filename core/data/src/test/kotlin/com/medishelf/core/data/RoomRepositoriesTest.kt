package com.medishelf.core.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.medishelf.core.data.repository.RoomConditionSuggestionRepository
import com.medishelf.core.data.repository.RoomFamilyMemberRepository
import com.medishelf.core.data.repository.RoomMedicineRepository
import com.medishelf.core.data.repository.RoomStorageLocationRepository
import com.medishelf.core.database.MediShelfDatabase
import com.medishelf.core.domain.usecase.ReduceQuantityUseCase
import com.medishelf.core.domain.usecase.SaveMedicineUseCase
import com.medishelf.core.domain.usecase.SaveResult
import com.medishelf.core.domain.validation.DraftAssignment
import com.medishelf.core.domain.validation.MedicineDraft
import com.medishelf.core.domain.validation.MedicineValidator
import com.medishelf.core.model.MedicineEventType
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.testing.TestTimeProvider
import com.medishelf.core.testing.testLocation
import com.medishelf.core.testing.testMember
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Use cases wired to the real Room repositories: the full write path minus encryption. */
@RunWith(RobolectricTestRunner::class)
class RoomRepositoriesTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), MediShelfDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val time = TestTimeProvider()
    private val dispatcher = StandardTestDispatcher()
    private val medicines = RoomMedicineRepository(db.medicineDao(), dispatcher)
    private val members = RoomFamilyMemberRepository(db.familyMemberDao())
    private val locations = RoomStorageLocationRepository(db.storageLocationDao())
    private val suggestions = RoomConditionSuggestionRepository(db.conditionSuggestionDao(), time)
    private val save = SaveMedicineUseCase(medicines, suggestions, MedicineValidator(), time)

    @After
    fun tearDown() = db.close()

    @Test
    fun saveMedicine_roundTripsAllFields() = runTest(dispatcher) {
        val sara = members.save(testMember(name = "Sara"))
        val fridge = locations.save(testLocation(name = "Fridge door").copy(requiresRefrigeration = true))
        val draft = MedicineDraft(
            brandName = "Insulatard",
            genericName = "Insulin isophane",
            strength = "100 IU/ml",
            quantity = 3.0,
            purchaseDate = time.today.minusDays(3),
            expiryDate = time.today.plusMonths(10),
            openedDate = time.today.minusDays(1),
            useWithinDaysAfterOpening = 28,
            prescribedBy = "Dr. Hana",
            dosageInstructions = "10 IU at night",
            storageLocationId = fridge,
            isPrescription = true,
            barcode = "6291",
            assignments = listOf(DraftAssignment(sara, "Diabetes")),
        )

        val id = (save(draft) as SaveResult.Saved).id
        val details = medicines.observe(id).first()!!

        with(details.medicine) {
            assertThat(brandName).isEqualTo("Insulatard")
            assertThat(effectiveExpiryDate).isEqualTo(time.today.plusDays(27))
            assertThat(isPrescription).isTrue()
            assertThat(status).isEqualTo(MedicineStatus.ACTIVE)
        }
        assertThat(details.location?.requiresRefrigeration).isTrue()
        assertThat(details.conditions).containsExactly("Diabetes")
        assertThat(suggestions.observeRecent().first()).containsExactly("Diabetes")
        assertThat(medicines.findLatestByBarcode("6291")?.id).isEqualTo(id)
    }

    @Test
    fun reduceToZero_finishesMedicine_andHistoryIsNewestFirst() = runTest(dispatcher) {
        val id = (
            save(MedicineDraft(brandName = "Panadol", quantity = 2.0, expiryDate = time.today.plusYears(1))) as SaveResult.Saved
            ).id

        ReduceQuantityUseCase(medicines, time)(id, 2.0)

        assertThat(medicines.get(id)!!.medicine.status).isEqualTo(MedicineStatus.FINISHED)
        assertThat(medicines.observeEvents(id).first().map { it.type })
            .containsExactly(MedicineEventType.QUANTITY_USED, MedicineEventType.CREATED).inOrder()
    }

    @Test
    fun upsertingExistingMember_returnsItsId() = runTest(dispatcher) {
        val id = members.save(testMember(name = "Sara"))

        assertThat(members.save(testMember(id = id, name = "Sara K."))).isEqualTo(id)
        assertThat(members.observeAll().first().map { it.name }).containsExactly("Sara K.")
    }
}
