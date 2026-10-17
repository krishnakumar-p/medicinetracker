package com.medishelf.core.model

data class StorageLocation(
    val id: Long = 0,
    val name: String,
    val room: String = "",
    val notes: String = "",
    /** File name inside app-private photo storage, never an absolute path. */
    val photoFileName: String? = null,
    val requiresRefrigeration: Boolean = false,
)
