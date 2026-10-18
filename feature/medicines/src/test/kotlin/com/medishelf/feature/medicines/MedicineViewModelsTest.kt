package com.medishelf.feature.medicines

import com.google.common.truth.Truth.assertThat
import com.medishelf.core.domain.validation.MedicineValidationError
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.PhotoType
import com.medishelf.core.testing.MainDispatcherRule
import com.medishelf.core.testing.testMedicine
import com.medishelf.core.testing.testMember
import com.medishelf.feature.medicines.detail.DetailMessage
import com.medishelf.feature.medicines.edit.ScannedField
import androidx.navigation.testing.invoke
import com.medishelf.feature.medicines.navigation.MedicineEditRoute
import com.medishelf.feature.medicines.navigation.MedicineListPreset
import com.medishelf.feature.medicines.navigation.MedicineListRoute
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MedicineViewModelsTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    private val f = MedicineTestFixture()

    @Test
    fun list_lowStockPreset_showsOnlyLowStock_andSearchNarrows() = runTest(mainDispatcherRule.testDispatcher) {
        f.medicines.put(testMedicine(brandName = "Zyrtec", quantity = 1.0, lowStockThreshold = 5.0))
        f.medicines.put(testMedicine(brandName = "Panadol", quantity = 30.0, lowStockThreshold = 5.0))
        f.medicines.put(testMedicine(brandName = "Ventolin", quantity = 1.0, lowStockThreshold = 2.0))
        val vm = f.listViewModel(MedicineListRoute(MedicineListPreset.LOW_STOCK))
        backgroundScope.launch { vm.uiState.collect {} }

        assertThat(vm.uiState.value.items.map { it.medicine.brandName }).containsExactly("Zyrtec", "Ventolin")
        vm.onQueryChange("vent")
        assertThat(vm.uiState.value.items.map { it.medicine.brandName }).containsExactly("Ventolin")
        vm.onClearFilters()
        assertThat(vm.uiState.value.filter.lowStockOnly).isFalse()
        assertThat(vm.uiState.value.filter.query).isEqualTo("vent")
    }

    @Test
    fun edit_showsErrorsOnlyAfterSave_thenSavesValidForm() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = f.editViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        assertThat(vm.uiState.value.errors).isEmpty()

        vm.onSave()
        val invalid = vm.uiState.value
        assertThat(invalid.errors).containsAtLeast(
            MedicineValidationError.NAME_REQUIRED,
            MedicineValidationError.QUANTITY_REQUIRED,
            MedicineValidationError.EXPIRY_REQUIRED,
        )
        assertThat(invalid.savedId).isNull()

        // Errors update live once shown.
        vm.onFormChange { it.copy(brandName = "Panadol", quantityText = "20", expiryDate = f.time.today.plusYears(1)) }
        assertThat(vm.uiState.value.errors).isEmpty()
        vm.onSave()
        val savedId = vm.uiState.value.savedId
        assertThat(savedId).isNotNull()
        assertThat(f.medicines.get(savedId!!)!!.medicine.quantity).isEqualTo(20.0)
    }

    @Test
    fun edit_assignsMembersWithConditions() = runTest(mainDispatcherRule.testDispatcher) {
        val sara = f.members.save(testMember(name = "Sara"))
        val vm = f.editViewModel()
        vm.onToggleMember(sara)
        vm.onConditionChange(sara, "Asthma")
        vm.onFormChange { it.copy(brandName = "Ventolin", quantityText = "1", expiryDate = f.time.today.plusYears(1)) }

        vm.onSave()

        val id = vm.uiState.first { it.savedId != null }.savedId!!
        assertThat(f.medicines.get(id)!!.assignments.single().condition).isEqualTo("Asthma")
        assertThat(f.suggestions.recent.value).containsExactly("Asthma")
    }

    @Test
    fun edit_withKnownBarcode_prefillsProductFields() = runTest(mainDispatcherRule.testDispatcher) {
        f.medicines.put(testMedicine(brandName = "Augmentin", barcode = "6281"))
        val handle = androidx.lifecycle.SavedStateHandle(route = MedicineEditRoute())
        val vm = f.editViewModel(savedStateHandle = handle)
        vm.onScanResult(com.medishelf.core.model.ScanSuggestion(barcode = "6281"))

        val state = vm.uiState.first { it.prefilledFromBarcode }

        assertThat(state.prefilledFromBarcode).isTrue()
        assertThat(state.form.brandName).isEqualTo("Augmentin")
        assertThat(state.form.expiryDate).isNull()
    }

    @Test
    fun edit_existingMedicine_loadsIntoForm() = runTest(mainDispatcherRule.testDispatcher) {
        val id = f.medicines.put(testMedicine(brandName = "Brufen", quantity = 12.0))
        val vm = f.editViewModel(MedicineEditRoute(id = id))

        val state = vm.uiState.first { !it.isLoading }

        assertThat(state.isNew).isFalse()
        assertThat(state.form.brandName).isEqualTo("Brufen")
        assertThat(state.form.quantityText).isEqualTo("12")
    }

    @Test
    fun detail_useDisposeAndPhotos() = runTest(mainDispatcherRule.testDispatcher) {
        val id = f.medicines.put(testMedicine(quantity = 3.0))
        val vm = f.detailViewModel(id)
        backgroundScope.launch { vm.uiState.collect {} }
        assertThat(vm.uiState.value.details!!.medicine.quantity).isEqualTo(3.0)

        vm.onUse(1.0)
        assertThat(vm.uiState.value.message).isEqualTo(DetailMessage.USED)
        assertThat(vm.uiState.value.details!!.medicine.quantity).isEqualTo(2.0)
        vm.onMessageShown()

        vm.onUse(null)
        assertThat(vm.uiState.value.message).isEqualTo(DetailMessage.INVALID_AMOUNT)

        vm.onAddPhoto(PhotoType.BOX, "content://picker/1")
        assertThat(vm.uiState.value.photos.single().path).isEqualTo("/fake/photos/photo1.jpg")

        f.photos.failNextImport = true
        vm.onAddPhoto(PhotoType.STRIP, "content://picker/2")
        assertThat(vm.uiState.value.message).isEqualTo(DetailMessage.PHOTO_FAILED)
        assertThat(vm.uiState.value.photos).hasSize(1)

        vm.onMarkDisposed("pharmacy")
        val disposed = vm.uiState.value
        assertThat(disposed.details!!.medicine.status).isEqualTo(MedicineStatus.DISPOSED)
        assertThat(disposed.history.first().note).isEqualTo("pharmacy")
    }

    @Test
    fun edit_fullPackScan_fillsEveryFieldAndMarksIt() = runTest(mainDispatcherRule.testDispatcher) {
        val handle = androidx.lifecycle.SavedStateHandle(route = MedicineEditRoute())
        val vm = f.editViewModel(savedStateHandle = handle)
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onScanResult(com.medishelf.core.model.ScanSuggestion(
                barcode = "6291000000017", name = "Augmentin", genericName = "Amoxicillin + Clavulanic acid",
                strength = "457 mg/5 ml", form = com.medishelf.core.model.MedicineForm.SYRUP, quantity = 70.0,
                unit = com.medishelf.core.model.QuantityUnit.ML, manufacturer = "Beecham Group plc",
                expiryDate = java.time.LocalDate.of(2027, 12, 31), batchNumber = "B9", isPrescription = true,
                dosageInstructions = "5 ml twice daily", storageNote = "Store in a refrigerator (2°C – 8°C).",
            ),
        )

        val state = vm.uiState.value
        with(state.form) {
            assertThat(brandName).isEqualTo("Augmentin")
            assertThat(genericName).isEqualTo("Amoxicillin + Clavulanic acid")
            assertThat(form).isEqualTo(com.medishelf.core.model.MedicineForm.SYRUP)
            assertThat(quantityText).isEqualTo("70")
            assertThat(unit).isEqualTo(com.medishelf.core.model.QuantityUnit.ML)
            assertThat(manufacturer).isEqualTo("Beecham Group plc")
            assertThat(isPrescription).isTrue()
            assertThat(dosageInstructions).isEqualTo("5 ml twice daily")
            assertThat(notes).isEqualTo("Store in a refrigerator (2°C – 8°C).")
        }
        assertThat(state.scannedFields).containsExactly(
            ScannedField.BARCODE, ScannedField.NAME, ScannedField.GENERIC, ScannedField.STRENGTH, ScannedField.FORM,
            ScannedField.QUANTITY, ScannedField.MANUFACTURER, ScannedField.EXPIRY, ScannedField.BATCH,
            ScannedField.PRESCRIPTION, ScannedField.DOSAGE, ScannedField.NOTES,
        )
        // Nothing saved until the user confirms in the editor.
        assertThat(f.medicines.events.value).isEmpty()
    }

    @Test
    fun edit_labelResultViaBackStack_keepsTypedName_fillsBlankFields() = runTest(mainDispatcherRule.testDispatcher) {
        val handle = androidx.lifecycle.SavedStateHandle(route = MedicineEditRoute())
        val vm = f.editViewModel(savedStateHandle = handle)
        backgroundScope.launch { vm.uiState.collect {} }
        vm.onFormChange { it.copy(brandName = "My typed name") }

        vm.onScanResult(com.medishelf.core.model.ScanSuggestion(name = "Brufen", strength = "400 mg", expiryDate = java.time.LocalDate.of(2027, 6, 30)),
        )

        val state = vm.uiState.value
        assertThat(state.form.brandName).isEqualTo("My typed name")
        assertThat(state.form.strength).isEqualTo("400 mg")
        assertThat(state.form.expiryDate).isEqualTo(java.time.LocalDate.of(2027, 6, 30))
        assertThat(state.scannedFields).containsExactly(ScannedField.STRENGTH, ScannedField.EXPIRY)
        // Delivered once: the handle no longer holds the result.
        assertThat(handle.get<ArrayList<String?>>("medishelf.scan_result")).isNull()
    }

    @Test
    fun edit_pharmacyLabelScan_assignsPatientWithCondition_andPicksFridge() = runTest(mainDispatcherRule.testDispatcher) {
        val sara = f.members.save(testMember(name = "Sara Ahmed"))
        f.members.save(testMember(name = "Omar"))
        f.locations.save(com.medishelf.core.testing.testLocation(name = "Kitchen"))
        val fridge = f.locations.save(com.medishelf.core.testing.testLocation(name = "Fridge door").copy(requiresRefrigeration = true))
        val handle = androidx.lifecycle.SavedStateHandle(route = MedicineEditRoute())
        val vm = f.editViewModel(savedStateHandle = handle)
        backgroundScope.launch { vm.uiState.collect {} }
        val dispensed = java.time.LocalDate.of(2026, 1, 12)

        vm.onScanResult(com.medishelf.core.model.ScanSuggestion(
                genericName = "Amoxicillin", patientName = "SARA AHMED", condition = "Ear infection",
                prescribedBy = "Dr. Hana Saleh", prescriptionDate = dispensed, useWithinDays = 14,
                storageNote = "Store in a refrigerator (2°C – 8°C).",
            ),
        )

        val state = vm.uiState.value
        with(state.form) {
            assertThat(assignments).containsExactly(sara, "Ear infection")
            assertThat(storageLocationId).isEqualTo(fridge)
            assertThat(useWithinText).isEqualTo("14")
            assertThat(prescribedBy).isEqualTo("Dr. Hana Saleh")
            assertThat(prescriptionDate).isEqualTo(dispensed)
            assertThat(purchaseDate).isEqualTo(dispensed)
            assertThat(prescribedForMemberId).isEqualTo(sara)
            assertThat(prescribedForReason).isEqualTo("Ear infection")
        }
        assertThat(state.scannedFields).containsAtLeast(
            ScannedField.PRESCRIBED_FOR, ScannedField.PEOPLE, ScannedField.LOCATION, ScannedField.USE_WITHIN,
            ScannedField.PRESCRIBER, ScannedField.PRESCRIPTION_DATE, ScannedField.PURCHASE_DATE,
        )
    }

    @Test
    fun edit_prescriptionCourse_savesPrescribedForAndDose_andReloadsIt() = runTest(mainDispatcherRule.testDispatcher) {
        val sara = f.members.save(testMember(name = "Sara"))
        val vm = f.editViewModel()
        backgroundScope.launch { vm.uiState.collect {} }
        val today = f.time.today
        vm.onFormChange {
            it.copy(
                brandName = "Augmentin", quantityText = "20", expiryDate = today.plusYears(1), isPrescription = true,
                prescribedForMemberId = sara, prescribedForReason = "Ear infection",
                prescriptionStartDate = today, prescriptionEndDate = today.minusDays(1),
                doseAmountText = "1", dosesPerDayText = "",
            )
        }
        vm.onSave()
        assertThat(vm.uiState.value.errors).containsExactly(
            MedicineValidationError.PRESCRIPTION_END_BEFORE_START,
            MedicineValidationError.DOSE_INCOMPLETE,
        )

        vm.onFormChange { it.copy(prescriptionEndDate = today.plusDays(6), dosesPerDayText = "2") }
        vm.onSave()
        val id = vm.uiState.first { it.savedId != null }.savedId!!
        val saved = f.medicines.get(id)!!
        assertThat(saved.prescribedFor!!.name).isEqualTo("Sara")
        assertThat(saved.medicine.prescriptionEndDate).isEqualTo(today.plusDays(6))
        assertThat(saved.assignments.single().condition).isEqualTo("Ear infection")
        val schedule = f.doses.schedules.value.values.single()
        assertThat(schedule.timesPerDay).isEqualTo(2)
        assertThat(schedule.endDate).isEqualTo(today.plusDays(6))

        val reopened = f.editViewModel(MedicineEditRoute(id))
        backgroundScope.launch { reopened.uiState.collect {} }
        val form = reopened.uiState.first { !it.isLoading }.form
        assertThat(form.doseAmountText).isEqualTo("1")
        assertThat(form.dosesPerDayText).isEqualTo("2")
        assertThat(form.prescribedForReason).isEqualTo("Ear infection")
    }

    @Test
    fun matchMember_byFullOrUniqueFirstName() {
        val members = listOf(testMember(id = 1, name = "Sara Ahmed"), testMember(id = 2, name = "Omar"), testMember(id = 3, name = "Omar Ali"))
        assertThat(com.medishelf.feature.medicines.edit.matchMember("sara ahmed", members)?.id).isEqualTo(1)
        assertThat(com.medishelf.feature.medicines.edit.matchMember("Sara K.", members)?.id).isEqualTo(1)
        assertThat(com.medishelf.feature.medicines.edit.matchMember("Omar", members)?.id).isEqualTo(2) // exact beats ambiguity
        assertThat(com.medishelf.feature.medicines.edit.matchMember("Omar Hassan", members)).isNull() // two Omars
        assertThat(com.medishelf.feature.medicines.edit.matchMember("Layla", members)).isNull()
    }
}
