package com.example.data

import android.util.Base64
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import java.nio.charset.StandardCharsets
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class TransactionRepository(private val transactionDao: TransactionDao) {

    val allTransactions: Flow<List<TransactionEntity>> = transactionDao.getAllTransactionsFlow()

    suspend fun getUnsyncedCount(): Int {
        return transactionDao.getUnsyncedChanges().size
    }

    suspend fun insert(transaction: TransactionEntity): Long {
        return transactionDao.insertTransaction(transaction)
    }

    suspend fun update(transaction: TransactionEntity) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun softDelete(id: Long) {
        transactionDao.softDeleteTransaction(id, System.currentTimeMillis())
    }

    // Interactive Cloud Synchronization simulation with true End-To-End Encryption (E2EE)
    suspend fun simulateE2EESync(
        passphrase: String,
        onProgress: (String) -> Unit
    ): Boolean {
        if (passphrase.length < 4) {
            onProgress("Error: La clave de cifrado E2EE debe tener al menos 4 caracteres.")
            return false
        }

        val unsynced = transactionDao.getUnsyncedChanges()
        if (unsynced.isEmpty()) {
            onProgress("Todo al día. No hay cambios locales pendientes de subir.")
            delay(1000)
            onProgress("Sincronizado")
            return true
        }

        onProgress("Iniciando sincronización E2EE...")
        delay(800)

        // 1. Serialize local unsynced changes to JSON string or visual content
        onProgress("Cifrando de extremo a extremo (${unsynced.size} transacciones)...")
        delay(1200)

        val encryptedPayload = try {
            encryptData(unsynced.joinToString("|") {
                "${it.id};${it.concept};${it.amount};${it.type};${it.category};${it.dateMillis};${it.isDeletedLocally};${it.lastUpdatedMillis}"
            }, passphrase)
        } catch (e: Exception) {
            onProgress("Error de cifrado local: ${e.localizedMessage}")
            return false
        }

        onProgress("Payload cifrado generado: ${encryptedPayload.take(24)}... [🔒 E2EE con AES-128]")
        delay(1000)

        // 2. Simulate cloud uploading
        onProgress("Subiendo cambios pendientes al servidor seguro de SendaSync...")
        delay(1500)

        // Mark them as synced in local DB
        val idsToMark = unsynced.map { it.id }
        transactionDao.markAsSynced(idsToMark)

        onProgress("¡Cambios sincronizados y guardados en la nube con éxito!")
        delay(1000)
        onProgress("Sincronizado")
        return true
    }

    // Helper functions for real local encryption simulating E2EE
    private fun encryptData(data: String, rawKey: String): String {
        // Derive standard key from passphrase
        val keyBytes = ByteArray(16)
        val rawKeyBytes = rawKey.toByteArray(StandardCharsets.UTF_8)
        System.arraycopy(rawKeyBytes, 0, keyBytes, 0, minOf(rawKeyBytes.size, 16))
        
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val encryptedBytes = cipher.doFinal(data.toByteArray(StandardCharsets.UTF_8))
        return Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
    }

    // Cloud recovery simulation (decrypts a simulated remote cloud payload)
    suspend fun simulateE2EERecovery(
        passphrase: String,
        onProgress: (String) -> Unit
    ): Boolean {
        onProgress("Conectando con el servidor SendaSync...")
        delay(1000)
        onProgress("Descargando copia de seguridad remota cifrada...")
        delay(1200)

        // Simulated remote encrypted payload (containing a few startup sample transactions)
        val demoRemoteText = "1001;Salario Empresa;2500.0;INCOME;Salario;1779717142000;false;1779717142000" +
                "|1002;Compra Supermercado;84.50;EXPENSE;Comida;1779717150000;false;1779717150000" +
                "|1003;Suscripción Streaming;12.99;EXPENSE;Otros;1779717160000;false;1779717160000"

        val encryptedContainer = try {
            encryptData(demoRemoteText, passphrase)
        } catch (e: Exception) {
            onProgress("Error al preparar demo remota.")
            return false
        }

        onProgress("Paquete descargado. Descifrando contenido con clave local...")
        delay(1500)

        try {
            // Decrypt standard derived key
            val keyBytes = ByteArray(16)
            val rawKeyBytes = passphrase.toByteArray(StandardCharsets.UTF_8)
            System.arraycopy(rawKeyBytes, 0, keyBytes, 0, minOf(rawKeyBytes.size, 16))
            val secretKey = SecretKeySpec(keyBytes, "AES")
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            
            val decodedBytes = Base64.decode(encryptedContainer, Base64.NO_WRAP)
            val decryptedText = String(cipher.doFinal(decodedBytes), StandardCharsets.UTF_8)

            onProgress("Copia de seguridad validada. Restableciendo datos locales...")
            delay(1000)

            val records = decryptedText.split("|")
            for (record in records) {
                if (record.isBlank()) continue
                val parts = record.split(";")
                if (parts.size >= 8) {
                    val remoteId = parts[0].toLongOrNull() ?: 0L
                    val concept = parts[1]
                    val amount = parts[2].toDoubleOrNull() ?: 0.0
                    val type = parts[3]
                    val category = parts[4]
                    val dateMillis = parts[5].toLongOrNull() ?: System.currentTimeMillis()
                    val isDeleted = parts[6].toBoolean()
                    val lastUpdated = parts[7].toLongOrNull() ?: System.currentTimeMillis()

                    val localEntity = TransactionEntity(
                        id = 0L, // Insert as new local copy
                        concept = concept,
                        amount = amount,
                        type = type,
                        category = category,
                        dateMillis = dateMillis,
                        notes = "Restaurado de nube SendaSync",
                        isSynced = true,
                        isDeletedLocally = isDeleted,
                        lastUpdatedMillis = lastUpdated
                    )
                    transactionDao.insertTransaction(localEntity)
                }
            }
            onProgress("¡Datos remotos sincronizados con éxito!")
            delay(1000)
            onProgress("Sincronizado")
            return true
        } catch (e: Exception) {
            onProgress("Error de descifrado: Clave incorrecta o paquete dañado.")
            delay(2000)
            onProgress("Error de clave")
            return false
        }
    }
}
