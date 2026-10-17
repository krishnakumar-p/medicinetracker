package com.medishelf.core.domain.demo

import com.medishelf.core.domain.repository.AssignmentInput
import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.Medicine
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.QuantityUnit
import com.medishelf.core.model.StorageLocation
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/** Debug-only sample inventory for testing and demos. */
interface DemoData {
    val isLoaded: Flow<Boolean>
    suspend fun load()

    /** Removes exactly the records [load] created; real records are untouched. */
    suspend fun clear()

    /** Drops the record of which IDs are demo data (after a restore, IDs belong to real records). */
    suspend fun forget()
}

/** A demo medicine referencing members/locations by their index in [DemoDataset]. */
data class DemoMedicine(
    val medicine: Medicine,
    val locationIndex: Int?,
    val people: List<Pair<Int, String?>>,
)

data class DemoDataset(
    val members: List<FamilyMember>,
    val locations: List<StorageLocation>,
    val medicines: List<DemoMedicine>,
) {
    fun assignments(demo: DemoMedicine, memberIds: List<Long>) =
        demo.people.map { (index, condition) -> AssignmentInput(memberIds[index], condition) }

    companion object {
        /** Dates relative to [today] so every state (expired, expiring, low stock…) is always on show. */
        fun build(today: LocalDate): DemoDataset {
            val members = listOf(
                FamilyMember(name = "Layla", relationship = "Me", avatarColor = 0xFF006A60.toInt()),
                FamilyMember(name = "Karim", relationship = "Husband", avatarColor = 0xFF3F51B5.toInt()),
                FamilyMember(name = "Sara", relationship = "Daughter", dateOfBirth = today.minusYears(9), allergies = "Penicillin", avatarColor = 0xFF8E4585.toInt()),
                FamilyMember(name = "Omar", relationship = "Son", dateOfBirth = today.minusYears(4), avatarColor = 0xFF7A5900.toInt()),
            )
            val locations = listOf(
                StorageLocation(name = "Kitchen cabinet – top shelf", room = "Kitchen"),
                StorageLocation(name = "Fridge door", room = "Kitchen", requiresRefrigeration = true),
                StorageLocation(name = "Master bedroom drawer", room = "Bedroom"),
                StorageLocation(name = "Car first-aid kit", room = "Car", notes = "Check before summer"),
            )
            fun med(
                brand: String, generic: String, strength: String, form: MedicineForm, qty: Double, unit: QuantityUnit,
                expiresInDays: Long, location: Int?, people: List<Pair<Int, String?>>, threshold: Double? = null,
                openedDaysAgo: Long? = null, useWithin: Int? = null, rx: Boolean = false, doctor: String = "",
                dosage: String = "", status: MedicineStatus = MedicineStatus.ACTIVE, barcode: String? = null,
            ) = DemoMedicine(
                Medicine(
                    brandName = brand, genericName = generic, strength = strength, form = form, quantity = qty, unit = unit,
                    lowStockThreshold = threshold, purchaseDate = today.minusMonths(3), expiryDate = today.plusDays(expiresInDays),
                    openedDate = openedDaysAgo?.let(today::minusDays), useWithinDaysAfterOpening = useWithin,
                    isPrescription = rx, prescribedBy = doctor, dosageInstructions = dosage, status = status,
                    barcode = barcode, batchNumber = "DEMO-${brand.take(3).uppercase()}", notes = "Demo data",
                ),
                location,
                people,
            )
            val medicines = listOf(
                med("Panadol", "Paracetamol", "500 mg", MedicineForm.TABLET, 20.0, QuantityUnit.TABLETS, 400, 0, listOf(0 to "Headache", 1 to "Back pain"), threshold = 6.0, barcode = "6291000000017"),
                med("Calpol", "Paracetamol", "120 mg/5 ml", MedicineForm.SYRUP, 40.0, QuantityUnit.ML, 5, 0, listOf(3 to "Fever"), dosage = "5 ml every 6 hours, max 4 doses a day"),
                med("Ventolin", "Salbutamol", "100 mcg/dose", MedicineForm.INHALER, 30.0, QuantityUnit.DOSES, 200, 2, listOf(2 to "Asthma"), threshold = 40.0, rx = true, doctor = "Dr. Hana Saleh", dosage = "2 puffs when needed"),
                med("Insulatard", "Insulin isophane", "100 IU/ml", MedicineForm.INJECTION, 3.0, QuantityUnit.PIECES, 300, 1, listOf(1 to "Type 2 diabetes"), threshold = 2.0, openedDaysAgo = 20, useWithin = 28, rx = true, doctor = "Dr. Yusuf Amin", dosage = "12 IU at bedtime"),
                med("Augmentin", "Amoxicillin + clavulanic acid", "457 mg/5 ml", MedicineForm.SYRUP, 35.0, QuantityUnit.ML, -12, 1, listOf(3 to "Ear infection"), rx = true, doctor = "Dr. Hana Saleh"),
                med("Optive", "Carboxymethylcellulose", "0.5%", MedicineForm.DROPS, 10.0, QuantityUnit.ML, 500, 2, listOf(0 to "Dry eyes"), openedDaysAgo = 25, useWithin = 30),
                med("Brufen", "Ibuprofen", "400 mg", MedicineForm.TABLET, 4.0, QuantityUnit.TABLETS, 25, 3, listOf(1 to "Pain"), threshold = 6.0),
                med("Fucidin", "Fusidic acid", "2%", MedicineForm.CREAM, 15.0, QuantityUnit.GRAMS, 90, 3, emptyList()),
                med("Zyrtec", "Cetirizine", "10 mg", MedicineForm.TABLET, 0.0, QuantityUnit.TABLETS, 180, 2, listOf(0 to "Hay fever"), status = MedicineStatus.FINISHED),
                med("Old cough syrup", "Dextromethorphan", "15 mg/5 ml", MedicineForm.SYRUP, 50.0, QuantityUnit.ML, -200, null, emptyList(), status = MedicineStatus.DISPOSED),
            )
            return DemoDataset(members, locations, medicines)
        }
    }
}
