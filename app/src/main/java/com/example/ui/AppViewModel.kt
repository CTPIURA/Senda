package com.example.ui

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.TransactionEntity
import com.example.data.TransactionRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val sharedPrefs = context.getSharedPreferences("senda_app_prefs", Context.MODE_PRIVATE)

    private val db = AppDatabase.getDatabase(context)
    private val repository = TransactionRepository(db.transactionDao())

    // UI Transactions state
    val allTransactions: StateFlow<List<TransactionEntity>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Filter properties
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedCategoryFilter = MutableStateFlow("Todos")
    val selectedCategoryFilter = _selectedCategoryFilter.asStateFlow()

    private val _selectedTypeFilter = MutableStateFlow("Todos") // "Todos", "EXPENSE", "INCOME"
    val selectedTypeFilter = _selectedTypeFilter.asStateFlow()

    // Monthly selection (defaults to current month: 0-11)
    private val _selectedMonthFilter = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val selectedMonthFilter = _selectedMonthFilter.asStateFlow()

    private val _selectedYearFilter = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYearFilter = _selectedYearFilter.asStateFlow()

    // Computed filtered list
    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions, _searchQuery, _selectedCategoryFilter, _selectedTypeFilter, _selectedMonthFilter, _selectedYearFilter
    ) { array ->
        val txList = array[0] as List<TransactionEntity>
        val query = array[1] as String
        val catFilter = array[2] as String
        val typeFilter = array[3] as String
        val monthFilter = array[4] as Int
        val yearFilter = array[5] as Int

        txList.filter { tx ->
            val matchesQuery = tx.concept.contains(query, ignoreCase = true) || tx.notes.contains(query, ignoreCase = true)
            val matchesCategory = catFilter == "Todos" || tx.category == catFilter
            val matchesType = typeFilter == "Todos" || tx.type == typeFilter
            
            val calendar = Calendar.getInstance().apply { timeInMillis = tx.dateMillis }
            val matchesMonth = monthFilter == -1 || calendar.get(Calendar.MONTH) == monthFilter
            val matchesYear = yearFilter == -1 || calendar.get(Calendar.YEAR) == yearFilter

            matchesQuery && matchesCategory && matchesType && matchesMonth && matchesYear
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Synchronization state
    private val _syncProgressState = MutableStateFlow("Libre") // "Libre", "Cifrando...", etc.
    val syncProgressState = _syncProgressState.asStateFlow()

    private val _encryptionPassphrase = MutableStateFlow(sharedPrefs.getString("e2ee_passphrase", "ClaveSenda2026") ?: "ClaveSenda2026")
    val encryptionPassphrase = _encryptionPassphrase.asStateFlow()

    private val _unsyncedChangesCount = MutableStateFlow(0)
    val unsyncedChangesCount = _unsyncedChangesCount.asStateFlow()

    // Biometric & Security state
    private val _isBiometricEnabled = MutableStateFlow(sharedPrefs.getBoolean("biometric_enabled", false))
    val isBiometricEnabled = _isBiometricEnabled.asStateFlow()

    private val _isAppLocked = MutableStateFlow(true) // Always starts locked on application launch to protect account data
    val isAppLocked = _isAppLocked.asStateFlow()

    private val _securityPin = MutableStateFlow(sharedPrefs.getString("security_pin", "1234") ?: "1234")
    val securityPin = _securityPin.asStateFlow()

    private val _userAccountEmail = MutableStateFlow(sharedPrefs.getString("user_account_email", "ctpiuraexacta@gmail.com") ?: "ctpiuraexacta@gmail.com")
    val userAccountEmail = _userAccountEmail.asStateFlow()

    fun setUserAccountEmail(email: String) {
        if (email.contains("@")) {
            _userAccountEmail.value = email.trim()
            sharedPrefs.edit().putString("user_account_email", email.trim()).apply()
        }
    }

    private val _biometricStatusMessage = MutableStateFlow("")
    val biometricStatusMessage = _biometricStatusMessage.asStateFlow()

    // Customization & Settings state
    private val _themeMode = MutableStateFlow(sharedPrefs.getString("theme_mode", "DARK") ?: "DARK") // Default dark to reduce fatigue
    val themeMode = _themeMode.asStateFlow()

    private val _accentColor = MutableStateFlow(sharedPrefs.getString("accent_color", "VERDE") ?: "VERDE")
    val accentColor = _accentColor.asStateFlow()

    // Simulated cloud toggle
    private val _isCloudAutoSync = MutableStateFlow(sharedPrefs.getBoolean("cloud_autosync", true))
    val isCloudAutoSync = _isCloudAutoSync.asStateFlow()

    // Payment reminders
    private val _reminders = MutableStateFlow<List<PaymentReminder>>(loadMockReminders())
    val reminders = _reminders.asStateFlow()

    init {
        updateUnsyncedCount()
    }

    fun updateUnsyncedCount() {
        viewModelScope.launch {
            _unsyncedChangesCount.value = repository.getUnsyncedCount()
        }
    }

    // CRUD database actions
    fun addTransaction(concept: String, amount: Double, type: String, category: String, dateMillis: Long, notes: String) {
        viewModelScope.launch {
            val tx = TransactionEntity(
                concept = concept.trim(),
                amount = amount,
                type = type,
                category = category,
                dateMillis = dateMillis,
                notes = notes.trim(),
                isSynced = false,
                lastUpdatedMillis = System.currentTimeMillis()
            )
            repository.insert(tx)
            updateUnsyncedCount()
            triggerAutoSyncIfEnabled()
        }
    }

    fun editTransaction(id: Long, concept: String, amount: Double, type: String, category: String, dateMillis: Long, notes: String) {
        viewModelScope.launch {
            val tx = TransactionEntity(
                id = id,
                concept = concept.trim(),
                amount = amount,
                type = type,
                category = category,
                dateMillis = dateMillis,
                notes = notes.trim(),
                isSynced = false,
                lastUpdatedMillis = System.currentTimeMillis()
            )
            repository.update(tx)
            updateUnsyncedCount()
            triggerAutoSyncIfEnabled()
        }
    }

    fun deleteTransaction(id: Long) {
        viewModelScope.launch {
            repository.softDelete(id)
            updateUnsyncedCount()
            triggerAutoSyncIfEnabled()
        }
    }

    // Custom Filters setters
    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setCategoryFilter(category: String) { _selectedCategoryFilter.value = category }
    fun setTypeFilter(type: String) { _selectedTypeFilter.value = type }
    fun setMonthFilter(month: Int) { _selectedMonthFilter.value = month }
    fun setYearFilter(year: Int) { _selectedYearFilter.value = year }

    // Settings actions
    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        sharedPrefs.edit().putString("theme_mode", mode).apply()
    }

    fun setAccentColor(colorStr: String) {
        _accentColor.value = colorStr
        sharedPrefs.edit().putString("accent_color", colorStr).apply()
    }

    fun setEncryptionPassphrase(ph: String) {
        _encryptionPassphrase.value = ph
        sharedPrefs.edit().putString("e2ee_passphrase", ph).apply()
    }

    fun toggleBiometric(enabled: Boolean) {
        _isBiometricEnabled.value = enabled
        sharedPrefs.edit().putBoolean("biometric_enabled", enabled).apply()
        if (!enabled) {
            _isAppLocked.value = false
        }
    }

    fun setSecurityPin(pin: String) {
        if (pin.length >= 4) {
            _securityPin.value = pin
            sharedPrefs.edit().putString("security_pin", pin).apply()
        }
    }

    fun toggleCloudAutoSync(enabled: Boolean) {
        _isCloudAutoSync.value = enabled
        sharedPrefs.edit().putBoolean("cloud_autosync", enabled).apply()
    }

    fun unlockApp(pin: String): Boolean {
        return if (pin == _securityPin.value) {
            _isAppLocked.value = false
            _biometricStatusMessage.value = "Acceso concedido"
            true
        } else {
            _biometricStatusMessage.value = "PIN incorrecto. Inténtalo de nuevo."
            false
        }
    }

    fun setBiometricStatusMessage(msg: String) {
        _biometricStatusMessage.value = msg
    }

    fun onBiometricSuccess() {
        _isAppLocked.value = false
        _biometricStatusMessage.value = "Autenticación Biométrica Exitosa"
    }

    fun triggerBiometricSimulation() {
        _biometricStatusMessage.value = "Escaneando huella dactilar..."
        viewModelScope.launch {
            delay(1500)
            onBiometricSuccess()
        }
    }

    fun lockAppManually() {
        if (_isBiometricEnabled.value) {
            _isAppLocked.value = true
            _biometricStatusMessage.value = "Dispositivo protegido con autenticación biométrica"
        }
    }

    // Simulated Sync E2EE
    fun runE2EESync() {
        viewModelScope.launch {
            repository.simulateE2EESync(_encryptionPassphrase.value) { status ->
                _syncProgressState.value = status
            }
            updateUnsyncedCount()
        }
    }

    fun runE2EERecovery() {
        viewModelScope.launch {
            repository.simulateE2EERecovery(_encryptionPassphrase.value) { status ->
                _syncProgressState.value = status
            }
            updateUnsyncedCount()
        }
    }

    private fun triggerAutoSyncIfEnabled() {
        if (_isCloudAutoSync.value) {
            runE2EESync()
        }
    }

    // Reminders controls
    fun addReminder(title: String, amount: Double, day: Int) {
        val current = _reminders.value.toMutableList()
        current.add(PaymentReminder(title, amount, day))
        _reminders.value = current
        saveReminders(current)
        scheduleNotificationSimulation(title, amount, day)
    }

    fun deleteReminder(reminder: PaymentReminder) {
        val current = _reminders.value.toMutableList()
        current.remove(reminder)
        _reminders.value = current
        saveReminders(current)
    }

    private fun loadMockReminders(): List<PaymentReminder> {
        val count = sharedPrefs.getInt("reminder_count", -1)
        if (count == -1) {
            // Seed mock reminders
            val seed = listOf(
                PaymentReminder("Alquiler Hogar", 650.0, 5),
                PaymentReminder("Seguro de Auto", 45.0, 15),
                PaymentReminder("Gimnasio", 35.0, 20)
            )
            saveReminders(seed)
            return seed
        }
        val list = mutableListOf<PaymentReminder>()
        for (i in 0 until count) {
            val t = sharedPrefs.getString("rem_title_$i", "") ?: ""
            val a = sharedPrefs.getFloat("rem_amount_$i", 0f).toDouble()
            val d = sharedPrefs.getInt("rem_day_$i", 1)
            list.add(PaymentReminder(t, a, d))
        }
        return list
    }

    private fun saveReminders(list: List<PaymentReminder>) {
        val editor = sharedPrefs.edit()
        editor.putInt("reminder_count", list.size)
        list.forEachIndexed { i, item ->
            editor.putString("rem_title_$i", item.title)
            editor.putFloat("rem_amount_$i", item.amount.toFloat())
            editor.putInt("rem_day_$i", item.dayOfMonth)
        }
        editor.apply()
    }

    private fun scheduleNotificationSimulation(title: String, amount: Double, day: Int) {
        Toast.makeText(
            context,
            "Recordatorio de pago programado para el día $day: $title ($amount PEN)",
            Toast.LENGTH_LONG
        ).show()
    }

    // PDF / CSV export helper
    fun exportReport(type: String) {
        viewModelScope.launch {
            val txList = filteredTransactions.value
            val isExcel = type == "EXCEL"
            val extension = if (isExcel) "csv" else "txt"
            val mimeType = if (isExcel) "text/csv" else "text/plain"

            val fileName = "Senda_Reporte_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.$extension"
            val file = File(context.cacheDir, fileName)

            try {
                file.printWriter().use { writer ->
                    if (isExcel) {
                        // Document formatting as clean CSV (Excel-readable)
                        writer.println("ID,Concepto,Monto,Tipo,Categoria,Fecha,IdSincronizado,Notas")
                        txList.forEach { tx ->
                            val readableDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(tx.dateMillis))
                            writer.println("${tx.id},\"${tx.concept}\",${tx.amount},${tx.type},${tx.category},$readableDate,${tx.isSynced},\"${tx.notes}\"")
                        }
                    } else {
                        // PDF-style plain-text alignment matching report visual boundaries
                        writer.println("==========================================================")
                        writer.println("                SENDA - REPORTE DE GASTOS                 ")
                        writer.println("==========================================================")
                        writer.println("Fecha de Generación: " + SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
                        writer.println("Categoría Filtrada: " + _selectedCategoryFilter.value)
                        writer.println("Tipo Filtrado: " + _selectedTypeFilter.value)
                        writer.println("==========================================================")
                        writer.println(String.format("%-25s %-10s %-12s %-10s", "Concepto", "Categoría", "Tipo", "Monto (PEN)"))
                        writer.println("----------------------------------------------------------")
                        var totalExpenses = 0.0
                        var totalIncomes = 0.0
                        txList.forEach { tx ->
                            val conceptTrunc = if (tx.concept.length > 24) tx.concept.take(21) + "..." else tx.concept
                            writer.println(String.format("%-25s %-10s %-12s %-10.2f", conceptTrunc, tx.category, if (tx.type == "INCOME") "Ingreso" else "Gasto", tx.amount))
                            if (tx.type == "INCOME") totalIncomes += tx.amount else totalExpenses += tx.amount
                        }
                        writer.println("----------------------------------------------------------")
                        writer.println("Total Ingresos : PEN " + String.format("%.2f", totalIncomes))
                        writer.println("Total Gastos   : PEN " + String.format("%.2f", totalExpenses))
                        writer.println("Balance Neto   : PEN " + String.format("%.2f", totalIncomes - totalExpenses))
                        writer.println("Sincronización : Cifrado Extremo-a-Extremo con SendaSync")
                        writer.println("==========================================================")
                    }
                }

                // Share file natively using FileProvider & Intent chooser
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    this.type = mimeType
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Senda Reporte de Gastos")
                    putExtra(Intent.EXTRA_TEXT, "Compartiendo reporte detallado generado desde la aplicación Senda.")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                val chooser = Intent.createChooser(intent, "Exportar Reporte de Senda (${type})").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(context, "Error al exportar reporte: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

data class PaymentReminder(
    val title: String,
    val amount: Double,
    val dayOfMonth: Int
)
