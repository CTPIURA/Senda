package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val concept: String,
    val amount: Double,
    val type: String, // "INCOME" or "EXPENSE"
    val category: String,
    val dateMillis: Long,
    val notes: String = "",
    val isSynced: Boolean = false,
    val isDeletedLocally: Boolean = false,
    val lastUpdatedMillis: Long = System.currentTimeMillis()
)
