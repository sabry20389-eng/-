package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.database.AppDatabase
import com.example.data.model.LimitSettings
import com.example.data.model.Transaction
import com.example.data.model.Wallet
import com.example.data.repository.LimitRepository
import com.example.ui.theme.*
import com.example.viewmodel.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Initialize Room Database and Repository
        val database = AppDatabase.getDatabase(this)
        val repository = LimitRepository(database.transactionDao(), database.limitSettingsDao(), database.walletDao())
        val factory = LimitGuardViewModelFactory(repository)

        setContent {
            MyApplicationTheme(darkTheme = true) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    LimitGuardDashboard(
                        factory = factory,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LimitGuardDashboard(
    factory: LimitGuardViewModelFactory,
    modifier: Modifier = Modifier,
    viewModel: LimitGuardViewModel = viewModel(factory = factory)
) {
    val context = LocalContext.current
    var showWelcomeDialog by remember { mutableStateOf(false) }

    var isOutdoorMode by remember { mutableStateOf(false) }
    var activeThemeId by remember { mutableStateOf(0) }
    var showThemeSelectorDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val savedThemeId = prefs.getInt("selected_theme_id", 0)
        activeThemeId = savedThemeId
        isOutdoorMode = prefs.getBoolean("is_outdoor_mode", false)

        val isFirstLaunch = prefs.getBoolean("is_first_launch_v2", true)
        if (isFirstLaunch) {
            showWelcomeDialog = true
            prefs.edit().putBoolean("is_first_launch_v2", false).apply()
        }
    }

    val selectTheme: (Int) -> Unit = remember {
        { id ->
            activeThemeId = id
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putInt("selected_theme_id", id).apply()
        }
    }

    val toggleOutdoorMode: (Boolean) -> Unit = remember {
        { outdoor ->
            isOutdoorMode = outdoor
            context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE).edit().putBoolean("is_outdoor_mode", outdoor).apply()
        }
    }

    if (showWelcomeDialog) {
        WelcomeDialog(onDismiss = { showWelcomeDialog = false })
    }

    val transactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val wallets by viewModel.allWallets.collectAsStateWithLifecycle()
    val activeWallet by viewModel.activeWallet.collectAsStateWithLifecycle()
    val spentToday by viewModel.spentToday.collectAsStateWithLifecycle()
    val spentThisMonth by viewModel.spentThisMonth.collectAsStateWithLifecycle()
    val liveWarning by viewModel.liveWarning.collectAsStateWithLifecycle()
    
    // NEW balance flow observers
    val walletBalances by viewModel.walletBalances.collectAsStateWithLifecycle()
    val totalBalance by viewModel.totalBalance.collectAsStateWithLifecycle()
    val balancesAfter by viewModel.balancesAfterTransactions.collectAsStateWithLifecycle()
    val depositedToday by viewModel.depositedToday.collectAsStateWithLifecycle()
    val depositedThisMonth by viewModel.depositedThisMonth.collectAsStateWithLifecycle()
    val carriedOverTransferLimit by viewModel.carriedOverTransferLimit.collectAsStateWithLifecycle()
    val carriedOverDepositLimit by viewModel.carriedOverDepositLimit.collectAsStateWithLifecycle()

    LaunchedEffect(activeThemeId, isOutdoorMode, activeWallet) {
        applyThemeById(
            activeThemeId, 
            isDark = !isOutdoorMode, 
            activeWalletLabel = activeWallet?.label, 
            activeWalletNumber = activeWallet?.phoneNumber
        )
    }

    LaunchedEffect(transactions, settings, wallets) {
        if (com.example.util.GoogleDriveBackupHelper.isBackupEnabled(context)) {
            try {
                val jsonBackup = viewModel.exportBackupToJSON()
                com.example.util.GoogleDriveBackupHelper.performSilentCloudBackup(context, jsonBackup)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    var filterByActiveWalletOnly by remember { mutableStateOf(true) }
    val currentWallet = activeWallet

    val currencyFormatter = remember { DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)) }

    val scope = rememberCoroutineScope()
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonString = viewModel.exportBackupToJSON()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray(Charsets.UTF_8))
                    }
                    Toast.makeText(context, "تم حفظ النسخة الاحتياطية بنجاح! ✅", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "حدث خطأ أثناء حفظ النسخة: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val jsonString = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    }
                    if (jsonString != null) {
                        val success = viewModel.importBackupFromJSON(jsonString)
                        if (success) {
                            Toast.makeText(context, "تم استعادة البيانات بنجاح! 🔄✅", Toast.LENGTH_LONG).show()
                            viewModel.isSettingsOpen = false
                        } else {
                            Toast.makeText(context, "فشل استعادة البيانات. تأكد من صحة ملف النسخة.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "خطأ في قراءة ملف النسخة: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Start with default dialog closed, handle dialog inside Composable
    if (viewModel.isSettingsOpen) {
        LimitSettingsDialog(
            currentSettings = settings,
            activeWallet = activeWallet,
            canDelete = wallets.size > 1,
            onDismiss = { viewModel.isSettingsOpen = false },
            onDeleteWallet = {
                activeWallet?.let { wallet ->
                    viewModel.deleteWallet(wallet)
                    Toast.makeText(context, "تم حذف الرقم: ${wallet.label}", Toast.LENGTH_SHORT).show()
                }
                viewModel.isSettingsOpen = false
            },
            onSave = { daily, monthly, deposit, monthlyDeposit, initialBalance, warningPercent ->
                viewModel.dailyLimitInput = daily.toString()
                viewModel.monthlyLimitInput = monthly.toString()
                viewModel.dailyDepositLimitInput = deposit.toString()
                viewModel.monthlyDepositLimitInput = monthlyDeposit.toString()
                viewModel.walletInitialBalanceInput = initialBalance.toString()
                viewModel.warningPercentageInput = warningPercent
                viewModel.saveSettings()
                Toast.makeText(context, "تم حفظ الإعدادات بنجاح", Toast.LENGTH_SHORT).show()
                viewModel.isSettingsOpen = false
            },
            onExportBackup = {
                val defaultFileName = "LimitGuard_Backup_${System.currentTimeMillis() / 1000}.json"
                createDocumentLauncher.launch(defaultFileName)
            },
            onImportBackup = {
                openDocumentLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
            },
            onCloudBackupTrigger = {
                viewModel.exportBackupToJSON()
            },
            onCloudRestoreTrigger = { jsonString ->
                viewModel.importBackupFromJSON(jsonString)
            }
        )
    }

    if (viewModel.isAddWalletDialogOpen) {
        AddWalletDialog(
            phoneNumber = viewModel.walletNumberInput,
            label = viewModel.walletLabelInput,
            dailyLimit = viewModel.walletDailyLimitInput,
            monthlyLimit = viewModel.walletMonthlyLimitInput,
            dailyDepositLimit = viewModel.walletDailyDepositLimitInput,
            monthlyDepositLimit = viewModel.walletMonthlyDepositLimitInput,
            initialBalance = viewModel.walletInitialBalanceInput,
            onPhoneChange = { viewModel.onWalletNumberChange(it) },
            onLabelChange = { viewModel.onWalletLabelChange(it) },
            onDailyLimitChange = { viewModel.onWalletDailyLimitChange(it) },
            onMonthlyLimitChange = { viewModel.onWalletMonthlyLimitChange(it) },
            onDailyDepositLimitChange = { viewModel.onWalletDailyDepositLimitChange(it) },
            onMonthlyDepositLimitChange = { viewModel.onWalletMonthlyDepositLimitChange(it) },
            onInitialBalanceChange = { viewModel.onWalletInitialBalanceChange(it) },
            onDismiss = { viewModel.isAddWalletDialogOpen = false },
            onSave = {
                viewModel.addNewWallet()
                Toast.makeText(context, "تم إضافة رقم المحفظة بنجاح", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (viewModel.showSecuritySettingsDialog) {
        SecuritySettingsDialog(
            settings = settings,
            showDialog = viewModel.showSecuritySettingsDialog,
            onDismiss = { viewModel.showSecuritySettingsDialog = false },
            onToggleRoot = { viewModel.setPreventRooted(it) },
            onToggleDebugger = { viewModel.setPreventDebugger(it) },
            onSetupPinClick = {
                viewModel.showPinSetupDialog = true
                viewModel.firstPinInput = ""
                viewModel.secondPinInput = ""
            },
            context = context
        )
    }

    if (viewModel.showPinSetupDialog) {
        PinSetupDialog(
            showDialog = viewModel.showPinSetupDialog,
            isAlreadyEnabled = settings.isPinLockEnabled,
            firstPin = viewModel.firstPinInput,
            secondPin = viewModel.secondPinInput,
            onFirstPinChange = { viewModel.firstPinInput = it },
            onSecondPinChange = { viewModel.secondPinInput = it },
            onDismiss = { viewModel.showPinSetupDialog = false },
            onEnable = { viewModel.setupPinAndEnable(it) },
            onDisable = { viewModel.disablePinLock() }
        )
    }

    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }

    if (transactionToDelete != null) {
        val tx = transactionToDelete!!
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = {
                Text(
                    text = "تأكيد حذف العملية",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من حذف هذه المعاملة بقيمة ${currencyFormatter.format(tx.amount)} ج.م؟ سيتم إعادة حساب استهلاكك ورصيد المحفظة تلقائياً بناءً على ذلك.",
                    color = MutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransaction(tx)
                        transactionToDelete = null
                        Toast.makeText(context, "تم حذف المعاملة بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExceededRed)
                ) {
                    Text("حذف", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { transactionToDelete = null },
                    border = BorderStroke(1.dp, CharcoalBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("إلغاء", color = Color.White)
                }
            },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("delete_tx_confirm_dialog")
        )
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "تأكيد مسح السجل",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من مسح جميع عمليات السجل؟ لا يمكن التراجع عن هذا الإجراء وسيتم تصفير جميع الاستهلاكات الحاليّة مع الحفاظ على أرصدتك للتخطيط الجديد.",
                    color = MutedText,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearAll()
                        Toast.makeText(context, "تم مسح سجل المعاملات", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ExceededRed)
                ) {
                    Text("مسح السجل", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showClearConfirmDialog = false },
                    border = BorderStroke(1.dp, CharcoalBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("إلغاء", color = Color.White)
                }
            },
            containerColor = CharcoalSurface,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.testTag("clear_all_confirm_dialog")
        )
    }

    if (showThemeSelectorDialog) {
        ThemeSelectorDialog(
            activeThemeId = activeThemeId,
            onThemeSelect = { id ->
                selectTheme(id)
            },
            onDismiss = { showThemeSelectorDialog = false }
        )
    }

    var isRooted by remember { mutableStateOf(false) }
    var isDebugger by remember { mutableStateOf(false) }
    
    LaunchedEffect(settings.preventRooted, settings.preventDebugger) {
        withContext(Dispatchers.IO) {
            isRooted = com.example.util.SecurityUtils.isDeviceRooted()
            isDebugger = com.example.util.SecurityUtils.isDebuggerAttached()
        }
    }
    
    val isBlocked = (settings.preventRooted && isRooted) || (settings.preventDebugger && isDebugger)

    if (viewModel.showPinUnlockScreen || isBlocked) {
        PinUnlockScreen(
            viewModel = viewModel,
            settings = settings,
            context = context
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(CharcoalBg, CharcoalSurface)
                    )
                )
        ) {
            // 1. Sleek Custom Header
            HeaderView(
                onSettingsClick = { viewModel.openSettings() },
                onAddWalletClick = { viewModel.openAddWalletDialog() },
                onClearAll = { showClearConfirmDialog = true },
                onSecurityClick = { viewModel.showSecuritySettingsDialog = true },
                isPinEnabled = settings.isPinLockEnabled,
                onLockClick = { viewModel.lockApp() },
                onThemeClick = { showThemeSelectorDialog = true },
                isOutdoorMode = isOutdoorMode,
                onOutdoorModeToggle = toggleOutdoorMode
            )

        // Calculate filtered transactions for both list and export features
        val query = viewModel.searchQuery.trim().lowercase()
        val filteredTransactions = transactions.filter {
            val matchesWallet = !filterByActiveWalletOnly || currentWallet == null || it.senderWalletNumber == currentWallet.phoneNumber
            
            val matchesPeriod = when (viewModel.filterPeriod) {
                LimitGuardViewModel.FilterPeriod.ALL -> true
                LimitGuardViewModel.FilterPeriod.TODAY -> {
                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    it.timestamp >= calendar.timeInMillis
                }
                LimitGuardViewModel.FilterPeriod.THIS_MONTH -> {
                    val calendar = Calendar.getInstance()
                    calendar.set(Calendar.DAY_OF_MONTH, 1)
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    it.timestamp >= calendar.timeInMillis
                }
            }

            val walletLabel = wallets.find { w -> w.phoneNumber == it.senderWalletNumber }?.label?.lowercase() ?: ""
            val matchesSearch = if (query.isEmpty()) {
                true
            } else {
                it.recipientNumber.lowercase().contains(query) ||
                it.notes.lowercase().contains(query) ||
                it.amount.toString().contains(query) ||
                it.senderWalletNumber.lowercase().contains(query) ||
                walletLabel.contains(query) ||
                (if (it.isDeposit) "إيداع شحن" else "تحويل سحب دفعة").lowercase().contains(query)
            }

            matchesWallet && matchesPeriod && matchesSearch
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // NEW: Total Balance Indicator + StatusAlertBanner across all wallets
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.5.dp, VodafoneRed.copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = VodafoneRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "إجمالي كافة الأرقام",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MutedText,
                                    textAlign = TextAlign.Center
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${currencyFormatter.format(totalBalance)} ج.م",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = DynamicWhite,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    StatusAlertBanner(
                        spentToday = spentToday,
                        spentThisMonth = spentThisMonth,
                        settings = settings,
                        warningPercentage = settings.warningPercentage,
                        activeWallet = activeWallet,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }

            // 1.5 Active Wallet Selection & Addition
            item {
                WalletBar(
                    wallets = wallets,
                    activeWallet = activeWallet,
                    walletBalances = walletBalances,
                    currencyFormatter = currencyFormatter,
                    transactions = transactions,
                    onSelectWallet = { viewModel.selectWallet(it.phoneNumber) },
                    onAddWalletClick = { viewModel.openAddWalletDialog() },
                    onDeleteWallet = {
                        viewModel.deleteWallet(it)
                        Toast.makeText(context, "تم حذف الرقم: ${it.label}", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.swipeToSwitchWallet(wallets, activeWallet) { viewModel.selectWallet(it.phoneNumber) }
                )
            }



            // 3. Status limit metrics cards (Today vs Month)
            item {
                val dailyLimit = activeWallet?.dailyLimit ?: settings.dailyLimit
                val monthlyLimit = activeWallet?.monthlyLimit ?: settings.monthlyLimit
                val dailyDepositLimit = activeWallet?.dailyDepositLimit ?: settings.dailyDepositLimit
                val monthlyDepositLimit = activeWallet?.monthlyDepositLimit ?: settings.monthlyDepositLimit

                val dailyOutProgress = if (dailyLimit > 0) (spentToday / dailyLimit).coerceIn(0.0, 1.0).toFloat() else 0f
                val dailyInProgress = if (dailyDepositLimit > 0) (depositedToday / dailyDepositLimit).coerceIn(0.0, 1.0).toFloat() else 0f
                val monthlyOutProgress = if (monthlyLimit > 0) (spentThisMonth / monthlyLimit).coerceIn(0.0, 1.0).toFloat() else 0f
                val monthlyInProgress = if (monthlyDepositLimit > 0) (depositedThisMonth / monthlyDepositLimit).coerceIn(0.0, 1.0).toFloat() else 0f

                val transferThresholdDaily = dailyLimit * settings.warningPercentage
                val depositThresholdDaily = dailyDepositLimit * settings.warningPercentage
                val transferThresholdMonthly = monthlyLimit * settings.warningPercentage
                val depositThresholdMonthly = monthlyDepositLimit * settings.warningPercentage

                val dailyOutColor = when {
                    spentToday >= dailyLimit -> ExceededRed
                    spentToday >= transferThresholdDaily -> WarningOrange
                    else -> SafeGreen
                }
                val dailyInColor = when {
                    depositedToday >= dailyDepositLimit -> ExceededRed
                    depositedToday >= depositThresholdDaily -> WarningOrange
                    else -> SafeGreen
                }
                val monthlyOutColor = when {
                    spentThisMonth >= monthlyLimit -> ExceededRed
                    spentThisMonth >= transferThresholdMonthly -> WarningOrange
                    else -> SafeGreen
                }
                val monthlyInColor = when {
                    depositedThisMonth >= monthlyDepositLimit -> ExceededRed
                    depositedThisMonth >= depositThresholdMonthly -> WarningOrange
                    else -> SafeGreen
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .swipeToSwitchWallet(wallets, activeWallet) { viewModel.selectWallet(it.phoneNumber) },
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Title section for Visual Progress Donuts
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "لوحة المؤشرات البصرية الدائرية 📊",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = VodafoneLightRed
                        )
                        Text(
                            text = "◀️ اسحب لتغيير المحفظة",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }

                    // Dual progress donuts side-by-side
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        NestedCircularProgressDonut(
                            outboundProgress = dailyOutProgress,
                            inboundProgress = dailyInProgress,
                            outboundColor = dailyOutColor,
                            inboundColor = dailyInColor,
                            title = "حدود اليوم",
                            outboundSpent = currencyFormatter.format(spentToday),
                            outboundLimit = currencyFormatter.format(dailyLimit),
                            inboundCollected = currencyFormatter.format(depositedToday),
                            inboundLimit = currencyFormatter.format(dailyDepositLimit),
                            leftOutbound = (dailyLimit - spentToday).coerceAtLeast(0.0),
                            leftInbound = (dailyDepositLimit - depositedToday).coerceAtLeast(0.0),
                            isMonthly = false,
                            formatter = currencyFormatter,
                            modifier = Modifier.weight(1f)
                        )

                        NestedCircularProgressDonut(
                            outboundProgress = monthlyOutProgress,
                            inboundProgress = monthlyInProgress,
                            outboundColor = monthlyOutColor,
                            inboundColor = monthlyInColor,
                            title = "حدود الشهر",
                            outboundSpent = currencyFormatter.format(spentThisMonth),
                            outboundLimit = currencyFormatter.format(monthlyLimit),
                            inboundCollected = currencyFormatter.format(depositedThisMonth),
                            inboundLimit = currencyFormatter.format(monthlyDepositLimit),
                            leftOutbound = (monthlyLimit - spentThisMonth).coerceAtLeast(0.0),
                            leftInbound = (monthlyDepositLimit - depositedThisMonth).coerceAtLeast(0.0),
                            isMonthly = true,
                            formatter = currencyFormatter,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Input Form & Live Warning Generator
            item {
                TransferForm(
                    phoneNumber = viewModel.recipientNumber,
                    amount = viewModel.amountText,
                    notes = viewModel.transactionNotes,
                    timestamp = viewModel.transactionTimestamp,
                    liveWarning = liveWarning,
                    isDeposit = viewModel.isDepositInput,
                    onDepositChange = { viewModel.setDepositMode(it) },
                    onPhoneChange = { viewModel.onPhoneNumberChange(it) },
                    onAmountChange = { viewModel.onAmountChange(it) },
                    onNotesChange = { viewModel.onNotesChange(it) },
                    onTimestampChange = { viewModel.onTimestampChange(it) },
                    onAddTransaction = {
                        viewModel.addTransaction()
                        Toast.makeText(context, "تم تسجيل العملية بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    onDialUssd = {
                        val ussd = viewModel.getUssdCode()
                        // Copy to clipboard
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("USSD Ccode", ussd)
                        clipboard.setPrimaryClip(clip)

                        Toast.makeText(context, "تم نسخ الكود: $ussd وبدء الاتصال", Toast.LENGTH_LONG).show()

                        // Open dialer intent (we encode the '#' properly to avoid system truncating it)
                        val intent = Intent(Intent.ACTION_DIAL).apply {
                            data = Uri.parse("tel:" + Uri.encode(ussd))
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "لم يتم العثور على تطبيق اتصال، الكود منسوخ بالفعل للحافظة!", Toast.LENGTH_LONG).show()
                        }
                    },
                    formatter = currencyFormatter
                )
            }

            // 4.5. Full Search Bar
            item {
                OutlinedTextField(
                    value = viewModel.searchQuery,
                    onValueChange = { viewModel.searchQuery = it },
                    label = { Text("بحث كامل في التطبيق") },
                    placeholder = { Text("مثال: بلال، دفع الإيجار، 010... إلخ") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث", tint = VodafoneRed) },
                    trailingIcon = {
                        if (viewModel.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "إلغاء البحث", tint = Color.LightGray)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("app_search_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VodafoneRed,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedLabelColor = VodafoneRed,
                        unfocusedLabelColor = MutedText,
                        focusedTextColor = DynamicWhite,
                        unfocusedTextColor = DynamicWhite,
                        cursorColor = VodafoneRed
                    )
                )
            }

            // 5. Recent transactions section title
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = if (viewModel.searchQuery.isNotEmpty()) "نتائج البحث عن: \"${viewModel.searchQuery}\"" else if (filterByActiveWalletOnly && currentWallet != null) "عمليات ${currentWallet.label}" else "سجل العمليات الأخير",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DynamicWhite
                            )
                            if (filterByActiveWalletOnly && currentWallet != null) {
                                Text(
                                    text = "رقم: ${currentWallet.phoneNumber}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VodafoneLightRed,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Filter Period Buttons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterButton(
                                text = "الكل",
                                active = viewModel.filterPeriod == LimitGuardViewModel.FilterPeriod.ALL,
                                onClick = { viewModel.filterPeriod = LimitGuardViewModel.FilterPeriod.ALL }
                            )
                            FilterButton(
                                text = "اليوم",
                                active = viewModel.filterPeriod == LimitGuardViewModel.FilterPeriod.TODAY,
                                onClick = { viewModel.filterPeriod = LimitGuardViewModel.FilterPeriod.TODAY }
                            )
                        }
                    }

                    // Source Wallet Filter Chips Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تصفية حسب:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FilterButton(
                                text = "المحفظة الحالية فقط",
                                active = filterByActiveWalletOnly,
                                onClick = { filterByActiveWalletOnly = true }
                            )
                            FilterButton(
                                text = "جميع المحافظ 🛡️",
                                active = !filterByActiveWalletOnly,
                                onClick = { filterByActiveWalletOnly = false }
                            )
                        }
                    }

                    // Export Report panel
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تصدير هذه الحركات:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Excel Export
                            OutlinedButton(
                                onClick = {
                                    val rangeText = when (viewModel.filterPeriod) {
                                        LimitGuardViewModel.FilterPeriod.ALL -> "جميع الأوقات"
                                        LimitGuardViewModel.FilterPeriod.TODAY -> "اليوم فقط"
                                        LimitGuardViewModel.FilterPeriod.THIS_MONTH -> "الشهر الحالي"
                                    }
                                    com.example.util.ExportUtils.shareCSV(context, filteredTransactions, wallets, rangeText)
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = SafeGreen),
                                contentPadding = PaddingValues(vertical = 2.dp, horizontal = 8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تصدير Excel", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = SafeGreen)
                            }

                            // PDF Export
                            OutlinedButton(
                                onClick = {
                                    val rangeText = when (viewModel.filterPeriod) {
                                        LimitGuardViewModel.FilterPeriod.ALL -> "جميع الأوقات"
                                        LimitGuardViewModel.FilterPeriod.TODAY -> "اليوم فقط"
                                        LimitGuardViewModel.FilterPeriod.THIS_MONTH -> "الشهر الحالي"
                                    }
                                    com.example.util.ExportUtils.sharePDF(context, filteredTransactions, wallets, rangeText)
                                },
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, VodafoneRed.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = VodafoneRed),
                                contentPadding = PaddingValues(vertical = 2.dp, horizontal = 8.dp),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(10.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("تحميل PDF", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = VodafoneRed)
                            }
                        }
                    }
                }
            }

            if (filteredTransactions.isEmpty()) {
                item {
                    EmptyHistoryView(isSearching = viewModel.searchQuery.isNotEmpty())
                }
            } else {
                items(filteredTransactions, key = { it.id }) { tx ->
                    val balAfter = balancesAfter[tx.id]
                    TransactionItemRow(
                        transaction = tx,
                        balanceAfter = balAfter,
                        onDelete = { transactionToDelete = tx },
                        formatter = currencyFormatter
                    )
                }
            }

            // Developer copyright info at the bottom of the list
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "تطوير وتنفيذ مهندس صبري السيد",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MutedText.copy(alpha = 0.8f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "01020303230",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = VodafoneLightRed.copy(alpha = 0.8f),
                        letterSpacing = 1.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
    }
}

@Composable
fun HeaderView(
    onSettingsClick: () -> Unit,
    onAddWalletClick: () -> Unit,
    onClearAll: () -> Unit,
    onSecurityClick: () -> Unit = {},
    isPinEnabled: Boolean = false,
    onLockClick: () -> Unit = {},
    onThemeClick: () -> Unit = {},
    isOutdoorMode: Boolean = false,
    onOutdoorModeToggle: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(VodafoneRed)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "كاشاتى",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = VodafoneLightRed
                )
            }
            Text(
                text = "حارس حد التحويلات اليومي والشهري",
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // New Add Wallet button next to Settings
            IconButton(
                onClick = onAddWalletClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("add_wallet_hdr_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "إضافة رقم",
                    tint = SafeGreen
                )
            }

            // Lock Button (only if PIN is enabled)
            if (isPinEnabled) {
                IconButton(
                    onClick = onLockClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("lock_app_hdr_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "قفل التطبيق فورا",
                        tint = VodafoneLightRed
                    )
                }
            }

            // Security Audit button
            IconButton(
                onClick = onSecurityClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("security_hdr_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "فحص الحماية والأمان",
                    tint = SafeGreen
                )
            }

            IconButton(
                onClick = onThemeClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("theme_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "اختيار المظهر والألوان",
                    tint = Color.White
                )
            }

            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("settings_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "الإعدادات",
                    tint = Color.White
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("menu_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "قائمة إضافية",
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(CharcoalSurface)
                ) {
                    DropdownMenuItem(
                        text = { Text("درع الأمان وحماية البيئة", color = Color.White) },
                        onClick = {
                            showMenu = false
                            onSecurityClick()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = SafeGreen
                            )
                        }
                    )
                    if (isPinEnabled) {
                        DropdownMenuItem(
                            text = { Text("قفل التطبيق الآن", color = Color.White) },
                            onClick = {
                                showMenu = false
                                onLockClick()
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = VodafoneLightRed
                                )
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("وضع الشمس الخارجي ☀️", color = Color.White)
                                Switch(
                                    checked = isOutdoorMode,
                                    onCheckedChange = { 
                                        showMenu = false
                                        onOutdoorModeToggle(it) 
                                    },
                                    colors = androidx.compose.material3.SwitchDefaults.colors(
                                        checkedThumbColor = SafeGreen,
                                        checkedTrackColor = SafeGreen.copy(alpha = 0.5f)
                                    )
                                )
                            }
                        },
                        onClick = {
                            showMenu = false
                            onOutdoorModeToggle(!isOutdoorMode)
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.WbSunny,
                                contentDescription = null,
                                tint = WarningOrange
                            )
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("مسح جميع العمليات", color = ExceededRed) },
                        onClick = {
                            showMenu = false
                            onClearAll()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = ExceededRed
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SmartAnalyticsDashboard(
    transactions: List<Transaction>,
    spentToday: Double,
    spentThisMonth: Double,
    activeWallet: Wallet?,
    settings: LimitSettings,
    formatter: DecimalFormat,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Calculate details for active wallet
    val walletNumber = activeWallet?.phoneNumber ?: ""
    val walletTxs = remember(transactions, walletNumber) {
        if (walletNumber.isEmpty()) transactions else transactions.filter { it.senderWalletNumber == walletNumber }
    }

    // AI Prediction engine: Calculates average daily spend and predicts days until limit is exceeded
    val aiBriefText = remember(walletTxs, activeWallet, settings) {
        val dailyLimit = activeWallet?.dailyLimit ?: settings.dailyLimit
        val monthlyLimit = activeWallet?.monthlyLimit ?: settings.monthlyLimit
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_MONTH).coerceIn(1, 30)
        
        val totalSpentThisMonth = walletTxs.filter { !it.isDeposit && it.timestamp >= getStartOfMonth() }.sumOf { it.amount }
        val avgDailySpend = totalSpentThisMonth / currentDay
        
        val remainingMonthlyLimit = (monthlyLimit - totalSpentThisMonth).coerceAtLeast(0.0)

        if (avgDailySpend <= 0) {
            "لم يتم رصد أي عمليات سحب هذا الشهر بعد. حدودك المالية في أمان تام بنسبة 100%!"
        } else {
            val daysLeftValue = remainingMonthlyLimit / avgDailySpend
            val formattedAvg = formatter.format(avgDailySpend)
            if (daysLeftValue <= 5) {
                "⚠️ تنبيه الاستهلاك الذكي: بناءً على معدل إنفاقك الحالي ($formattedAvg ج.م/يومياً)، يرجى الحذر! قد تتجاوز حدك الشهري للهذه المحفظة خلال ${daysLeftValue.toInt()} أيام!"
            } else if (daysLeftValue <= 10) {
                "⚠️ تنبيه الاستهلاك الذكي: معدل إنفاقك ($formattedAvg ج.م/يومياً) يقترب تدريجياً من حافّة الحد الأقصى. قد تستهلك كامل السعة الشهرية خلال ${daysLeftValue.toInt()} أيام."
            } else {
                "✅ تنبيه الاستهلاك الذكي: معدل إنفاقك اليومي متزن ($formattedAvg ج.م/يومياً). حدودك الشهرية في نطاق الأمان التام وتكفيك لأكثر من ${daysLeftValue.toInt()} يوماً."
            }
        }
    }

    // Pie chart / progress math
    val dailyLimit = activeWallet?.dailyLimit ?: settings.dailyLimit
    val monthlyLimit = activeWallet?.monthlyLimit ?: settings.monthlyLimit
    
    val totalTransfersMonth = remember(walletTxs) {
        walletTxs.filter { !it.isDeposit && it.timestamp >= getStartOfMonth() }.sumOf { it.amount }
    }
    val totalDepositsMonth = remember(walletTxs) {
        walletTxs.filter { it.isDeposit && it.timestamp >= getStartOfMonth() }.sumOf { it.amount }
    }

    val spendProgress = remember(totalTransfersMonth, monthlyLimit) {
        if (monthlyLimit <= 0) 0f else (totalTransfersMonth / monthlyLimit).toFloat().coerceIn(0f, 1f)
    }

    val animatedSpendProgress by animateFloatAsState(
        targetValue = spendProgress,
        label = "spendProgress"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
        border = BorderStroke(1.dp, CharcoalBorder),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = VodafoneRed,
                        modifier = Modifier.size(20.dp)
                    )
                    Column {
                        Text(
                            text = "تحليلات الذكاء المالي والرسوم البيانية",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = DynamicWhite
                        )
                        Text(
                            text = if (isExpanded) "انقر للإغلاق وتوفير المساحة" else "انقر لعرض المخططات البيانية ولغة الأرقام",
                            style = MaterialTheme.typography.labelSmall,
                            color = MutedText
                        )
                    }
                }
                
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = VodafoneRed
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    // 1. AI Intelligent prediction text banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(VodafoneRed.copy(alpha = 0.08f))
                            .border(1.dp, VodafoneRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = VodafoneRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "توقعات المستشار المالي الذكي (AI)",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = VodafoneRed
                                )
                            }
                            Text(
                                text = aiBriefText,
                                style = MaterialTheme.typography.bodySmall,
                                color = DynamicWhite,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    // 2. Beautiful side-by-side charts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // A: Donut Chart showing core spend limit
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                            border = BorderStroke(1.dp, CharcoalBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = "استهلاك الحد الشهري",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MutedText,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(90.dp)
                                ) {
                                    val strokeWidth = 10.dp
                                    val currentVodafoneRed = VodafoneRed
                                    val currentCardBorder = CharcoalBorder
                                    Canvas(modifier = Modifier.size(80.dp)) {
                                        // Background circular ring
                                        drawCircle(
                                            color = currentCardBorder,
                                            style = Stroke(width = strokeWidth.toPx())
                                        )
                                        // Animated progress sweep
                                        drawArc(
                                            color = currentVodafoneRed,
                                            startAngle = -90f,
                                            sweepAngle = animatedSpendProgress * 360f,
                                            useCenter = false,
                                            style = Stroke(width = strokeWidth.toPx())
                                        )
                                    }
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${(spendProgress * 100).toInt()}%",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Black,
                                            color = DynamicWhite
                                        )
                                        Text(
                                            text = "مستهلك",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedText,
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "المنفق: ${formatter.format(totalTransfersMonth)} ج.م",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = DynamicWhite,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // B: Visual Categorized Bar showing (Transfers vs Recharges)
                        Card(
                            colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                            border = BorderStroke(1.dp, CharcoalBorder),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.3f)
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "توازن التدفق الشهري (ج.م)",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MutedText
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                // Transfers block
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("سحب وتحويل", style = MaterialTheme.typography.labelSmall, color = DynamicWhite)
                                        Text("${formatter.format(totalTransfersMonth)} ج.م", style = MaterialTheme.typography.labelSmall, color = VodafoneRed, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { if ((totalDepositsMonth + totalTransfersMonth) <= 0) 0f else (totalTransfersMonth / (totalDepositsMonth + totalTransfersMonth)).toFloat() },
                                        color = VodafoneRed,
                                        trackColor = CharcoalBorder,
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                }

                                // Deposits block
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("إيداع وشحن", style = MaterialTheme.typography.labelSmall, color = DynamicWhite)
                                        Text("${formatter.format(totalDepositsMonth)} ج.م", style = MaterialTheme.typography.labelSmall, color = SafeGreen, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = { if ((totalDepositsMonth + totalTransfersMonth) <= 0) 0f else (totalDepositsMonth / (totalDepositsMonth + totalTransfersMonth)).toFloat() },
                                        color = SafeGreen,
                                        trackColor = CharcoalBorder,
                                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp))
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

fun getStartOfMonth(): Long {
    val cal = Calendar.getInstance()
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    return cal.timeInMillis
}

fun Modifier.swipeToSwitchWallet(
    wallets: List<Wallet>,
    activeWallet: Wallet?,
    onSelectWallet: (Wallet) -> Unit
): Modifier {
    if (wallets.size <= 1) return this
    return this.pointerInput(wallets, activeWallet) {
        var totalDrag = 0f
        detectHorizontalDragGestures(
            onDragEnd = {
                val activeIndex = wallets.indexOfFirst { it.phoneNumber == (activeWallet?.phoneNumber ?: "") }
                if (activeIndex != -1) {
                    if (totalDrag > 120f) {
                        // Swipe Right -> Select Previous Wallet (Arabic RTL layout friendly)
                        val prevIndex = if (activeIndex > 0) activeIndex - 1 else wallets.size - 1
                        onSelectWallet(wallets[prevIndex])
                    } else if (totalDrag < -120f) {
                        // Swipe Left -> Select Next Wallet
                        val nextIndex = if (activeIndex < wallets.size - 1) activeIndex + 1 else 0
                        onSelectWallet(wallets[nextIndex])
                    }
                }
                totalDrag = 0f
            },
            onHorizontalDrag = { _, dragAmount ->
                totalDrag += dragAmount
            }
        )
    }
}

data class AlertVisuals(
    val cardBg: Color,
    val tintColor: Color,
    val titleAr: String,
    val titleEn: String,
    val descAr: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@Composable
fun StatusAlertBanner(
    spentToday: Double,
    spentThisMonth: Double,
    settings: LimitSettings,
    warningPercentage: Float,
    activeWallet: Wallet? = null,
    modifier: Modifier = Modifier
) {
    val dailyLimit = activeWallet?.dailyLimit ?: settings.dailyLimit
    val monthlyLimit = activeWallet?.monthlyLimit ?: settings.monthlyLimit

    val isDailyExceeded = spentToday >= dailyLimit
    val isMonthlyExceeded = spentThisMonth >= monthlyLimit

    val isDailyWarning = spentToday >= (dailyLimit * warningPercentage) && !isDailyExceeded
    val isMonthlyWarning = spentThisMonth >= (monthlyLimit * warningPercentage) && !isMonthlyExceeded

    val visuals = when {
        isDailyExceeded -> {
            AlertVisuals(
                cardBg = Color(0x26E74C3C),
                tintColor = ExceededRed,
                titleAr = "لقد تجاوزت الحد اليومي!",
                titleEn = "Daily Limit Exceeded!",
                descAr = "لقد أنفقت ${spentToday.toInt()} من أصل ${dailyLimit.toInt()} جنيه اليوم.",
                icon = Icons.Default.Warning
            )
        }
        isMonthlyExceeded -> {
            AlertVisuals(
                cardBg = Color(0x26E74C3C),
                tintColor = ExceededRed,
                titleAr = "لقد تجاوزت الحد الشهري!",
                titleEn = "Monthly Limit Exceeded!",
                descAr = "لقد أنفقت ${spentThisMonth.toInt()} من أصل ${monthlyLimit.toInt()} جنيه هذا الشهر.",
                icon = Icons.Default.Warning
            )
        }
        isDailyWarning -> {
            AlertVisuals(
                cardBg = Color(0x1AF39C12),
                tintColor = WarningOrange,
                titleAr = "إقتربت من تجاوز الحد اليومي!",
                titleEn = "Approaching Daily Limit!",
                descAr = "لقد أنفقت أكثر من ${(warningPercentage * 100).toInt()}% من حدك اليومي.",
                icon = Icons.Default.Warning
            )
        }
        isMonthlyWarning -> {
            AlertVisuals(
                cardBg = Color(0x1AF39C12),
                tintColor = WarningOrange,
                titleAr = "إقتربت من تجاوز الحد الشهري!",
                titleEn = "Approaching Monthly Limit!",
                descAr = "لقد أنفقت أكثر من ${(warningPercentage * 100).toInt()}% من حدك الشهري.",
                icon = Icons.Default.Warning
            )
        }
        else -> {
            AlertVisuals(
                cardBg = Color(0x1A2ECC71),
                tintColor = SafeGreen,
                titleAr = "التحويلات في نطاق آمن",
                titleEn = "Transactions Safe & Secure",
                descAr = "أنت داخل الحدود المفروضة بنجاح وطبقاً لقوانين فودافون كاش.",
                icon = Icons.Default.CheckCircle
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(visuals.cardBg)
            .border(1.dp, visuals.tintColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(visuals.tintColor.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = visuals.icon,
                    contentDescription = null,
                    tint = visuals.tintColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = visuals.titleAr,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 12.sp, lineHeight = 14.sp),
                    color = DynamicWhite,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = visuals.titleEn,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = visuals.tintColor,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = visuals.descAr,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
                    color = DynamicWhite.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun NestedCircularProgressDonut(
    outboundProgress: Float,
    inboundProgress: Float,
    outboundColor: Color,
    inboundColor: Color,
    title: String,
    outboundSpent: String,
    outboundLimit: String,
    inboundCollected: String,
    inboundLimit: String,
    leftOutbound: Double,
    leftInbound: Double,
    isMonthly: Boolean,
    formatter: DecimalFormat,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CharcoalBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = DynamicWhite,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeOut = 8.dp.toPx()
                    val strokeIn = 6.dp.toPx()
                    val spacing = 8.dp.toPx()
                    
                    val sizeOutWidth = size.width - strokeOut
                    val sizeOutHeight = size.height - strokeOut
                    
                    drawArc(
                        color = CharcoalBorder.copy(alpha = 0.4f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeOut, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(strokeOut / 2, strokeOut / 2),
                        size = androidx.compose.ui.geometry.Size(sizeOutWidth, sizeOutHeight)
                    )
                    drawArc(
                        color = outboundColor,
                        startAngle = -90f,
                        sweepAngle = 360f * outboundProgress,
                        useCenter = false,
                        style = Stroke(width = strokeOut, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(strokeOut / 2, strokeOut / 2),
                        size = androidx.compose.ui.geometry.Size(sizeOutWidth, sizeOutHeight)
                    )
                    
                    val offsetIn = strokeOut + spacing
                    val sizeInWidth = size.width - (offsetIn * 2)
                    val sizeInHeight = size.height - (offsetIn * 2)
                    
                    drawArc(
                        color = CharcoalBorder.copy(alpha = 0.4f),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeIn, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(offsetIn, offsetIn),
                        size = androidx.compose.ui.geometry.Size(sizeInWidth, sizeInHeight)
                    )
                    drawArc(
                        color = inboundColor,
                        startAngle = -90f,
                        sweepAngle = 360f * inboundProgress,
                        useCenter = false,
                        style = Stroke(width = strokeIn, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                        topLeft = androidx.compose.ui.geometry.Offset(offsetIn, offsetIn),
                        size = androidx.compose.ui.geometry.Size(sizeInWidth, sizeInHeight)
                    )
                }
                
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${(outboundProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                        color = outboundColor
                    )
                    Text(
                        text = "صادر",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                        color = MutedText
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${(inboundProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = inboundColor
                    )
                    Text(
                        text = "وارد",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp),
                        color = MutedText
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(outboundColor))
                    Text("الصادر :", style = MaterialTheme.typography.labelSmall, color = MutedText)
                }
                Text("$outboundSpent / $outboundLimit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = DynamicWhite)
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(inboundColor))
                    Text("الوارد :", style = MaterialTheme.typography.labelSmall, color = MutedText)
                }
                Text("$inboundCollected / $inboundLimit", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = DynamicWhite)
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(8.dp))

            // Two dynamic remaining boxes side-by-side inside the donut card!
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Outbound/Transfer Remaining Box
                val leftOutboundStr = "${formatter.format(leftOutbound)} ج.م"
                val leftOutboundFontSize = if (leftOutboundStr.length > 12) 11.sp else if (leftOutboundStr.length > 9) 13.sp else 15.sp

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(41.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CharcoalBg.copy(alpha = 0.40f))
                        .border(1.dp, outboundColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isMonthly) "متبقي تحويل الشهر" else "متبقي تحويل اليوم",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            color = MutedText,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = leftOutboundStr,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = leftOutboundFontSize, fontWeight = FontWeight.Black),
                            color = outboundColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }

                // Inbound/Deposit Remaining Box
                val leftInboundStr = "${formatter.format(leftInbound)} ج.م"
                val leftInboundFontSize = if (leftInboundStr.length > 12) 11.sp else if (leftInboundStr.length > 9) 13.sp else 15.sp

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(41.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(CharcoalBg.copy(alpha = 0.40f))
                        .border(1.dp, inboundColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isMonthly) "متبقي إيداع الشهر" else "متبقي إيداع اليوم",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            fontWeight = FontWeight.Bold,
                            color = MutedText,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = leftInboundStr,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = leftInboundFontSize, fontWeight = FontWeight.Black),
                            color = inboundColor,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LimitProgressCard(
    title: String,
    subTitle: String,
    spent: Double,
    limit: Double,
    warningThreshold: Double,
    modifier: Modifier = Modifier,
    formatter: DecimalFormat
) {
    val progress = remember(spent, limit) {
        if (limit > 0) (spent / limit).coerceIn(0.0, 1.0).toFloat() else 0f
    }

    val progressColor = when {
        spent >= limit -> ExceededRed
        spent >= warningThreshold -> WarningOrange
        else -> SafeGreen
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CharcoalBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = DynamicWhite
            )
            Text(
                text = subTitle,
                style = MaterialTheme.typography.labelSmall,
                color = MutedText
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Amount Numbers
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = formatter.format(spent),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = DynamicWhite
                )
                Text(
                    text = "ج.م",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Limiting Indicator
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = progressColor,
                trackColor = CharcoalBorder
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Limit details
            Text(
                text = "الحد الأقصى: ${formatter.format(limit)} ج.م",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText
            )
        }
    }
}

@Composable
fun CompactDailyLimitsCard(
    spentToday: Double,
    dailyLimit: Double,
    depositedToday: Double,
    dailyDepositLimit: Double,
    warningPercentage: Float,
    formatter: DecimalFormat,
    modifier: Modifier = Modifier
) {
    val transferProgress = remember(spentToday, dailyLimit) {
        if (dailyLimit > 0) (spentToday / dailyLimit).coerceIn(0.0, 1.0).toFloat() else 0f
    }
    val depositProgress = remember(depositedToday, dailyDepositLimit) {
        if (dailyDepositLimit > 0) (depositedToday / dailyDepositLimit).coerceIn(0.0, 1.0).toFloat() else 0f
    }

    val transferThreshold = dailyLimit * warningPercentage
    val depositThreshold = dailyDepositLimit * warningPercentage

    val transferColor = when {
        spentToday >= dailyLimit -> ExceededRed
        spentToday >= transferThreshold -> WarningOrange
        else -> SafeGreen
    }

    val depositColor = when {
        depositedToday >= dailyDepositLimit -> ExceededRed
        depositedToday >= depositThreshold -> WarningOrange
        else -> SafeGreen
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CharcoalBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(VodafoneRed.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        tint = VodafoneRed,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Text(
                    text = "حدود اليوم",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = DynamicWhite
                )
            }

            HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.3f))

            // Transfer Limit Progress
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تحويل صادر",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                    Text(
                        text = "${((transferProgress * 100).toInt())}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = transferColor
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatter.format(spentToday),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                        color = DynamicWhite
                    )
                    Text(
                        text = "/ ${formatter.format(dailyLimit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                }
                LinearProgressIndicator(
                    progress = { transferProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = transferColor,
                    trackColor = CharcoalBorder
                )
            }

            HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.12f))

            // Deposit Limit Progress
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إيداع وارد",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                    Text(
                        text = "${((depositProgress * 100).toInt())}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = depositColor
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatter.format(depositedToday),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                        color = DynamicWhite
                    )
                    Text(
                        text = "/ ${formatter.format(dailyDepositLimit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                }
                LinearProgressIndicator(
                    progress = { depositProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = depositColor,
                    trackColor = CharcoalBorder
                )
            }
        }
    }
}

@Composable
fun CompactMonthlyLimitsCard(
    spentThisMonth: Double,
    monthlyLimit: Double,
    depositedThisMonth: Double,
    monthlyDepositLimit: Double,
    warningPercentage: Float,
    formatter: DecimalFormat,
    carriedOverTransfer: Double = 0.0,
    carriedOverDeposit: Double = 0.0,
    modifier: Modifier = Modifier
) {
    val transferProgress = remember(spentThisMonth, monthlyLimit) {
        if (monthlyLimit > 0) (spentThisMonth / monthlyLimit).coerceIn(0.0, 1.0).toFloat() else 0f
    }
    val depositProgress = remember(depositedThisMonth, monthlyDepositLimit) {
        if (monthlyDepositLimit > 0) (depositedThisMonth / monthlyDepositLimit).coerceIn(0.0, 1.0).toFloat() else 0f
    }

    val transferThreshold = monthlyLimit * warningPercentage
    val depositThreshold = monthlyDepositLimit * warningPercentage

    val transferColor = when {
        spentThisMonth >= monthlyLimit -> ExceededRed
        spentThisMonth >= transferThreshold -> WarningOrange
        else -> SafeGreen
    }

    val depositColor = when {
        depositedThisMonth >= monthlyDepositLimit -> ExceededRed
        depositedThisMonth >= depositThreshold -> WarningOrange
        else -> SafeGreen
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CharcoalBorder),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(WarningOrange.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = WarningOrange,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = "حدود الشهر",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = DynamicWhite
                    )
                }

                if (carriedOverDeposit > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(SafeGreen.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "🔄 +${formatter.format(carriedOverDeposit)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SafeGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.3f))

            // Transfer Limit Progress
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تحويل صادر",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                    Text(
                        text = "${((transferProgress * 100).toInt())}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = transferColor
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatter.format(spentThisMonth),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                        color = DynamicWhite
                    )
                    Text(
                        text = "/ ${formatter.format(monthlyLimit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                }
                LinearProgressIndicator(
                    progress = { transferProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = transferColor,
                    trackColor = CharcoalBorder
                )
            }

            HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.12f))

            // Deposit Limit Progress
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إيداع وارد",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                    Text(
                        text = "${((depositProgress * 100).toInt())}%",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = depositColor
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatter.format(depositedThisMonth),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                        color = DynamicWhite
                    )
                    Text(
                        text = "/ ${formatter.format(monthlyDepositLimit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                }
                LinearProgressIndicator(
                    progress = { depositProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = depositColor,
                    trackColor = CharcoalBorder
                )
            }
        }
    }
}

@Composable
fun TransferForm(
    phoneNumber: String,
    amount: String,
    notes: String,
    timestamp: Long,
    liveWarning: LiveWarningState,
    isDeposit: Boolean,
    onDepositChange: (Boolean) -> Unit,
    onPhoneChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onTimestampChange: (Long) -> Unit,
    onAddTransaction: () -> Unit,
    onDialUssd: () -> Unit,
    formatter: DecimalFormat
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, CharcoalBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "تسجيل عملية جديدة",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = DynamicWhite
            )

            // Dynamic Operation type chooser
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Outbound Withdrawal/Transfer button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (!isDeposit) VodafoneRed.copy(alpha = 0.15f) else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (!isDeposit) VodafoneRed else CharcoalBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onDepositChange(false) }
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = null,
                            tint = if (!isDeposit) VodafoneLightRed else MutedText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "سحب / تحويل صادر",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (!isDeposit) DynamicWhite else MutedText
                        )
                    }
                }

                // Inbound Deposit/Topup button
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isDeposit) SafeGreen.copy(alpha = 0.12f) else Color.Transparent)
                        .border(
                            1.5.dp,
                            if (isDeposit) SafeGreen else CharcoalBorder,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onDepositChange(true) }
                        .padding(vertical = 10.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = if (isDeposit) SafeGreen else MutedText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "شحن / إيداع وارد",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isDeposit) DynamicWhite else MutedText
                        )
                    }
                }
            }

            // Amount Field
            OutlinedTextField(
                value = amount,
                onValueChange = onAmountChange,
                label = { Text("المبلغ ج.م") },
                placeholder = { Text("مثال: 1500") },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = if (isDeposit) SafeGreen else VodafoneRed) },
                suffix = { Text("ج.م", color = DynamicWhite) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("amount_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isDeposit) SafeGreen else VodafoneRed,
                    unfocusedBorderColor = CharcoalBorder,
                    focusedLabelColor = if (isDeposit) SafeGreen else VodafoneRed,
                    unfocusedLabelColor = MutedText,
                    focusedTextColor = DynamicWhite,
                    unfocusedTextColor = DynamicWhite,
                    cursorColor = if (isDeposit) SafeGreen else VodafoneRed
                )
            )

            // Notes / Optional Name or Number Field to remember the transaction
            OutlinedTextField(
                value = notes,
                onValueChange = onNotesChange,
                label = { Text("اسم أو رقم أو ملاحظة لتذكر العملية (اختياري)") },
                placeholder = { Text("مثال: دفع الإيجار، بلال، 010... إلخ") },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = if (isDeposit) SafeGreen else VodafoneRed) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notes_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = if (isDeposit) SafeGreen else VodafoneRed,
                    unfocusedBorderColor = CharcoalBorder,
                    focusedLabelColor = if (isDeposit) SafeGreen else VodafoneRed,
                    unfocusedLabelColor = MutedText,
                    focusedTextColor = DynamicWhite,
                    unfocusedTextColor = DynamicWhite,
                    cursorColor = if (isDeposit) SafeGreen else VodafoneRed
                )
            )

            // Live Alert Box (updates interactively as user types the amount)
            AnimatedVisibility(
                visible = !isDeposit && liveWarning.level != LiveWarningLevel.NONE,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (liveWarning.level == LiveWarningLevel.CRITICAL) ExceededRed.copy(alpha = 0.15f)
                            else WarningOrange.copy(alpha = 0.15f)
                        )
                        .border(
                            1.dp,
                            if (liveWarning.level == LiveWarningLevel.CRITICAL) ExceededRed.copy(alpha = 0.3f)
                            else WarningOrange.copy(alpha = 0.3f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (liveWarning.level == LiveWarningLevel.CRITICAL) ExceededRed else WarningOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = liveWarning.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = DynamicWhite,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4.1 Beautiful custom Date & Time Selector for the Transaction
            val context = LocalContext.current
            val displaySdf = remember { 
                SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale("ar", "EG")).apply {
                    numberFormat = NumberFormat.getInstance(Locale.US)
                }
            }
            val formattedDateTime = remember(timestamp) { displaySdf.format(Date(timestamp)) }

            val onShowDatePicker = {
                val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
                val datePickerDialog = android.app.DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        val timePickerDialog = android.app.TimePickerDialog(
                            context,
                            { _, hourOfDay, minute ->
                                val finalCalendar = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                                    set(Calendar.MINUTE, minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                onTimestampChange(finalCalendar.timeInMillis)
                            },
                            calendar.get(Calendar.HOUR_OF_DAY),
                            calendar.get(Calendar.MINUTE),
                            false
                        )
                        timePickerDialog.show()
                    },
                    calendar.get(Calendar.YEAR),
                    calendar.get(Calendar.MONTH),
                    calendar.get(Calendar.DAY_OF_MONTH)
                )
                datePickerDialog.show()
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "تاريخ ووقت العملية",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                    fontWeight = FontWeight.Medium
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(CharcoalSurface)
                        .border(1.dp, CharcoalBorder, RoundedCornerShape(12.dp))
                        .clickable { onShowDatePicker() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "اختيار التاريخ والوقت",
                            tint = if (isDeposit) SafeGreen else VodafoneRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = formattedDateTime,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = DynamicWhite
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background((if (isDeposit) SafeGreen else VodafoneRed).copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "تعديل الوقت",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDeposit) SafeGreen else VodafoneLightRed
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Add to records list locally (Full-width)
            Button(
                onClick = {
                    if (amount.isNotEmpty() && (amount.toDoubleOrNull() ?: 0.0) > 0) {
                        onAddTransaction()
                    }
                },
                enabled = amount.isNotEmpty() && (amount.toDoubleOrNull() ?: 0.0) > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDeposit) SafeGreen else VodafoneRed,
                    disabledContainerColor = CharcoalBorder,
                    contentColor = Color.White,
                    disabledContentColor = MutedText
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_transaction_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isDeposit) "إيداع بالمحفظة" else "تسجيل صادر",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItemRow(
    transaction: Transaction,
    balanceAfter: Double?,
    onDelete: () -> Unit,
    formatter: DecimalFormat
) {
    val dateString = remember(transaction.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale("ar", "EG")).apply {
            numberFormat = NumberFormat.getInstance(Locale.US)
        }
        sdf.format(Date(transaction.timestamp))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CharcoalCard),
        border = BorderStroke(1.dp, CharcoalBorder),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("transaction_item_${transaction.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Circle icon with dynamic tint and shape (More compact)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(if (transaction.isDeposit) SafeGreen.copy(alpha = 0.15f) else VodafoneRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (transaction.isDeposit) Icons.Default.Add else Icons.Default.Send,
                        contentDescription = null,
                        tint = if (transaction.isDeposit) SafeGreen else VodafoneRed,
                        modifier = Modifier.size(14.dp)
                    )
                }

                // Info Text (Compact size)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (transaction.recipientNumber.startsWith("0")) "إلى: ${transaction.recipientNumber}" else transaction.recipientNumber,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = DynamicWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "رقم: ${transaction.senderWalletNumber}",
                            style = MaterialTheme.typography.labelSmall,
                            color = VodafoneLightRed,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (transaction.notes.isNotEmpty()) {
                            Text(
                                text = "(${transaction.notes})",
                                style = MaterialTheme.typography.labelSmall,
                                color = DynamicWhite.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = dateString,
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText.copy(alpha = 0.8f)
                    )
                    if (balanceAfter != null) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "الرصيد المتاح: ${formatter.format(balanceAfter)} ج.م",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (transaction.isDeposit) SafeGreen.copy(alpha = 0.9f) else DynamicWhite.copy(alpha = 0.7f),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Amount Indicator (Highly optimized text sizing)
                Column(
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        text = if (transaction.isDeposit) "+ ${formatter.format(transaction.amount)} ج.م" else "- ${formatter.format(transaction.amount)} ج.م",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Black,
                        color = if (transaction.isDeposit) SafeGreen else VodafoneLightRed
                    )
                    Text(
                        text = if (transaction.isDeposit) "تم الشحن" else "تم الخصم",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (transaction.isDeposit) SafeGreen.copy(alpha = 0.9f) else MutedText
                    )
                }

                // Delete Button (Visual trash can restored - High Visibility)
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("delete_tx_btn_${transaction.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "حذف العملية",
                        tint = ExceededRed.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FilterButton(
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) VodafoneRed else CharcoalCard)
            .border(1.dp, if (active) VodafoneRed else CharcoalBorder, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (active) Color.White else MutedText
        )
    }
}

@Composable
fun EmptyHistoryView(isSearching: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isSearching) Icons.Default.Search else Icons.Default.List,
                contentDescription = null,
                tint = MutedText.copy(alpha = 0.5f),
                modifier = Modifier.size(48.dp)
            )
            Text(
                text = if (isSearching) "لم يتم العثور على نتائج تطابق بحثك" else "لا توجد معاملات مسجلة بعد",
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isSearching) "يرجى التحقق من الكلمة المفتاحية أو رقم الهاتف والمحاولة مرة أخرى." else "سجل معاملاتك السابقة لمراقبة الحد المفروض بنجاح.",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun LimitSettingsDialog(
    currentSettings: LimitSettings,
    activeWallet: Wallet?,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onDeleteWallet: () -> Unit,
    onSave: (Double, Double, Double, Double, Double, Float) -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onCloudBackupTrigger: (suspend () -> String)? = null,
    onCloudRestoreTrigger: (suspend (String) -> Boolean)? = null
) {
    var dailyInput by remember { mutableStateOf((activeWallet?.dailyLimit ?: currentSettings.dailyLimit).toInt().toString()) }
    var monthlyInput by remember { mutableStateOf((activeWallet?.monthlyLimit ?: currentSettings.monthlyLimit).toInt().toString()) }
    var dailyDepositInput by remember { mutableStateOf((activeWallet?.dailyDepositLimit ?: currentSettings.dailyDepositLimit).toInt().toString()) }
    var monthlyDepositInput by remember { mutableStateOf((activeWallet?.monthlyDepositLimit ?: currentSettings.monthlyDepositLimit).toInt().toString()) }
    var initialBalanceInput by remember { mutableStateOf((activeWallet?.initialBalance ?: 0.0).toInt().toString()) }
    var warningProgress by remember { mutableStateOf(currentSettings.warningPercentage) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CharcoalBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .heightIn(max = 620.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تعديل حدود التحويل والأمان",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }

                HorizontalDivider(color = CharcoalBorder)

                // 1. Transfer Limits Group
                Text(
                    text = "حدود الاستهلاك اليومية والشهرية (صادر)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = VodafoneRed
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = dailyInput,
                        onValueChange = { if (it.all { char -> char.isDigit() }) dailyInput = it },
                        label = { Text("يومي (المقترح 60ألف)", style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VodafoneRed,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = VodafoneRed,
                            unfocusedLabelColor = MutedText
                        )
                    )

                    OutlinedTextField(
                        value = monthlyInput,
                        onValueChange = { if (it.all { char -> char.isDigit() }) monthlyInput = it },
                        label = { Text("شهري (المقترح 200ألف)", style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VodafoneRed,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = VodafoneRed,
                            unfocusedLabelColor = MutedText
                        )
                    )
                }

                // 2. Deposit Limits Group
                Text(
                    text = "حدود الإيداع والاستقبال (وارد)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SafeGreen
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedTextField(
                        value = dailyDepositInput,
                        onValueChange = { if (it.all { char -> char.isDigit() }) dailyDepositInput = it },
                        label = { Text("يومي (المقترح 60ألف)", style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VodafoneRed,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = VodafoneRed,
                            unfocusedLabelColor = MutedText
                        )
                    )

                    OutlinedTextField(
                        value = monthlyDepositInput,
                        onValueChange = { if (it.all { char -> char.isDigit() }) monthlyDepositInput = it },
                        label = { Text("شهري (المقترح 200ألف)", style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VodafoneRed,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = VodafoneRed,
                            unfocusedLabelColor = MutedText
                        )
                    )
                }

                // Initial Balance input
                if (activeWallet != null) {
                    OutlinedTextField(
                        value = initialBalanceInput,
                        onValueChange = { if (it.all { char -> char.isDigit() }) initialBalanceInput = it },
                        label = { Text("الرصيد الافتتاحي لهذه المحفظة ج.م", style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodySmall,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VodafoneRed,
                            unfocusedBorderColor = CharcoalBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedLabelColor = VodafoneRed,
                            unfocusedLabelColor = MutedText
                        )
                    )
                }

                // Warning threshold slider
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "تنبيه الأمان عند الاستخدام المفرط بنسبة",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White
                        )
                        Text(
                            text = "${(warningProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Black,
                            color = WarningOrange
                        )
                    }
                    Slider(
                        value = warningProgress,
                        onValueChange = { warningProgress = it },
                        valueRange = 0.5f..0.95f,
                        colors = SliderDefaults.colors(
                            thumbColor = VodafoneRed,
                            activeTrackColor = VodafoneRed,
                            inactiveTrackColor = CharcoalBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "يرسل التطبيق إشعاراً مرئياً عند اقتراب الحد الحالي من النسبة المحددة.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                }

                // Delete Wallet Button (Only if it's not the only wallet left)
                if (activeWallet != null && canDelete) {
                    Button(
                        onClick = onDeleteWallet,
                        colors = ButtonDefaults.buttonColors(containerColor = ExceededRed.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, ExceededRed.copy(alpha = 0.5f)),
                        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "حذف الرقم",
                            tint = ExceededRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "حذف هذا الرقم (${activeWallet.label}) نهائياً",
                            color = ExceededRed,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Backup & Restore Section
                HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.5f))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = VodafoneRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "النسخ الاحتياطي واستعادة البيانات",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    Text(
                        text = "يمكنك حفظ نسخة احتياطية من معاملات ومحفظة الهاتف كملف لاستعادته لاحقاً.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedText
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Export Button
                        OutlinedButton(
                            onClick = onExportBackup,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SafeGreen),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("حفظ نسخة للهاتف", style = MaterialTheme.typography.labelSmall, color = SafeGreen)
                        }

                        // Import Button
                        OutlinedButton(
                            onClick = onImportBackup,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, VodafoneRed.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = VodafoneRed),
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("استعادة نسخة", style = MaterialTheme.typography.labelSmall, color = VodafoneRed)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Google Drive Sync Section
                val context = LocalContext.current
                var isDriveEnabled by remember { mutableStateOf(com.example.util.GoogleDriveBackupHelper.isBackupEnabled(context)) }
                val syncState by com.example.util.GoogleDriveBackupHelper.syncStatus.collectAsStateWithLifecycle(initialValue = com.example.util.GoogleDriveBackupHelper.SyncStatus.Idle)
                val scope = rememberCoroutineScope()

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CharcoalBg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .border(1.dp, CharcoalBorder.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(0.7f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = SafeGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Column {
                                Text(
                                    text = "النسخ الاحتياطي التلقائي الصامت",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "مزامنة سحابية بحساب Google Drive مشفرة",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MutedText
                                )
                            }
                        }
                        Switch(
                            checked = isDriveEnabled,
                            onCheckedChange = { checked ->
                                isDriveEnabled = checked
                                com.example.util.GoogleDriveBackupHelper.setBackupEnabled(context, checked)
                                if (checked) {
                                    scope.launch {
                                        try {
                                            if (onCloudBackupTrigger != null) {
                                                val jsonBackup = onCloudBackupTrigger()
                                                val success = com.example.util.GoogleDriveBackupHelper.performSilentCloudBackup(context, jsonBackup)
                                                if (success) {
                                                    Toast.makeText(context, "تم حفظ أول نسخة احتياطية سحابياً! ✅☁️", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = SafeGreen,
                                checkedTrackColor = SafeGreen.copy(alpha = 0.4f),
                                uncheckedThumbColor = MutedText,
                                uncheckedTrackColor = CharcoalBorder
                            )
                        )
                    }

                    // Status and credentials
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val statusText = when (val state = syncState) {
                            is com.example.util.GoogleDriveBackupHelper.SyncStatus.Idle -> {
                                val lastTime = com.example.util.GoogleDriveBackupHelper.getLastSyncTime(context)
                                if (lastTime > 0) {
                                    val formatted = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale("ar", "EG")).format(java.util.Date(lastTime))
                                    "آخر مزامنة ناجحة: $formatted"
                                } else {
                                    "مستعد وبانتظار تفعيل المزامنة"
                                }
                            }
                            is com.example.util.GoogleDriveBackupHelper.SyncStatus.Syncing -> "جاري مزامنة الملفات صامتاً..."
                            is com.example.util.GoogleDriveBackupHelper.SyncStatus.Success -> {
                                val formatted = java.text.SimpleDateFormat("yyyy/MM/dd HH:mm", java.util.Locale("ar", "EG")).format(java.util.Date(state.lastSyncTime))
                                "تم التحديث بنجاح: $formatted ✅"
                            }
                            is com.example.util.GoogleDriveBackupHelper.SyncStatus.Error -> "خطأ بالمزامنة: ${state.message} ⚠️"
                        }
                        
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = if (syncState is com.example.util.GoogleDriveBackupHelper.SyncStatus.Error) VodafoneLightRed else SafeGreen,
                            modifier = Modifier.weight(1f)
                        )

                        // Manual Restore Button
                        OutlinedButton(
                            onClick = {
                                if (onCloudRestoreTrigger != null) {
                                    scope.launch {
                                        val backupJson = com.example.util.GoogleDriveBackupHelper.downloadBackupFromDrive(context)
                                        if (backupJson != null) {
                                            val restored = onCloudRestoreTrigger(backupJson)
                                            if (restored) {
                                                Toast.makeText(context, "تم استعادة معاملاتك في صمت من Google Drive بنجاح! ☁️🔒✅", Toast.LENGTH_LONG).show()
                                            } else {
                                                Toast.makeText(context, "فشل فك تشفير النسخة السحابية.", Toast.LENGTH_SHORT).show()
                                            }
                                        } else {
                                            Toast.makeText(context, "لم يتم العثور على نسخ احتياطية في حساب Google Drive.", Toast.LENGTH_LONG).show()
                                        }
                                    }
                                }
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, SafeGreen.copy(alpha = 0.8f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SafeGreen),
                            contentPadding = PaddingValues(vertical = 2.dp, horizontal = 6.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text("استعادة من السحاب ☁️", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = SafeGreen)
                        }
                    }
                }

                HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.3f))

                // Copyright Statement
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        Text(
                            text = "حقوق الملكية وتطوير المهندس صبري السيد",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = VodafoneLightRed
                        )
                        Text(
                            text = "01020303230",
                            style = MaterialTheme.typography.labelSmall,
                            color = SafeGreen,
                            letterSpacing = 1.sp
                        )
                    }
                }

                HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.3f))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = CharcoalCard),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("إلغاء", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }

                    Button(
                        onClick = {
                            val daily = dailyInput.toDoubleOrNull() ?: 60000.0
                            val monthly = monthlyInput.toDoubleOrNull() ?: 200000.0
                            val deposit = dailyDepositInput.toDoubleOrNull() ?: 60000.0
                            val monDep = monthlyDepositInput.toDoubleOrNull() ?: 200000.0
                            val initBal = initialBalanceInput.toDoubleOrNull() ?: 0.0
                            onSave(daily, monthly, deposit, monDep, initBal, warningProgress)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = VodafoneRed),
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        Text("حفظ التغييرات", color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun WalletBar(
    wallets: List<Wallet>,
    activeWallet: Wallet?,
    walletBalances: Map<String, Double>,
    currencyFormatter: DecimalFormat,
    transactions: List<Transaction>,
    onSelectWallet: (Wallet) -> Unit,
    onAddWalletClick: () -> Unit,
    onDeleteWallet: (Wallet) -> Unit,
    modifier: Modifier = Modifier
) {
    val (startOfToday, startOfThisMonth) = remember(transactions) {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val today = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, 1)
        val month = cal.timeInMillis
        Pair(today, month)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "المحافظ النشطة (حسابات الأرقام)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = DynamicWhite
            )
            Text(
                text = "سجل لكل رقم حدوده منفصلاً",
                style = MaterialTheme.typography.labelSmall,
                color = MutedText
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // List of active wallets
            wallets.forEach { wallet ->
                val isActive = activeWallet?.phoneNumber == wallet.phoneNumber
                
                val walletTxs = remember(transactions, wallet.phoneNumber) {
                    transactions.filter { it.senderWalletNumber == wallet.phoneNumber }
                }
                val hasTransactions = walletTxs.isNotEmpty()

                val spentTodayVal = remember(walletTxs, startOfToday) {
                    walletTxs.filter { !it.isDeposit && it.timestamp >= startOfToday }.sumOf { it.amount }
                }
                val spentMonthVal = remember(walletTxs, startOfThisMonth) {
                    walletTxs.filter { !it.isDeposit && it.timestamp >= startOfThisMonth }.sumOf { it.amount }
                }
                val depositedTodayVal = remember(walletTxs, startOfToday) {
                    walletTxs.filter { it.isDeposit && it.timestamp >= startOfToday }.sumOf { it.amount }
                }
                val prevMonthBalance = remember(walletTxs, startOfThisMonth) {
                    val initial = wallet.initialBalance
                    val prevTxs = walletTxs.filter { it.timestamp < startOfThisMonth }
                    var current = initial
                    prevTxs.forEach { tx ->
                        if (tx.isDeposit) {
                            current += tx.amount
                        } else {
                            current -= tx.amount
                        }
                    }
                    current.coerceAtLeast(0.0)
                }

                val depositedMonthVal = remember(walletTxs, startOfThisMonth, prevMonthBalance) {
                    val currentMonthDeposits = walletTxs.filter { it.isDeposit && it.timestamp >= startOfThisMonth }.sumOf { it.amount }
                    currentMonthDeposits + prevMonthBalance
                }

                val reachedDailyTransferLimit = spentTodayVal >= wallet.dailyLimit
                val reachedMonthlyTransferLimit = spentMonthVal >= wallet.monthlyLimit
                val reachedDailyDepositLimit = depositedTodayVal >= wallet.dailyDepositLimit
                val reachedMonthlyDepositLimit = depositedMonthVal >= wallet.monthlyDepositLimit

                val isLimitReached = reachedDailyTransferLimit || reachedMonthlyTransferLimit || reachedDailyDepositLimit || reachedMonthlyDepositLimit

                // Determine colors based on design specifications to satisfy user intent
                val containerColor = when {
                    isActive -> VodafoneRed
                    isLimitReached -> Color(0xFF331616) // Rich deep wine red indicating limit exhaustion
                    hasTransactions -> Color(0xFF10281F) // Smooth forest-green indicating used but safe wallet
                    else -> CharcoalCard // Muted gray indicating fresh, unused wallet
                }
                
                val borderColor = when {
                    isActive -> if (isLimitReached) WarningOrange else VodafoneRed
                    isLimitReached -> ExceededRed
                    hasTransactions -> SafeGreen
                    else -> CharcoalBorder
                }

                val borderWidth = if (isActive || isLimitReached || hasTransactions) 1.5.dp else 1.dp

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = containerColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(borderWidth, borderColor),
                    modifier = Modifier
                        .clickable { onSelectWallet(wallet) }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = wallet.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) Color.White else DynamicWhite
                                )
                                
                                // Beautiful Arabic badges to distinguish limits and activities
                                val statusText = when {
                                    isLimitReached -> "مكتملة الحد ⚠️"
                                    hasTransactions -> "مستعملة 🟢"
                                    else -> "لم تُستخدم ⚪"
                                }
                                val statusColor = when {
                                    isLimitReached -> if (isActive) Color.White else ExceededRed
                                    hasTransactions -> if (isActive) Color.White else SafeGreen
                                    else -> if (isActive) Color.White.copy(alpha = 0.8f) else MutedText
                                }
                                val statusBg = when {
                                    isLimitReached -> if (isActive) Color.White.copy(alpha = 0.25f) else ExceededRed.copy(alpha = 0.15f)
                                    hasTransactions -> if (isActive) Color.White.copy(alpha = 0.25f) else SafeGreen.copy(alpha = 0.15f)
                                    else -> if (isActive) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f)
                                }
                                Box(
                                    modifier = Modifier
                                        .background(statusBg, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = statusColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = wallet.phoneNumber,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isActive) Color.White.copy(alpha = 0.8f) else MutedText
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val balance = walletBalances[wallet.phoneNumber] ?: wallet.initialBalance
                            Text(
                                text = "رصيد: ${currencyFormatter.format(balance)} ج.م",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Black,
                                color = if (isActive) Color.White else SafeGreen
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddWalletDialog(
    phoneNumber: String,
    label: String,
    dailyLimit: String,
    monthlyLimit: String,
    dailyDepositLimit: String,
    monthlyDepositLimit: String,
    initialBalance: String,
    onPhoneChange: (String) -> Unit,
    onLabelChange: (String) -> Unit,
    onDailyLimitChange: (String) -> Unit,
    onMonthlyLimitChange: (String) -> Unit,
    onDailyDepositLimitChange: (String) -> Unit,
    onMonthlyDepositLimitChange: (String) -> Unit,
    onInitialBalanceChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CharcoalBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إضافة رقم محفظة جديد",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }

                HorizontalDivider(color = CharcoalBorder)

                // Phone/Wallet Number input
                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = onPhoneChange,
                    label = { Text("رقم هاتف المحفظة الجديد") },
                    placeholder = { Text("مثال: 01012345678") },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = VodafoneRed) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VodafoneRed,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = VodafoneRed,
                        unfocusedLabelColor = MutedText
                    )
                )

                // Wallet Label / Alias input
                OutlinedTextField(
                    value = label,
                    onValueChange = onLabelChange,
                    label = { Text("اسم أو وصف المحفظة") },
                    placeholder = { Text("مثال: محفظة فودافون 2، محفظة العمل") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = VodafoneRed) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VodafoneRed,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = VodafoneRed,
                        unfocusedLabelColor = MutedText
                    )
                )

                // Wallet Starting Balance
                OutlinedTextField(
                    value = initialBalance,
                    onValueChange = onInitialBalanceChange,
                    label = { Text("الرصيد المتاح حالياً (الافتتاحي) ج.م") },
                    placeholder = { Text("مثال: 5000") },
                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null, tint = VodafoneRed) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VodafoneRed,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = VodafoneRed,
                        unfocusedLabelColor = MutedText
                    )
                )

                // Custom Daily Limit
                OutlinedTextField(
                    value = dailyLimit,
                    onValueChange = onDailyLimitChange,
                    label = { Text("الحد اليومي للتحويل ج.م لهذه المحفظة") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VodafoneRed,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = VodafoneRed,
                        unfocusedLabelColor = MutedText
                    )
                )

                // Custom Daily Deposit Limit
                OutlinedTextField(
                    value = dailyDepositLimit,
                    onValueChange = onDailyDepositLimitChange,
                    label = { Text("الحد اليومي للإيداع ج.م لهذه المحفظة") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VodafoneRed,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = VodafoneRed,
                        unfocusedLabelColor = MutedText
                    )
                )

                // Custom Monthly Limit
                OutlinedTextField(
                    value = monthlyLimit,
                    onValueChange = onMonthlyLimitChange,
                    label = { Text("الحد الشهري للتحويل ج.م لهذه المحفظة") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VodafoneRed,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = VodafoneRed,
                        unfocusedLabelColor = MutedText
                    )
                )

                // Custom Monthly Deposit Limit
                OutlinedTextField(
                    value = monthlyDepositLimit,
                    onValueChange = onMonthlyDepositLimitChange,
                    label = { Text("الحد الشهري للإيداع ج.م لهذه المحفظة") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VodafoneRed,
                        unfocusedBorderColor = CharcoalBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedLabelColor = VodafoneRed,
                        unfocusedLabelColor = MutedText
                    )
                )

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(containerColor = CharcoalCard),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("إلغاء", color = Color.White)
                    }

                    Button(
                        onClick = onSave,
                        enabled = phoneNumber.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = VodafoneRed),
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("إضافة الرقم", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PinUnlockScreen(
    viewModel: LimitGuardViewModel,
    settings: LimitSettings,
    context: Context
) {
    var codeState by remember { mutableStateOf("") }
    var isRooted by remember { mutableStateOf(false) }
    var isDebugger by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            isRooted = com.example.util.SecurityUtils.isDeviceRooted()
            isDebugger = com.example.util.SecurityUtils.isDebuggerAttached()
        }
    }
    
    val isBlockedByRoot = settings.preventRooted && isRooted
    val isBlockedByDebugger = settings.preventDebugger && isDebugger
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CharcoalBg)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(if (isBlockedByRoot || isBlockedByDebugger) ExceededRed.copy(alpha = 0.2f) else VodafoneRed.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isBlockedByRoot || isBlockedByDebugger) Icons.Default.Warning else Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isBlockedByRoot || isBlockedByDebugger) ExceededRed else VodafoneRed,
                    modifier = Modifier.size(40.dp)
                )
            }
            
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isBlockedByRoot || isBlockedByDebugger) "تم قفل الوصول البرمجي" else "كاشاتى - حارس التحويلات",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = if (isBlockedByRoot) {
                        "كشف نظام حماية مكسور (Root) المتلاعب به في هاتفك. الإجراء مغلق لسلامة بيانات المحافظ."
                    } else if (isBlockedByDebugger) {
                        "تم كشف اتصال مصحح الأخطاء البرمجية (Debugger) بجهازك. تم إلغاء الوصول منعاً لأي تلاعب بالذاكرة."
                    } else {
                        "الوصول مغلق بالرمز السري لمنع التلاعب بالحدود أو العمليات من أي شخص آخر."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            
            if (isBlockedByRoot || isBlockedByDebugger) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = ExceededRed.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ExceededRed),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Text(
                        text = "تم تعطيل ميزة تشغيل التطبيق لوجود خطر أمني على النظام. أزل الـ Root أو افصل مصحح الأخطاء للمتابعة.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ExceededRed,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (i in 0 until 4) {
                        val isFilled = i < codeState.length
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (isFilled) VodafoneLightRed else CharcoalBorder)
                                .border(1.dp, if (isFilled) VodafoneLightRed else MutedText, CircleShape)
                        )
                    }
                }
                
                if (viewModel.pinMessageAr.isNotEmpty()) {
                    Text(
                        text = viewModel.pinMessageAr,
                        color = ExceededRed,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val keys = listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "OK")
                    )
                    
                    for (row in keys) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth(0.85f)
                        ) {
                            for (key in row) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1.5f)
                                        .clip(RoundedCornerShape(40))
                                        .background(if (key == "OK") SafeGreen.copy(alpha = 0.2f) else if (key == "C") ExceededRed.copy(alpha = 0.2f) else CharcoalCard)
                                        .clickable {
                                            if (key == "C") {
                                                if (codeState.isNotEmpty()) {
                                                    codeState = codeState.dropLast(1)
                                                }
                                            } else if (key == "OK") {
                                                if (codeState.length == 4) {
                                                    val success = viewModel.verifyUnlockPin(codeState)
                                                    if (success) {
                                                        Toast.makeText(context, "تم إلغاء القفل بنجاح! 🔓✨", Toast.LENGTH_SHORT).show()
                                                    } else {
                                                        codeState = ""
                                                    }
                                                } else {
                                                    viewModel.pinMessageAr = "الرجاء إدخال 4 أرقام للرمز السري."
                                                }
                                            } else {
                                                if (codeState.length < 4) {
                                                    codeState += key
                                                    viewModel.pinMessageAr = ""
                                                    if (codeState.length == 4) {
                                                        val success = viewModel.verifyUnlockPin(codeState)
                                                        if (success) {
                                                            Toast.makeText(context, "تم إلغاء القفل بنجاح! 🔓✨", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            codeState = ""
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (key == "OK") SafeGreen else if (key == "C") ExceededRed else Color.White
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
fun PinSetupDialog(
    showDialog: Boolean,
    isAlreadyEnabled: Boolean,
    firstPin: String,
    secondPin: String,
    onFirstPinChange: (String) -> Unit,
    onSecondPinChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onEnable: (String) -> Unit,
    onDisable: () -> Unit
) {
    if (!showDialog) return
    var errorMessage by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CharcoalBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isAlreadyEnabled) "إيقاف رمز الحماية" else "إعداد قفل التلاعب بالرقم السري",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White)
                    }
                }
                
                HorizontalDivider(color = CharcoalBorder)
                
                if (isAlreadyEnabled) {
                    Text(
                        text = "التطبيق محمي حالياً برمز سري. هل تريد إزالة الحماية تماماً؟ هاتف غير مقفل قد يسمح بالتلاعب في الحدود اليومية كود USSD.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                        textAlign = TextAlign.Center
                    )
                    
                    Button(
                        onClick = onDisable,
                        colors = ButtonDefaults.buttonColors(containerColor = ExceededRed),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("إيقاف الحماية وإلغاء الرقم السري", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Text(
                        text = "ادخل الرمز السري المكون من 4 أرقام لحماية التطبيق من العبث أو تعديل الحدود والعمليات المالية.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                        textAlign = TextAlign.Center
                    )
                    
                    OutlinedTextField(
                        value = firstPin,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) onFirstPinChange(it) },
                        label = { Text("الرمز السري الجديد (4 أرقام)", style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = VodafoneRed,
                            unfocusedBorderColor = CharcoalBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = secondPin,
                        onValueChange = { if (it.length <= 4 && it.all { char -> char.isDigit() }) onSecondPinChange(it) },
                        label = { Text("تأكيد الرمز السري", style = MaterialTheme.typography.labelSmall) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = VodafoneRed,
                            unfocusedBorderColor = CharcoalBorder
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    if (errorMessage.isNotEmpty()) {
                        Text(errorMessage, color = ExceededRed, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    
                    Button(
                        onClick = {
                            if (firstPin.length != 4) {
                                errorMessage = "يجب أن يكون الرمز السري مكوناً من 4 أركان."
                            } else if (firstPin != secondPin) {
                                errorMessage = "الرموز المدخلة غير متطابقة."
                            } else {
                                errorMessage = ""
                                onEnable(firstPin)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("تفعيل وحفظ الرقم السري", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun SecuritySettingsDialog(
    settings: LimitSettings,
    showDialog: Boolean,
    onDismiss: () -> Unit,
    onToggleRoot: (Boolean) -> Unit,
    onToggleDebugger: (Boolean) -> Unit,
    onSetupPinClick: () -> Unit,
    context: Context
) {
    if (!showDialog) return
    
    var isRooted by remember { mutableStateOf(false) }
    var isDebugger by remember { mutableStateOf(false) }
    var isEmulator by remember { mutableStateOf(false) }
    var isOriginal by remember { mutableStateOf(true) }
    var signatureHash by remember { mutableStateOf("N/A") }
    
    LaunchedEffect(showDialog) {
        if (showDialog) {
            withContext(Dispatchers.IO) {
                isRooted = com.example.util.SecurityUtils.isDeviceRooted()
                isDebugger = com.example.util.SecurityUtils.isDebuggerAttached()
                isEmulator = com.example.util.SecurityUtils.isRunningOnEmulator()
                val packageStatus = com.example.util.SecurityUtils.checkPackageTampering(context)
                isOriginal = packageStatus["isOriginal"] as? Boolean ?: true
                signatureHash = packageStatus["signatureHash"] as? String ?: "N/A"
            }
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CharcoalBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .heightIn(max = 580.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = VodafoneLightRed, modifier = Modifier.size(24.dp))
                        Text(
                            text = "مركز حماية التطبيق والأمان",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                }
                
                HorizontalDivider(color = CharcoalBorder)
                
                Text(
                    text = "سياسات الحماية وسلامة العمليات",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = VodafoneRed
                )
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("قفل التطبيق برقم سري (PIN)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                if (settings.isPinLockEnabled) "مفعل ومحمى بترميز SHA-256" else "غير مفعّل. ننصح بتفعيله للحماية من العبث الفعلي بالهاتف.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText
                            )
                        }
                        Button(
                            onClick = onSetupPinClick,
                            colors = ButtonDefaults.buttonColors(containerColor = if (settings.isPinLockEnabled) ExceededRed else SafeGreen),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                        ) {
                            Text(if (settings.isPinLockEnabled) "إيقاف / تعديل" else "إعداد وتفعيل", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        }
                    }
                }
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("حظر الأجهزة مكسورة الحماية (Root)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("يمنع تشغيل التطبيق على أجهزة روت لمنع قراءة الذاكرة وقواعد البينات برمجياً.", style = MaterialTheme.typography.labelSmall, color = MutedText)
                        }
                        Switch(
                            checked = settings.preventRooted,
                            onCheckedChange = onToggleRoot,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VodafoneLightRed,
                                checkedTrackColor = VodafoneRed.copy(alpha = 0.5f),
                                uncheckedThumbColor = MutedText,
                                uncheckedTrackColor = CharcoalBorder
                            )
                        )
                    }
                }
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = CharcoalCard),
                    border = BorderStroke(1.dp, CharcoalBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("حظر مصحح الأخطاء (Anti-Debug)", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("يقوم بقفل التطبيق فوراً إذا تم كشف اتصال IDE أو أداة تصحيح أخطاء لمنع تلاعب الذاكرة.", style = MaterialTheme.typography.labelSmall, color = MutedText)
                        }
                        Switch(
                            checked = settings.preventDebugger,
                            onCheckedChange = onToggleDebugger,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = VodafoneLightRed,
                                checkedTrackColor = VodafoneRed.copy(alpha = 0.5f),
                                uncheckedThumbColor = MutedText,
                                uncheckedTrackColor = CharcoalBorder
                            )
                        )
                    }
                }
                
                HorizontalDivider(color = CharcoalBorder.copy(alpha = 0.5f))
                
                Text(
                    text = "تقرير الفحص الفوري لسلامة التطبيق (Real-Time Security Audit)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = SafeGreen
                )
                
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("حالة نظام الهاتف (Root status)", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        Text(
                            text = if (isRooted) "مكسور الحماية ⚠️ (Rooted)" else "بيئة النظام أمنة وسليمة ✅ (Secure)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isRooted) ExceededRed else SafeGreen
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("مصحح الأخطاء (Debugger status)", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        Text(
                            text = if (isDebugger) "مكشوف / متصل ⚠️ (Connected)" else "غير متصل وسليم ✅ (Disconnected)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isDebugger) ExceededRed else SafeGreen
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("نوع منصة التشغيل (Platform type)", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        Text(
                            text = if (isEmulator) "بيئة افتراضية ℹ️ (Emulator)" else "جهاز حقيقي سليم ✅ (Real Device)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isEmulator) WarningOrange else SafeGreen
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("سلامة حزمة البرمجيات (App Signature)", style = MaterialTheme.typography.labelSmall, color = Color.White)
                        Text(
                            text = if (isOriginal) "توقيع أصلي سليم ✅ (Original)" else "توقيع معدل / تلاعب ⚠️ (Tempered)",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isOriginal) SafeGreen else ExceededRed
                        )
                    }
                    
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CharcoalCard.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, CharcoalBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            Text("بصمة توقيع الحزمة الحالية (SHA-256 Hash):", style = MaterialTheme.typography.labelSmall, color = MutedText)
                            Text(
                                text = signatureHash.take(48) + "...",
                                style = MaterialTheme.typography.labelSmall,
                                color = VodafoneLightRed,
                                fontWeight = FontWeight.Bold,
                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WelcomeDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, CharcoalBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Greeting / Developer Visual Indicator
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(VodafoneRed.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "رسالة ترحيبية",
                        tint = VodafoneLightRed,
                        modifier = Modifier.size(36.dp)
                    )
                }

                Text(
                    text = "مرحباً بك في كاشاتي ✨",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                HorizontalDivider(color = CharcoalBorder, thickness = 1.dp)

                Text(
                    text = "مرحبا بك في برنامجي المتواضع  اسال الله ان يعلمنا ويعلمكم\nوان يجعل فيه نفع للناس \n\nتنفيذ وتطوير  مهندس صبري السيد \nللتواصل. 01020303230",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = SafeGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("welcome_dismiss_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "ابدأ الاستخدام 🚀",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun ThemeSelectorDialog(
    activeThemeId: Int,
    onThemeSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = CharcoalSurface),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, CharcoalBorder),
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = VodafoneRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "مظهر وألوان التطبيق 🎨",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Text(
                    text = "اختر السمة المفضلة لديك للمعاينة والتطبيق الفوري للواجهة:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )

                // List of Themes
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Smart Wallet Auto Theme (ID = 3)
                    val isAutoActive = activeThemeId == 3
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isAutoActive) VodafoneRed.copy(alpha = 0.12f) else CharcoalCard)
                            .border(
                                width = if (isAutoActive) 2.dp else 1.dp,
                                color = if (isAutoActive) VodafoneRed else CharcoalBorder,
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onThemeSelect(3) }
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy((-6).dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFE60000))
                                            .border(1.dp, Color.White, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFF6600))
                                            .border(1.dp, Color.White, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF7FBA00))
                                            .border(1.dp, Color.White, CircleShape)
                                    )
                                }

                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "الربط الذكي بنوع المحفظة ⚡",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(SafeGreen.copy(alpha = 0.2f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                "تلقائي",
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.sp, fontWeight = FontWeight.Bold),
                                                color = SafeGreen
                                            )
                                        }
                                    }
                                    Text(
                                        text = "يتغير مظهر ولون التطبيق ليتطابق مع شركة ونظام المحفظة النشطة حالياً تلقائياً",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MutedText
                                    )
                                }
                            }

                            if (isAutoActive) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "نشط",
                                    tint = VodafoneRed,
                                    modifier = Modifier.size(22.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .border(1.5.dp, CharcoalBorder, CircleShape)
                                )
                            }
                        }
                    }

                    AppThemesList.forEach { theme ->
                        val isActive = theme.id == activeThemeId
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isActive) theme.primary.copy(alpha = 0.12f) else CharcoalCard)
                                .border(
                                    width = if (isActive) 2.dp else 1.dp,
                                    color = if (isActive) theme.primary else CharcoalBorder,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { onThemeSelect(theme.id) }
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // Visual color representation circles
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy((-6).dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(theme.primary)
                                                .border(1.dp, Color.White, CircleShape)
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(theme.background)
                                                .border(1.dp, Color.White, CircleShape)
                                        )
                                    }

                                    Column {
                                        Text(
                                            text = theme.nameAr,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        Text(
                                            text = when (theme.id) {
                                                0 -> "تصميم فودافون الكلاسيكي الأنيق"
                                                1 -> "تصميم غامق فخم بلمسات ذهبية عصرية"
                                                else -> "ألوان الفيروز الهادئة مع درجات الرمادي الداكن المريحة للعين والتصميم المعاصر"
                                            },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MutedText
                                        )
                                    }
                                }

                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "نشط",
                                        tint = theme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .border(1.5.dp, CharcoalBorder, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                // Close Button
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = VodafoneRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "إغلاق نافذة المظهر",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
