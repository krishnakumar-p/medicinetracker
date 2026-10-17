package com.medishelf.feature.medicines.list

import com.medishelf.core.model.ExpiryWindow
import com.medishelf.core.model.MedicineForm
import com.medishelf.core.model.MedicineSort
import com.medishelf.core.model.MedicineStatus

internal data class MedicineListActions(
    val onMedicineClick: (Long) -> Unit = {},
    val onAddMedicine: () -> Unit = {},
    val onQueryChange: (String) -> Unit = {},
    val onToggleMember: (Long) -> Unit = {},
    val onToggleLocation: (Long) -> Unit = {},
    val onToggleStatus: (MedicineStatus) -> Unit = {},
    val onToggleForm: (MedicineForm) -> Unit = {},
    val onExpiryWindowChange: (ExpiryWindow) -> Unit = {},
    val onLowStockOnlyChange: (Boolean) -> Unit = {},
    val onSortChange: (MedicineSort) -> Unit = {},
    val onClearFilters: () -> Unit = {},
)
