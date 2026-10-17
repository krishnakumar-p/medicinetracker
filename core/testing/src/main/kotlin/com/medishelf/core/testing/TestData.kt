package com.medishelf.core.testing

import com.medishelf.core.model.FamilyMember
import com.medishelf.core.model.Medicine
import com.medishelf.core.model.MedicineStatus
import com.medishelf.core.model.StorageLocation
import java.time.LocalDate

fun testMedicine(
    id: Long = 0,
    brandName: String = "Panadol",
    genericName: String = "Paracetamol",
    quantity: Double = 10.0,
    expiryDate: LocalDate = LocalDate.of(2027, 1, 1),
    status: MedicineStatus = MedicineStatus.ACTIVE,
    barcode: String? = null,
    storageLocationId: Long? = null,
    lowStockThreshold: Double? = null,
    openedDate: LocalDate? = null,
    useWithinDaysAfterOpening: Int? = null,
) = Medicine(
    id = id,
    brandName = brandName,
    genericName = genericName,
    quantity = quantity,
    expiryDate = expiryDate,
    status = status,
    barcode = barcode,
    storageLocationId = storageLocationId,
    lowStockThreshold = lowStockThreshold,
    openedDate = openedDate,
    useWithinDaysAfterOpening = useWithinDaysAfterOpening,
)

fun testMember(id: Long = 0, name: String = "Sara") =
    FamilyMember(id = id, name = name, relationship = "Daughter", avatarColor = 0xFF006A60.toInt())

fun testLocation(id: Long = 0, name: String = "Kitchen cabinet") = StorageLocation(id = id, name = name)
