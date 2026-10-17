package com.medishelf.core.model

enum class MedicineForm { TABLET, CAPSULE, SYRUP, DROPS, INHALER, INJECTION, CREAM, OTHER }

enum class QuantityUnit { TABLETS, CAPSULES, ML, DOSES, GRAMS, PIECES, OTHER }

/**
 * Stored lifecycle status. Records are never hard-deleted; DISPOSED is the end state.
 * EXPIRED is set by the daily reconcile job, but [Medicine.effectiveStatus] reports
 * expiry immediately even before the job runs.
 */
enum class MedicineStatus { ACTIVE, EXPIRED, FINISHED, DISPOSED }

enum class PhotoType { BOX, STRIP, PRESCRIPTION }

enum class MedicineEventType { CREATED, EDITED, QUANTITY_USED, STATUS_CHANGED, AUTO_EXPIRED }
