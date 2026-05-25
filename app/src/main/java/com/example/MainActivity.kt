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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
