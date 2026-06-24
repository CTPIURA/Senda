package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import kotlinx.coroutines.delay
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.TransactionEntity
import com.example.ui.AppViewModel
import com.example.ui.PaymentReminder
import com.example.ui.ExchangeRateState
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.Rose500
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Slate900
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate700
import com.example.ui.theme.Sky600
import com.example.ui.theme.Sky400
import com.example.ui.theme.Slate100
import com.example.ui.theme.Amber500
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : androidx.fragment.app.FragmentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("SendaCrash", "FATAL EXCEPTION in thread: ${thread.name}", throwable)
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val isDark = when (themeMode) {
                "CREPUSCULO", "DARK" -> true
                "DIURNO", "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val isAppLocked by viewModel.isAppLocked.collectAsStateWithLifecycle()

                    AnimatedContent(
                        targetState = isAppLocked,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) togetherWith fadeOut(animationSpec = tween(400))
                        },
                        label = "LockScreenTransition"
                    ) { locked ->
                        if (locked) {
                            BiometricLockScreen(
                                viewModel = viewModel,
                                onTriggerBiometric = { showHardwareBiometricPrompt() }
                            )
                        } else {
                            MainContentScreen(viewModel)
                        }
                    }
                }
            }
        }
    }

    fun showHardwareBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        when (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)) {
            BiometricManager.BIOMETRIC_SUCCESS -> {
                val executor = ContextCompat.getMainExecutor(this)
                val biometricPrompt = BiometricPrompt(this, executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            viewModel.setBiometricStatusMessage("Error: $errString")
                        }

                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            viewModel.onBiometricSuccess()
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            viewModel.setBiometricStatusMessage("Huella no reconocida")
                        }
                    })

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Acceso Biométrico - Senda")
                    .setSubtitle("Inicia sesión usando tu huella, rostro o patrón registrado")
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                    .build()

                biometricPrompt.authenticate(promptInfo)
            }
            else -> {
                // Return fallback simulation so that development preview & users without device setup can still use Senda Security
                viewModel.setBiometricStatusMessage("No se detectó huella dactilar. Simulando sensor...")
                viewModel.triggerBiometricSimulation()
            }
        }
    }
}

// 1. STUNNING BIOMETRIC / PIN SECURITY COVERS
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BiometricLockScreen(viewModel: AppViewModel, onTriggerBiometric: () -> Unit) {
    val biometricMsg by viewModel.biometricStatusMessage.collectAsStateWithLifecycle()
    val correctPin by viewModel.securityPin.collectAsStateWithLifecycle()
    val userEmail by viewModel.userAccountEmail.collectAsStateWithLifecycle()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    var pinEntered by remember { mutableStateOf("") }

    // Automatically prompt for fingerprint/biometrics on app launch
    LaunchedEffect(Unit) {
        if (isBiometricEnabled) {
            onTriggerBiometric()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // App stylized vector shield
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Shield Locked",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "SENDA",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 5.sp,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Acceso Protegido por Biometría & Cifrado",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
            )

            // Account and Verification Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(12.dp, shape = RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Profile Account Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (userEmail.isNotEmpty()) userEmail.take(1).uppercase() else "S",
                                color = MaterialTheme.colorScheme.onPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                        }
                        Column(
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Cuenta de Acceso",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = userEmail,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Introduce tu PIN o usa tus datos biométricos",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Stylized secret circles indicating PIN length
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(bottom = 16.dp)
                    ) {
                        for (i in 1..4) {
                            val active = pinEntered.length >= i
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (active) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    if (biometricMsg.isNotEmpty()) {
                        Text(
                            text = biometricMsg,
                            color = if (biometricMsg.contains("concedido") || biometricMsg.contains("Exitosa")) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.error,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }

                    // Real / Fallback Biometric Scan button
                    Button(
                        onClick = { onTriggerBiometric() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("biometric_touch_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Fingerprint,
                            contentDescription = "Huella",
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Acceso Biométrico Rápido", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "O ingresa con teclado numérico:",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Rounded numeric PIN keyboard
                    val pinRows = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "OK")
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (row in pinRows) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (key in row) {
                                    Button(
                                        onClick = {
                                            when (key) {
                                                "C" -> if (pinEntered.isNotEmpty()) pinEntered = pinEntered.dropLast(1)
                                                "OK" -> {
                                                    viewModel.unlockApp(pinEntered)
                                                    pinEntered = ""
                                                }
                                                else -> {
                                                    if (pinEntered.length < 4) {
                                                        pinEntered += key
                                                    }
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = when (key) {
                                                "OK" -> MaterialTheme.colorScheme.primaryContainer
                                                "C" -> MaterialTheme.colorScheme.errorContainer
                                                else -> MaterialTheme.colorScheme.surfaceVariant
                                            },
                                            contentColor = when (key) {
                                                "OK" -> MaterialTheme.colorScheme.onPrimaryContainer
                                                "C" -> MaterialTheme.colorScheme.onErrorContainer
                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                            }
                                        ),
                                        contentPadding = PaddingValues(0.dp)
                                    ) {
                                        Text(text = key, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "PIN por defecto: 1234",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

// 2. MAIN APP CONTAINER & INTERACTIVE SCAFFOLD
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContentScreen(viewModel: AppViewModel) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Inicio, 1: Movimientos, 2: Reportes, 3: Ajustes
    val unsyncedCount by viewModel.unsyncedChangesCount.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgressState.collectAsStateWithLifecycle()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()

    var showAddTransactionSheet by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SENDA",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            letterSpacing = 2.sp
                        )
                        Text(
                            text = "Control Financiero Inteligente",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                },
                actions = {
                    // Sync Quick Actions Badge
                    Surface(
                        onClick = { viewModel.runE2EESync() },
                        shape = RoundedCornerShape(12.dp),
                        color = if (unsyncedCount > 0) MaterialTheme.colorScheme.errorContainer 
                                else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("sync_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (syncProgress == "Sincronizado") Icons.Default.CloudDone 
                                             else if (syncProgress.contains("Error")) Icons.Default.CloudOff 
                                             else Icons.Default.Sync,
                                contentDescription = "Sync state",
                                modifier = Modifier.size(16.dp),
                                tint = if (unsyncedCount > 0) MaterialTheme.colorScheme.onErrorContainer 
                                       else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (syncProgress.contains("Cifrando") || syncProgress.contains("Subiendo")) "Syncing..." 
                                       else if (unsyncedCount > 0) "$unsyncedCount Pend." 
                                       else "Protegido",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (unsyncedCount > 0) MaterialTheme.colorScheme.onErrorContainer 
                                       else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    // Direct biometrics logout trigger
                    if (isBiometricEnabled) {
                        IconButton(onClick = { viewModel.lockAppManually() }) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = "Lock App")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.shadow(1.dp)
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .shadow(16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                val items = listOf(
                    Triple(0, Icons.Outlined.Dashboard, "Inicio"),
                    Triple(1, Icons.Outlined.CompareArrows, "Movimientos"),
                    Triple(2, Icons.Outlined.Analytics, "Reportes"),
                    Triple(3, Icons.Outlined.Settings, "Ajustes")
                )
                items.forEach { (tabId, icon, label) ->
                    val isSelected = selectedTab == tabId
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTab = tabId },
                        icon = { Icon(imageVector = icon, contentDescription = label) },
                        label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("tab_$tabId")
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                FloatingActionButton(
                    onClick = {
                        transactionToEdit = null
                        showAddTransactionSheet = true
                    },
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary,
                    modifier = Modifier.testTag("add_tx_fab")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Añadir Movimiento")
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "NavigationTabTransition"
            ) { tab ->
                when (tab) {
                    0 -> DashboardTab(
                        viewModel = viewModel,
                        onEditTx = { tx ->
                            transactionToEdit = tx
                            showAddTransactionSheet = true
                        }
                    )
                    1 -> OperationsListTab(
                        viewModel = viewModel,
                        onEditTx = { tx ->
                            transactionToEdit = tx
                            showAddTransactionSheet = true
                        }
                    )
                    2 -> ReportsTab(viewModel = viewModel)
                    3 -> SettingsTab(viewModel = viewModel)
                }
            }

            if (showAddTransactionSheet) {
                TransactionFormDialog(
                    viewModel = viewModel,
                    editingTx = transactionToEdit,
                    onDismiss = { showAddTransactionSheet = false }
                )
            }
        }
    }
}

// Helper icons function for categories
fun getCategoryIcon(category: String): ImageVector {
    return when (category) {
        "Comida" -> Icons.Default.Restaurant
        "Hogar" -> Icons.Default.Home
        "Ocio" -> Icons.Default.LocalPlay
        "Transporte" -> Icons.Default.DirectionsCar
        "Salud" -> Icons.Default.MedicalServices
        "Educación" -> Icons.Default.School
        "Salario" -> Icons.Default.Payments
        "Inversiones" -> Icons.Default.TrendingUp
        "Ventas" -> Icons.Default.Storefront
        else -> Icons.Default.Label
    }
}

fun getCategoryColor(category: String): Color {
    return when (category) {
        "Comida" -> Color(0xFFF97316) // orange
        "Hogar" -> Color(0xFF3B82F6) // blue
        "Ocio" -> Color(0xFFEC4899) // pink
        "Transporte" -> Color(0xFF06B6D4) // cyan
        "Salud" -> Color(0xFFEF4444) // red
        "Educación" -> Color(0xFF8B5CF6) // purple
        "Salario" -> Color(0xFF10B981) // emerald
        "Inversiones" -> Color(0xFF0ea5e9) // sky
        "Ventas" -> Color(0xFFF59E0B) // amber
        else -> Color(0xFF64748B) // slate
    }
}

// 3. TAB 0: INTERACTIVE DASHBOARD VIEW (INICIO)
@Composable
fun DashboardTab(
    viewModel: AppViewModel,
    onEditTx: (TransactionEntity) -> Unit
) {
    val txs by viewModel.allTransactions.collectAsStateWithLifecycle()
    val filteredList by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    // Aggregate values
    val totalIncomes = remember(txs) {
        txs.filter { it.type == "INCOME" }.sumOf { it.amount }
    }
    val totalExpenses = remember(txs) {
        txs.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    }
    val currentBalance = totalIncomes - totalExpenses

    val df = DecimalFormat("#,##0.00 PEN")

    val userEmail by viewModel.userAccountEmail.collectAsStateWithLifecycle()
    val remindersList by viewModel.reminders.collectAsStateWithLifecycle()

    val userDisplayName = remember(userEmail) {
        val index = userEmail.indexOf('@')
        if (index > 0) {
            userEmail.substring(0, index).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        } else {
            userEmail.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
    }

    val spentPercentage = remember(totalIncomes, totalExpenses) {
        if (totalIncomes > 0) {
            (totalExpenses / totalIncomes).toFloat()
        } else {
            0f
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. User Welcome Header Card with Profile Info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, shape = RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stylish Gradient Avatar
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = userDisplayName.take(1).uppercase(Locale.getDefault()),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "¡Hola, $userDisplayName!",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    
                    val currentDateStr = remember {
                        val sdf = SimpleDateFormat("EEEE, d 'de' MMMM", Locale("es", "PE"))
                        sdf.format(Date()).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("es", "PE")) else it.toString() }
                    }
                    Text(
                        text = currentDateStr,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Quick Security Status Badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Protegido",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Seguro",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Real-time US Dollar Exchange Rate Card
        ExchangeRateCard(viewModel = viewModel)

        // Balance Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(4.dp, shape = RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "BALANCE DISPONIBLE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                )
                Text(
                    text = df.format(currentBalance),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = if (currentBalance >= 0) MaterialTheme.colorScheme.onPrimaryContainer 
                            else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Divider(
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                    modifier = Modifier.padding(vertical = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowUpward,
                                contentDescription = null,
                                tint = Emerald500,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Ingresos",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = df.format(totalIncomes),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Emerald500
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(40.dp)
                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.ArrowDownward,
                                contentDescription = null,
                                tint = Rose500,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Gastos",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = df.format(totalExpenses),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Rose500
                        )
                    }
                }
            }
        }

        // 1. Financial Health Indicator Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(2.dp, shape = RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "ÍNDICE DE SALUD FINANCIERA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    
                    val statusText = when {
                        totalIncomes == 0.0 && totalExpenses == 0.0 -> "Sin datos"
                        spentPercentage > 0.8f || currentBalance < 0 -> "Crítico"
                        spentPercentage > 0.5f -> "Moderado"
                        else -> "Excelente"
                    }
                    
                    val statusColor = when {
                        totalIncomes == 0.0 && totalExpenses == 0.0 -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        spentPercentage > 0.8f || currentBalance < 0 -> Rose500
                        spentPercentage > 0.5f -> Amber500
                        else -> Emerald500
                    }
                    
                    Text(
                        text = statusText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = statusColor,
                        modifier = Modifier
                            .background(statusColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Progress bar indicating expenditure ratio
                val animatedProgress by animateFloatAsState(
                    targetValue = spentPercentage.coerceIn(0f, 1f),
                    animationSpec = tween(1000)
                )

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = when {
                        spentPercentage > 0.8f || currentBalance < 0 -> Rose500
                        spentPercentage > 0.5f -> Amber500
                        else -> Emerald500
                    },
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                val adviceText = when {
                    totalIncomes == 0.0 && totalExpenses == 0.0 -> {
                        "Comienza añadiendo tus ingresos y gastos para calcular de forma inteligente tu nivel de ahorro mensual."
                    }
                    currentBalance < 0 -> {
                        "⚠️ Alerta: Tus gastos superan tus ingresos este mes. Te recomendamos recortar suscripciones o compras secundarias de inmediato."
                    }
                    spentPercentage > 0.8f -> {
                        "⚠️ Advertencia: Has consumido el ${(spentPercentage * 100).toInt()}% de tus ingresos en gastos. Queda poco margen para imprevistos."
                    }
                    spentPercentage > 0.5f -> {
                        "⚖️ Balance Moderado: Has gastado un ${(spentPercentage * 100).toInt()}% de tus ingresos. Es un buen momento para priorizar el ahorro."
                    }
                    else -> {
                        "✨ ¡Felicitaciones! Estás ahorrando más de la mitad de tus ingresos. Tu senda financiera se encuentra muy sólida y protegida."
                    }
                }

                Text(
                    text = adviceText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        // Dynamic Smart Savings Analysis & Tips Card
        SavingsAnalysisSection(
            txs = txs,
            totalIncomes = totalIncomes,
            totalExpenses = totalExpenses
        )

        // 2. Upcoming Payment Reminders Checklist (Quick Carousel)
        if (remindersList.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "RECORDATORIOS DE PAGO CLAVE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp)
                ) {
                    remindersList.forEach { reminder ->
                        Card(
                            modifier = Modifier
                                .width(200.dp)
                                .shadow(1.dp, shape = RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Event,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Día ${reminder.dayOfMonth}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = reminder.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "PEN ${df.format(reminder.amount).replace(" PEN", "")}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Animated Category Budget Donut Chart
        if (txs.none { it.type == "EXPENSE" }) {
            // Emptystate dashboard
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Savings,
                        contentDescription = "Ahorro vacio",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sin gastos registrados aún",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Añade tu primer gasto para ver el gráfico de distribución.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, shape = RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = "DISTRIBUCIÓN DE GASTOS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    val expenseTxs = txs.filter { it.type == "EXPENSE" }
                    val totalSum = expenseTxs.sumOf { it.amount }
                    val categoryGroups = expenseTxs.groupBy { it.category }
                        .mapValues { it.value.sumOf { tx -> tx.amount } }
                        .toList()
                        .sortedByDescending { it.second }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Drawing custom animated Donut Chart using Core Canvas
                        var isAnimatedLoaded by remember { mutableStateOf(false) }
                        LaunchedEffect(key1 = true) {
                            delay(100)
                            isAnimatedLoaded = true
                        }

                        val progressAnimate by animateFloatAsState(
                            targetValue = if (isAnimatedLoaded) 1f else 0f,
                            animationSpec = tween(durationMillis = 1000)
                        )

                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.size(110.dp)) {
                                var currentAngle = -90f
                                for (item in categoryGroups) {
                                    val sweep = ((item.second / totalSum) * 360f).toFloat() * progressAnimate
                                    drawArc(
                                        color = getCategoryColor(item.first),
                                        startAngle = currentAngle,
                                        sweepAngle = sweep,
                                        useCenter = false,
                                        style = Stroke(width = 16.dp.toPx(), cap = StrokeCap.Round),
                                        size = Size(size.width, size.height)
                                    )
                                    currentAngle += sweep
                                }
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "Gastos",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = df.format(totalSum).take(9),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Category Legends Layout
                        Column(
                            modifier = Modifier.weight(1.2f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            categoryGroups.take(4).forEach { (cat, sum) ->
                                val pct = (sum / totalSum * 100).toInt()
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .clip(CircleShape)
                                                .background(getCategoryColor(cat))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = cat,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.widthIn(max = 70.dp)
                                        )
                                    }
                                    Text(
                                        text = "$pct%",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Movements Title
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MOVIMIENTOS RECIENTES",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Text(
                text = "Ver Todos",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Sky600,
                modifier = Modifier.clickable {  } // Triggers view, but fits inline
            )
        }

        // Recent movements list
        val recentList = filteredList.take(6)
        if (recentList.isEmpty()) {
            Text(
                text = "No hay movimientos registrados para este mes.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
            )
        } else {
            recentList.forEach { tx ->
                TransactionRow(tx = tx, onEdit = onEditTx)
            }
        }
    }
}

// 4. TAB 1: OPERATIONS / TRANSACTIONS LIST TAB (MOVIMIENTOS)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationsListTab(
    viewModel: AppViewModel,
    onEditTx: (TransactionEntity) -> Unit
) {
    val filteredList by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCat by viewModel.selectedCategoryFilter.collectAsStateWithLifecycle()
    val selectedType by viewModel.selectedTypeFilter.collectAsStateWithLifecycle()
    val selectedMonth by viewModel.selectedMonthFilter.collectAsStateWithLifecycle()
    val selectedYear by viewModel.selectedYearFilter.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Search & Filters inputs
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(it) },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_tx_input"),
            placeholder = { Text("Buscar por concepto o notas...") },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setSearchQuery("") }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            ),
            singleLine = true
        )

        // Type selection filtering chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val types = listOf(
                Pair("Todos", "Todos"),
                Pair("EXPENSE", "Gastos"),
                Pair("INCOME", "Ingresos")
            )
            types.forEach { (key, label) ->
                val isSelected = selectedType == key
                FilterChip(
                    selected = isSelected,
                    onClick = { viewModel.setTypeFilter(key) },
                    label = { Text(label, fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (key == "EXPENSE") Rose500.copy(alpha = 0.15f) 
                                                 else if (key == "INCOME") Emerald500.copy(alpha = 0.15f) 
                                                 else MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = if (key == "EXPENSE") Rose500 
                                             else if (key == "INCOME") Emerald500 
                                             else MaterialTheme.colorScheme.primary
                    )
                )
            }
        }

        // Horizontal Category Quick Filter
        val activeCategoriesList = listOf("Todos", "Comida", "Hogar", "Ocio", "Transporte", "Salud", "Educación", "Salario", "Inversiones", "Ventas")
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                activeCategoriesList.forEach { cat ->
                    val isSelected = selectedCat == cat
                    Surface(
                        onClick = { viewModel.setCategoryFilter(cat) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.padding(vertical = 4.dp).testTag("chip_cat_$cat")
                    ) {
                        Text(
                            text = cat,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        // Month Selector dropdown simulator
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Months items
            val monthsName = listOf("Ene", "Feb", "Mar", "Abr", "May", "Jun", "Jul", "Ago", "Set", "Oct", "Nov", "Dic")
            var showMonthMenu by remember { mutableStateOf(false) }

            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showMonthMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (selectedMonth == -1) "Mes: Todos" else "Mes: ${monthsName[selectedMonth]}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                }

                DropdownMenu(expanded = showMonthMenu, onDismissRequest = { showMonthMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Todos los meses", fontWeight = FontWeight.SemiBold) },
                        onClick = {
                            viewModel.setMonthFilter(-1)
                            showMonthMenu = false
                        }
                    )
                    monthsName.forEachIndexed { idx, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                viewModel.setMonthFilter(idx)
                                showMonthMenu = false
                            }
                        )
                    }
                }
            }

            // Year dropdown simulator
            var showYearMenu by remember { mutableStateOf(false) }
            val yearOptions = listOf(-1, 2026, 2027)

            Box(modifier = Modifier.weight(1f)) {
                OutlinedButton(
                    onClick = { showYearMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (selectedYear == -1) "Año: Todos" else "Año: $selectedYear",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                }

                DropdownMenu(expanded = showYearMenu, onDismissRequest = { showYearMenu = false }) {
                    yearOptions.forEach { year ->
                        DropdownMenuItem(
                            text = { Text(if (year == -1) "Todos los años" else year.toString()) },
                            onClick = {
                                viewModel.setYearFilter(year)
                                showYearMenu = false
                            }
                        )
                    }
                }
            }
        }

        // Transactions scrolling container or Emptystate
        if (filteredList.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.SearchOff,
                        contentDescription = "No matches",
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sin coincidencia en transacciones",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "Modifica los filtros de tipo, categoría o fechas.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredList, key = { it.id }) { tx ->
                    TransactionRow(tx = tx, onEdit = onEditTx)
                }
            }
        }
    }
}

// 5. TRANSACTION ROW DISPLAY CARD WITH EDITING & SWIPE CONTROLS
@Composable
fun TransactionRow(
    tx: TransactionEntity,
    onEdit: (TransactionEntity) -> Unit
) {
    val df = DecimalFormat("#,##0.00 PEN")
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit(tx) }
            .testTag("tx_row_${tx.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circle filled Category Color
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(getCategoryColor(tx.category).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getCategoryIcon(tx.category),
                    contentDescription = null,
                    tint = getCategoryColor(tx.category),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.concept,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = tx.category + if (tx.notes.isNotEmpty()) " • ${tx.notes}" else "",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.padding(start = 12.dp)
            ) {
                val isIncome = tx.type == "INCOME"
                Text(
                    text = (if (isIncome) "+" else "-") + df.format(tx.amount),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = if (isIncome) Emerald500 else Rose500
                )
                
                // Formatted date indicator
                val dateStr = SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(tx.dateMillis))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!tx.isSynced) {
                        Icon(
                            imageVector = Icons.Default.CloudQueue,
                            contentDescription = "No sincronizado",
                            tint = Amber500,
                            modifier = Modifier.size(10.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                        text = dateStr,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

// 6. FORM OVERLAY: ADD / EDIT TRANSACTION DIALOG
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionFormDialog(
    viewModel: AppViewModel,
    editingTx: TransactionEntity?,
    onDismiss: () -> Unit
) {
    var concept by remember { mutableStateOf(editingTx?.concept ?: "") }
    var amountStr by remember { mutableStateOf(editingTx?.amount?.toString() ?: "") }
    var notes by remember { mutableStateOf(editingTx?.notes ?: "") }
    var type by remember { mutableStateOf(editingTx?.type ?: "EXPENSE") } // "EXPENSE" or "INCOME"
    var category by remember { mutableStateOf(editingTx?.category ?: "Comida") }

    AlertDialog(
        onDismissRequest = { onDismiss() },
        modifier = Modifier.testTag("tx_form_dialog"),
        title = {
            Text(
                text = if (editingTx != null) "Editar Transacción" else "Nuevo Registro",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // INCOME / EXPENSE segment buttons
                Row(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            type = "EXPENSE"
                            if (category == "Salario" || category == "Inversiones" || category == "Ventas") {
                                category = "Comida"
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "EXPENSE") Color(0xFF31111D) else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (type == "EXPENSE") Color(0xFFFFD8E4) else MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp) // customized boundary
                    ) {
                        Text("Gasto", fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Button(
                        onClick = {
                            type = "INCOME"
                            category = "Salario"
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (type == "INCOME") Color(0xFF2E3515) else MaterialTheme.colorScheme.primaryContainer,
                            contentColor = if (type == "INCOME") Color(0xFFE3E9C3) else MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Ingreso", fontWeight = FontWeight.Bold)
                    }
                }

                // Concept name
                OutlinedTextField(
                    value = concept,
                    onValueChange = { concept = it },
                    label = { Text("Concepto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("form_input_concept")
                )

                // Amount PEN
                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = { Text("Monto (PEN)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("form_input_amount")
                )

                // Category select drop list
                val activeCats = if (type == "EXPENSE") {
                    listOf("Comida", "Hogar", "Ocio", "Transporte", "Salud", "Educación", "Otros")
                } else {
                    listOf("Salario", "Inversiones", "Ventas", "Otros")
                }

                Text("Categoría:", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activeCats.forEach { cat ->
                        val isSel = category == cat
                        Surface(
                            onClick = { category = cat },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSel) getCategoryColor(cat) else MaterialTheme.colorScheme.surface,
                            contentColor = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(cat, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                        }
                    }
                }

                // Notes optional
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas (Opcional)") }
                )
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (editingTx != null) {
                    TextButton(
                        onClick = {
                            viewModel.deleteTransaction(editingTx.id)
                            onDismiss()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Eliminar")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Row {
                    TextButton(onClick = { onDismiss() }) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val amt = amountStr.toDoubleOrNull() ?: 0.0
                            if (concept.isNotBlank() && amt > 0) {
                                if (editingTx != null) {
                                    viewModel.editTransaction(
                                        id = editingTx.id,
                                        concept = concept,
                                        amount = amt,
                                        type = type,
                                        category = category,
                                        dateMillis = editingTx.dateMillis,
                                        notes = notes
                                    )
                                } else {
                                    viewModel.addTransaction(
                                        concept = concept,
                                        amount = amt,
                                        type = type,
                                        category = category,
                                        dateMillis = System.currentTimeMillis(),
                                        notes = notes
                                    )
                                }
                                onDismiss()
                            }
                        },
                        modifier = Modifier.testTag("form_submit_button")
                    ) {
                        Text("Guardar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    )
}

// 7. TAB 2: REPORTS & GENERATOR VIEW (REPORTES)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsTab(viewModel: AppViewModel) {
    val filteredList by viewModel.filteredTransactions.collectAsStateWithLifecycle()
    val remindersList by viewModel.reminders.collectAsStateWithLifecycle()
    val scrollState = rememberScrollState()

    var showReminderDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Export Tools Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Summarize,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )

                Text(
                    text = "Exportación de Reportes",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Text(
                    text = "Genera documentos descargables de forma local con toda tu información estructurada para finanzas.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { viewModel.exportReport("PDF") },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_pdf_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PDF", fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { viewModel.exportReport("EXCEL") },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("export_excel_button"),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.GridOn, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Excel (CSV)", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Monthly Breakdown Stats Panel
        val expenseTxs = filteredList.filter { it.type == "EXPENSE" }
        val totalSpending = expenseTxs.sumOf { it.amount }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "RESUMEN ESTADÍSTICO DE ESTE PERÍODO",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (expenseTxs.isEmpty()) {
                    Text(
                        text = "Registra gastos para ver el análisis de barras de consumo.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    val grouped = expenseTxs.groupBy { it.category }
                        .mapValues { it.value.sumOf { tx -> tx.amount } }
                        .toList()
                        .sortedByDescending { it.second }

                    grouped.forEach { (cat, sum) ->
                        val ratio = (sum / totalSpending).toFloat()
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(cat, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    String.format("%.2f PEN (%.1f%%)", sum, ratio * 100),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(6.dp))

                            // Interactive Bar sliding graph
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(ratio)
                                        .height(8.dp)
                                        .clip(CircleShape)
                                        .background(getCategoryColor(cat))
                                )
                            }
                        }
                    }
                }
            }
        }

        // Advanced Recharts-Style Interactive Analytics Dashboard
        RechartsAnalyticsDashboard(txs = filteredList)

        // Dynamic Smart Savings Analysis & Tips Card for the Selected Period
        val periodIncomes = remember(filteredList) {
            filteredList.filter { it.type == "INCOME" }.sumOf { it.amount }
        }
        SavingsAnalysisSection(
            txs = filteredList,
            totalIncomes = periodIncomes,
            totalExpenses = totalSpending
        )

        // TAB 2: Payment reminders checklist
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "RECORDATORIOS DE PAGO",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Notificaciones locales",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }

                    IconButton(
                        onClick = { showReminderDialog = true },
                        modifier = Modifier.testTag("add_reminder_btn")
                    ) {
                        Icon(imageVector = Icons.Default.AddAlert, contentDescription = "Programar Alerta")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (remindersList.isEmpty()) {
                    Text(
                        text = "No has creado ningún recordatorio aún.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                } else {
                    remindersList.forEach { rem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Amber500,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rem.title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    text = "Día de pago vencimiento: ${rem.dayOfMonth} de cada mes",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                            Text(
                                text = "${rem.amount.toInt()} PEN",
                                fontWeight = FontWeight.Black,
                                fontSize = 13.sp,
                                color = Rose500
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { viewModel.deleteReminder(rem) }) {
                                Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showReminderDialog) {
        var rTitle by remember { mutableStateOf("") }
        var rAmount by remember { mutableStateOf("") }
        var rDay by remember { mutableStateOf("10") }

        AlertDialog(
            onDismissRequest = { showReminderDialog = false },
            title = { Text("Programar Alerta de Pago", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Añade pagos fijos mensuales como el agua, luz o alquiler para que la app simule recordatorios automáticos.", fontSize = 12.sp)
                    OutlinedTextField(
                        value = rTitle,
                        onValueChange = { rTitle = it },
                        label = { Text("Título de cobro") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = rAmount,
                        onValueChange = { rAmount = it },
                        label = { Text("Importe (PEN)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = rDay,
                        onValueChange = { rDay = it },
                        label = { Text("Día de Pago (1-31)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val dVal = rDay.toIntOrNull() ?: 10
                        val aVal = rAmount.toDoubleOrNull() ?: 0.0
                        if (rTitle.isNotBlank() && aVal > 0) {
                            viewModel.addReminder(rTitle, aVal, dVal)
                            showReminderDialog = false
                        }
                    }
                ) {
                    Text("Agendar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReminderDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// 8. TAB 3: PERSONALIZATION, CRYPTO & SYNC SETTINGS (AJUSTES)
@Composable
fun SettingsTab(viewModel: AppViewModel) {
    val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
    val passphrase by viewModel.encryptionPassphrase.collectAsStateWithLifecycle()
    val cloudSynced by viewModel.isCloudAutoSync.collectAsStateWithLifecycle()
    val pinCode by viewModel.securityPin.collectAsStateWithLifecycle()
    val syncText by viewModel.syncProgressState.collectAsStateWithLifecycle()
    val userEmail by viewModel.userAccountEmail.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()
    var showPinDialog by remember { mutableStateOf(false) }
    var showEmailDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 0. Account Profile Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "PERFIL DE CUENTA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (userEmail.isNotEmpty()) userEmail.take(1).uppercase() else "S",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (userEmail.isNotEmpty()) userEmail else "Sin correo configurado",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Socio Senda Pro • Nivel de Encriptación Alto",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    OutlinedButton(
                        onClick = { showEmailDialog = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Editar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Theme Personalization segment
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "PERSONALIZACIÓN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Tema Oscuro Personalizado", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Protege tus ojos en cualquier entorno", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val listThemes = listOf("LIGHT" to "Diurno", "DARK" to "Crepúsculo")
                    listThemes.forEach { (key, name) ->
                        val active = themeMode == key
                        Button(
                            onClick = { viewModel.setThemeMode(key) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                                contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        ) {
                            Text(name, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Biometrics & Security Lock Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "SEGURIDAD BIOMÉTRICA",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Autenticación Biométrica", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Verificación nativa por huella dactilar al entrar", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { viewModel.toggleBiometric(it) },
                        modifier = Modifier.testTag("biometric_switch")
                    )
                }

                Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PIN de Bloqueo PIN", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("PIN actual: $pinCode", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    OutlinedButton(
                        onClick = { showPinDialog = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Ajustar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Cloud E2EE SendaSync Configurations
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "SINCRONIZACIÓN CIFRADA (E2EE)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Sincronizar Cambios", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Respaldar cambios locales cifrados automáticamente", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                    Switch(
                        checked = cloudSynced,
                        onCheckedChange = { viewModel.toggleCloudAutoSync(it) }
                    )
                }

                Text("Clave de cifrado de extremo a extremo (Local AES-128):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { viewModel.setEncryptionPassphrase(it) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("Escribe tu clave secreta...") }
                )

                Text(
                    text = "⚠️ Guarda esta frase. Nadie más (ni siquiera SendaSync) tiene acceso a tus transacciones descifradas, cumpliendo con la privacidad absoluta de tus datos.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.runE2EESync() },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Subir Datos", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { viewModel.runE2EERecovery() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Bajar Copia", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (syncText.isNotEmpty() && syncText != "Sincronizado" && syncText != "Libre") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(syncText, fontSize = 12.sp, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        var newPin by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Modificar PIN de Protección", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Define un PIN numérico de 4 dígitos para acceder localmente de forma manual.", fontSize = 12.sp)
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("Nuevo PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPin.length == 4) {
                            viewModel.setSecurityPin(newPin)
                            showPinDialog = false
                        }
                    }
                ) {
                    Text("Establecer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showEmailDialog) {
        var emailInput by remember { mutableStateOf(userEmail) }
        AlertDialog(
            onDismissRequest = { showEmailDialog = false },
            title = { Text("Editar Correo de Cuenta", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Ingresa la dirección de correo electrónico asociada a tu cuenta Pro protegida.", fontSize = 12.sp)
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = { emailInput = it },
                        label = { Text("Correo Electrónico") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (emailInput.contains("@") && emailInput.trim().isNotEmpty()) {
                            viewModel.setUserAccountEmail(emailInput)
                            showEmailDialog = false
                        }
                    }
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEmailDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// 9. DYNAMIC SAVINGS ADVISOR & ANALYTICS COMPONENT
data class AdviceInfo(
    val title: String,
    val desc: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun SavingsAnalysisSection(
    txs: List<TransactionEntity>,
    totalIncomes: Double,
    totalExpenses: Double
) {
    val df = DecimalFormat("#,##0.00")
    
    // Math Analysis
    val balance = totalIncomes - totalExpenses
    val savingsRate = if (totalIncomes > 0) ((totalIncomes - totalExpenses) / totalIncomes) * 100.0 else 0.0
    
    // Find highest expense category
    val expenseTxs = txs.filter { it.type == "EXPENSE" }
    val groupedExpenses = expenseTxs.groupBy { it.category }
        .mapValues { it.value.sumOf { tx -> tx.amount } }
    val highestExpenseCategory = groupedExpenses.maxByOrNull { it.value }?.key ?: "Ninguno"
    val maxExpenseAmount = groupedExpenses.maxByOrNull { it.value }?.value ?: 0.0
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape = RoundedCornerShape(20.dp))
            .testTag("savings_analysis_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section Title
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Savings,
                        contentDescription = "Tips de Ahorro",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "ANÁLISIS Y CONSEJOS DE AHORRO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Sugerencias inteligentes basadas en tus hábitos",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Savings Rate Progress / Score
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = "Tu Tasa de Ahorro",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        val rateFormatted = if (savingsRate < 0.0) "0.0%" else String.format(Locale.getDefault(), "%.1f%%", savingsRate)
                        Text(
                            text = rateFormatted,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Black,
                            color = when {
                                savingsRate >= 30.0 -> Emerald500
                                savingsRate >= 10.0 -> MaterialTheme.colorScheme.primary
                                else -> Rose500
                            }
                        )
                        Text(
                            text = when {
                                totalIncomes == 0.0 -> "Sin ingresos registrados"
                                savingsRate >= 30.0 -> "¡Nivel de ahorro sobresaliente!"
                                savingsRate >= 10.0 -> "Ahorro saludable. Sigue así."
                                savingsRate > 0.0 -> "Ahorro bajo. Necesitas optimizar."
                                else -> "Tus gastos superan tus ingresos."
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    // Circular indicator
                    Box(
                        modifier = Modifier.size(70.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { (savingsRate.coerceIn(0.0, 100.0) / 100.0).toFloat() },
                            modifier = Modifier.size(64.dp),
                            color = when {
                                savingsRate >= 30.0 -> Emerald500
                                savingsRate >= 10.0 -> MaterialTheme.colorScheme.primary
                                else -> Rose500
                            },
                            strokeWidth = 6.dp,
                            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        )
                        Icon(
                            imageVector = when {
                                savingsRate >= 30.0 -> Icons.Default.TrendingUp
                                savingsRate >= 10.0 -> Icons.Default.VerifiedUser
                                else -> Icons.Default.Warning
                            },
                            contentDescription = null,
                            tint = when {
                                savingsRate >= 30.0 -> Emerald500
                                savingsRate >= 10.0 -> MaterialTheme.colorScheme.primary
                                else -> Rose500
                            },
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Dynamic Actionable Tip Cards
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Tip 1: Overall Savings Recommendation
                val tip1 = when {
                    totalIncomes == 0.0 -> AdviceInfo(
                        "Registra tus primeros ingresos",
                        "Para poder ofrecerte un análisis exacto de tus hábitos de ahorro, necesitamos que registres tus fuentes de ingresos fijos o de ventas.",
                        Icons.Default.Payments,
                        MaterialTheme.colorScheme.primary
                    )
                    savingsRate < 10.0 -> AdviceInfo(
                        "Regla de Oro: Págate a ti primero",
                        "Separa al menos el 10% de tus ingresos (PEN ${df.format(totalIncomes * 0.10)}) apenas los recibas. Colócalos en una cuenta separada antes de empezar a gastar para asegurar tu ahorro.",
                        Icons.Default.Savings,
                        Rose500
                    )
                    savingsRate in 10.0..30.0 -> AdviceInfo(
                        "Consolida tu Reserva Financiera",
                        "Tu excedente de ahorro actual es de PEN ${df.format(balance)}. Te recomendamos destinarlo a construir tu Fondo de Emergencia hasta cubrir 3 meses de tus gastos fijos (PEN ${df.format(totalExpenses * 3.0)}) para proteger tu tranquilidad.",
                        Icons.Default.VerifiedUser,
                        MaterialTheme.colorScheme.primary
                    )
                    else -> AdviceInfo(
                        "Haz trabajar tu dinero ahorrado",
                        "Estás ahorrando un excelente ${savingsRate.toInt()}% de tus ingresos (PEN ${df.format(balance)}). Considera colocar una parte de tus ahorros en depósitos a plazo fijo o fondos mutuos de bajo riesgo para ganarle a la inflación.",
                        Icons.Default.TrendingUp,
                        Emerald500
                    )
                }

                AdviceCard(title = tip1.title, desc = tip1.desc, icon = tip1.icon, color = tip1.color)

                // Tip 2: Category Spending Reduction Recommendation
                if (highestExpenseCategory != "Ninguno" && maxExpenseAmount > 0) {
                    val tip2 = when (highestExpenseCategory) {
                        "Comida" -> AdviceInfo(
                            "Optimización en 'Comida' (PEN ${df.format(maxExpenseAmount)})",
                            "Es tu mayor gasto este mes. Para ahorrar, planifica tus comidas semanales (meal prep), haz una lista estricta antes de ir al supermercado y reduce las salidas a restaurantes.",
                            Icons.Default.Restaurant,
                            Amber500
                        )
                        "Ocio" -> AdviceInfo(
                            "Ajuste en Ocio y Entretenimiento (PEN ${df.format(maxExpenseAmount)})",
                            "Has gastado una suma considerable en diversión. Intenta establecer un límite semanal estricto para salidas, y revisa si tienes suscripciones mensuales que no uses.",
                            Icons.Default.LocalPlay,
                            Amber500
                        )
                        "Hogar" -> AdviceInfo(
                            "Control de Consumos en Hogar (PEN ${df.format(maxExpenseAmount)})",
                            "Los egresos del hogar representan una gran parte de tus gastos. Desenchufa electrodomésticos en standby, optimiza el uso de agua/luz y evalúa compras de insumos al por mayor.",
                            Icons.Default.Home,
                            Amber500
                        )
                        "Transporte" -> AdviceInfo(
                            "Eficiencia en Movilidad (PEN ${df.format(maxExpenseAmount)})",
                            "El gasto de traslado es alto. Consolida tus diligencias en un solo viaje, prefiere el transporte público o comparte trayecto con conocidos para ahorrar combustible.",
                            Icons.Default.DirectionsCar,
                            Amber500
                        )
                        else -> AdviceInfo(
                            "Atención al gasto en '$highestExpenseCategory' (PEN ${df.format(maxExpenseAmount)})",
                            "Esta categoría representa tu principal egreso. Revisa detalladamente cada transacción de este rubro e identifica cuáles de ellas puedes suprimir el próximo mes.",
                            Icons.Default.Label,
                            Amber500
                        )
                    }
                    AdviceCard(title = tip2.title, desc = tip2.desc, icon = tip2.icon, color = tip2.color)
                } else {
                    AdviceCard(
                        title = "La Regla de los Gastos Hormiga",
                        desc = "Pequeños consumos diarios de bajo costo (cafés, snacks, taxis cortos) pueden representar silenciosamente hasta el 15% de tus egresos mensuales sin que te des cuenta.",
                        icon = Icons.Default.Label,
                        color = Amber500
                    )
                }

                // Tip 3: Proactive Savings Goal Estimator (Interactivo / Educativo)
                if (balance > 0.0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f), modifier = Modifier.padding(vertical = 4.dp))
                    
                    Text(
                        text = "PROYECCIÓN DE AHORRO ESTIMADA",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    
                    Text(
                        text = "Si mantienes este ritmo de ahorro mensual de PEN ${df.format(balance)}, acumularás:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            Triple("En 3 Meses", balance * 3, MaterialTheme.colorScheme.primaryContainer),
                            Triple("En 6 Meses", balance * 6, MaterialTheme.colorScheme.secondaryContainer),
                            Triple("En 1 Año", balance * 12, MaterialTheme.colorScheme.tertiaryContainer)
                        ).forEach { (time, projection, bgColor) ->
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = bgColor.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(text = time, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "PEN ${df.format(projection).take(8)}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AdviceCard(
    title: String,
    desc: String,
    icon: ImageVector,
    color: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.05f))
            .border(1.dp, color.copy(alpha = 0.12f), RoundedCornerShape(14.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = desc,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

// 10. REAL-TIME US DOLLAR EXCHANGE RATE WIDGET
@Composable
fun ExchangeRateCard(viewModel: AppViewModel) {
    val exchangeRateState by viewModel.exchangeRateState.collectAsStateWithLifecycle()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, shape = RoundedCornerShape(20.dp))
            .testTag("exchange_rate_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachMoney,
                            contentDescription = "USD",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "TIPO DE CAMBIO USD/PEN",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Dólar en Tiempo Real",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                // Refresh Button
                IconButton(
                    onClick = { viewModel.fetchExchangeRate() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Actualizar tipo de cambio",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (val state = exchangeRateState) {
                is ExchangeRateState.Loading -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Consultando mercado cambiario...",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                is ExchangeRateState.Success -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Compra (Buy) Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "COMPRA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format(Locale.getDefault(), "S/ %.3f", state.rateBuy),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        // Venta (Sell) Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "VENTA",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format(Locale.getDefault(), "S/ %.3f", state.rateSell),
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fuente de datos: ER-API (Interbancario)",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                        Text(
                            text = "Act: ${state.lastUpdated}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is ExchangeRateState.Error -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                                RoundedCornerShape(12.dp)
                            )
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.error.copy(alpha = 0.15f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Error de red. Mostrando estimado: S/ 3.750",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        
                        TextButton(
                            onClick = { viewModel.fetchExchangeRate() },
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier.height(24.dp)
                        ) {
                            Text(
                                text = "Reintentar",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

// 11. RECHARTS-INSPIRED INTERACTIVE ANALYTICS & SAVINGS EVOLUTION DASHBOARD
data class SavingsPoint(
    val index: Int,
    val dateLabel: String,
    val dateMillis: Long,
    val balance: Double,
    val totalIncome: Double,
    val totalExpense: Double
)

@Composable
fun RechartsAnalyticsDashboard(
    txs: List<TransactionEntity>,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) } // 0: Evolución, 1: Categorías
    val df = DecimalFormat("#,##0.00")

    Card(
        modifier = modifier
            .fillMaxWidth()
            .shadow(4.dp, shape = RoundedCornerShape(24.dp))
            .testTag("recharts_dashboard_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "ANÁLISIS GRÁFICO AVANZADO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Tendencias y Métricas Recharts",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Timeline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Custom Segmented Control (Pills Layout)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf("Evolución del Ahorro", "Distribución de Gastos").forEachIndexed { idx, label ->
                    val isSelected = selectedTab == idx
                    val bgSelected = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                    val textSelected = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(bgSelected)
                            .clickable { selectedTab = idx }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = textSelected
                        )
                    }
                }
            }

            if (txs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Inserta transacciones para ver gráficos",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                    },
                    label = "TabContent"
                ) { tab ->
                    when (tab) {
                        0 -> SavingsEvolutionChart(txs = txs)
                        1 -> CategoryBarChart(txs = txs)
                    }
                }
            }
        }
    }
}

@Composable
fun SavingsEvolutionChart(txs: List<TransactionEntity>) {
    val df = DecimalFormat("#,##0.00")
    
    // Process savings data points chronologically
    val displayPoints = remember(txs) {
        val sortedTxs = txs.filter { !it.isDeletedLocally }.sortedBy { it.dateMillis }
        var currentBalance = 0.0
        var totalIncome = 0.0
        var totalExpense = 0.0
        val points = mutableListOf<SavingsPoint>()
        val sdf = SimpleDateFormat("dd/MM", Locale.getDefault())

        sortedTxs.forEachIndexed { index, tx ->
            if (tx.type == "INCOME") {
                currentBalance += tx.amount
                totalIncome += tx.amount
            } else {
                currentBalance -= tx.amount
                totalExpense += tx.amount
            }
            points.add(
                SavingsPoint(
                    index = index,
                    dateLabel = sdf.format(Date(tx.dateMillis)),
                    dateMillis = tx.dateMillis,
                    balance = currentBalance,
                    totalIncome = totalIncome,
                    totalExpense = totalExpense
                )
            )
        }

        // Downsample to max 10 points for readable rendering
        val maxPoints = 10
        if (points.size <= maxPoints) {
            points
        } else {
            val step = (points.size - 1).toDouble() / (maxPoints - 1)
            val downsampled = mutableListOf<SavingsPoint>()
            for (i in 0 until maxPoints) {
                val idx = (i * step).toInt().coerceIn(0, points.size - 1)
                downsampled.add(points[idx])
            }
            downsampled
        }
    }

    if (displayPoints.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Inserta ingresos/egresos para graficar la evolución", fontSize = 12.sp)
        }
        return
    }

    var selectedIndex by remember(displayPoints) { mutableStateOf(displayPoints.size - 1) }
    val currentSelectedPoint = displayPoints.getOrNull(selectedIndex) ?: displayPoints.last()

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Quick Tooltip Banner Info (Recharts inspired interactive indicator)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DETALLE DE PUNTO SELECCIONADO (${currentSelectedPoint.dateLabel})",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Balance: S/ ${df.format(currentSelectedPoint.balance)}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Ingresos", fontSize = 9.sp, color = Emerald500, fontWeight = FontWeight.Bold)
                        Text(text = "S/ ${df.format(currentSelectedPoint.totalIncome).take(8)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "Gastos", fontSize = 9.sp, color = Rose500, fontWeight = FontWeight.Bold)
                        Text(text = "S/ ${df.format(currentSelectedPoint.totalExpense).take(8)}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Chart Area Wrapper
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.01f), RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                .padding(bottom = 12.dp)
        ) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat() - 40f // leave space for X labels

            val balances = displayPoints.map { it.balance }
            val maxVal = balances.maxOrNull() ?: 100.0
            val minVal = balances.minOrNull() ?: 0.0
            val pad = ((maxVal - minVal).coerceAtLeast(100.0) * 0.15).toFloat()
            val yMax = (maxVal + pad).toFloat()
            val yMin = (minVal - pad).toFloat()

            // Touch mapping calculations
            val xStep = width / (displayPoints.size - 1).coerceAtLeast(1)

            val primaryColor = MaterialTheme.colorScheme.primary
            val outlineColor = MaterialTheme.colorScheme.outline

            // Interaction detection modifier
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(displayPoints) {
                        detectTapGestures { offset ->
                            val index = kotlin.math.round(offset.x / xStep)
                                .toInt()
                                .coerceIn(0, displayPoints.size - 1)
                            selectedIndex = index
                        }
                    }
                    .pointerInput(displayPoints) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val index = kotlin.math.round(change.position.x / xStep)
                                .toInt()
                                .coerceIn(0, displayPoints.size - 1)
                            selectedIndex = index
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // 1. Cartesian Gridlines (Recharts signature look)
                    val gridLines = 4
                    for (i in 0..gridLines) {
                        val y = (height / gridLines) * i
                        drawLine(
                            color = outlineColor.copy(alpha = 0.08f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // 2. Draw Area Path (gradient)
                    if (displayPoints.size > 1) {
                        val areaPath = Path().apply {
                            moveTo(0f, height)
                            displayPoints.forEachIndexed { idx, pt ->
                                val x = idx * xStep
                                val y = height - ((pt.balance - yMin) / (yMax - yMin) * height)
                                lineTo(x, y.toFloat())
                            }
                            lineTo(width, height)
                            close()
                        }

                        drawPath(
                            path = areaPath,
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    primaryColor.copy(alpha = 0.35f),
                                    primaryColor.copy(alpha = 0.0f)
                                ),
                                startY = 0f,
                                endY = height
                            )
                        )

                        // 3. Draw Stroke Path
                        val strokePath = Path().apply {
                            displayPoints.forEachIndexed { idx, pt ->
                                val x = idx * xStep
                                val y = height - ((pt.balance - yMin) / (yMax - yMin) * height)
                                if (idx == 0) moveTo(x, y.toFloat()) else lineTo(x, y.toFloat())
                            }
                        }

                        drawPath(
                            path = strokePath,
                            color = primaryColor,
                            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    // 4. Draw Selected Point Indicator (Vertical line & dot)
                    val selectX = selectedIndex * xStep
                    val selectY = height - ((currentSelectedPoint.balance - yMin) / (yMax - yMin) * height).toFloat()

                    drawLine(
                        color = primaryColor.copy(alpha = 0.4f),
                        start = Offset(selectX, 0f),
                        end = Offset(selectX, height),
                        strokeWidth = 1.5.dp.toPx()
                    )

                    // Glow background circle
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.2f),
                        radius = 12.dp.toPx(),
                        center = Offset(selectX, selectY)
                    )

                    // Core point circle
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = Offset(selectX, selectY)
                    )
                    drawCircle(
                        color = primaryColor,
                        radius = 4.dp.toPx(),
                        center = Offset(selectX, selectY)
                    )
                }
            }
        }

        // X Axis labels (Dates representation)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            displayPoints.forEachIndexed { idx, pt ->
                val isSelected = idx == selectedIndex
                Text(
                    text = pt.dateLabel,
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            }
        }

        // Touch suggestion tooltip
        Text(
            text = "💡 Desliza tu dedo o presiona sobre el gráfico para auditar el historial de tu ahorro.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
    }
}

@Composable
fun CategoryBarChart(txs: List<TransactionEntity>) {
    val df = DecimalFormat("#,##0.00")
    val expenseTxs = remember(txs) { txs.filter { it.type == "EXPENSE" && !it.isDeletedLocally } }

    val categorySummary = remember(expenseTxs) {
        val totalSpending = expenseTxs.sumOf { it.amount }
        expenseTxs.groupBy { it.category }
            .mapValues { entry ->
                val sum = entry.value.sumOf { it.amount }
                val pct = if (totalSpending > 0) (sum / totalSpending) * 100.0 else 0.0
                Pair(sum, pct)
            }
            .toList()
            .sortedByDescending { it.second.first }
    }

    if (categorySummary.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Registra egresos por categorías para visualizar la distribución.", fontSize = 12.sp)
        }
        return
    }

    var selectedIndex by remember(categorySummary) { mutableStateOf(0) }
    val selectedItem = categorySummary.getOrNull(selectedIndex) ?: categorySummary.first()

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        // Detailed indicator
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(getCategoryColor(selectedItem.first))
                    )
                    Column {
                        Text(
                            text = "CATEGORÍA DEL GASTO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = selectedItem.first.uppercase(),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Total: S/ ${df.format(selectedItem.second.first)}",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = String.format(Locale.getDefault(), "Porcentaje: %.1f%%", selectedItem.second.second),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // Recharts-inspired Custom Vertical Column bar graphics
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.01f), RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            val width = constraints.maxWidth.toFloat()
            val height = constraints.maxHeight.toFloat()

            val maxSum = categorySummary.maxOfOrNull { it.second.first } ?: 100.0
            val barCount = categorySummary.size
            val barSectionWidth = width / barCount
            val barWidth = (barSectionWidth * 0.45f).coerceAtLeast(15f)

            val onSurfaceColor = MaterialTheme.colorScheme.onSurface

            var isAnimated by remember { mutableStateOf(false) }
            LaunchedEffect(key1 = categorySummary) {
                delay(100)
                isAnimated = true
            }

            val progressAnimate by animateFloatAsState(
                targetValue = if (isAnimated) 1f else 0f,
                animationSpec = tween(durationMillis = 850)
            )

            // Canvas + Gesture Detector
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(categorySummary) {
                        detectTapGestures { offset ->
                            val index = (offset.x / barSectionWidth)
                                .toInt()
                                .coerceIn(0, barCount - 1)
                            selectedIndex = index
                        }
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Background grid line (Recharts visual signature)
                    drawLine(
                        color = Color.LightGray.copy(alpha = 0.15f),
                        start = Offset(0f, height * 0.5f),
                        end = Offset(width, height * 0.5f),
                        strokeWidth = 1.dp.toPx()
                    )

                    categorySummary.forEachIndexed { idx, (cat, pair) ->
                        val sum = pair.first
                        val barHeight = ((sum / maxSum) * (height - 30f)).toFloat() * progressAnimate
                        val leftX = (idx * barSectionWidth) + (barSectionWidth - barWidth) / 2f
                        val topY = height - barHeight - 15f

                        // Draw selection highlights
                        if (idx == selectedIndex) {
                            drawRoundRect(
                                color = onSurfaceColor.copy(alpha = 0.04f),
                                topLeft = Offset(idx * barSectionWidth, 0f),
                                size = Size(barSectionWidth, height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                            )
                        }

                        // Main bar column
                        drawRoundRect(
                            color = getCategoryColor(cat),
                            topLeft = Offset(leftX, topY),
                            size = Size(barWidth, barHeight.coerceAtLeast(4f)),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx())
                        )

                        // Base guideline
                        drawLine(
                            color = onSurfaceColor.copy(alpha = 0.15f),
                            start = Offset(0f, height - 15f),
                            end = Offset(width, height - 15f),
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }
                }
            }
        }

        // Text Labels below bars
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            categorySummary.forEachIndexed { idx, (cat, _) ->
                val isSelected = idx == selectedIndex
                Text(
                    text = cat.take(4).uppercase(),
                    fontSize = 10.sp,
                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                    color = if (isSelected) getCategoryColor(cat) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.width(36.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}



