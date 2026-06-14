package com.example.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.LimitSettings
import com.example.data.model.Transaction
import com.example.data.model.Wallet
import com.example.data.repository.LimitRepository
import java.util.Calendar
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import com.example.util.SecurityUtils
import android.content.Context

class LimitGuardViewModel(private val repository: LimitRepository) : ViewModel() {

    // App Lock & Tamper Protection parameters
    var isAppUnlocked by mutableStateOf(false)
        private set
    var showSecuritySettingsDialog by mutableStateOf(false)
    var showPinSetupDialog by mutableStateOf(false)
    var showPinUnlockScreen by mutableStateOf(false)

    // PIN Inputs
    var firstPinInput by mutableStateOf("")
    var secondPinInput by mutableStateOf("")
    var unlockPinInput by mutableStateOf("")
    var pinMessageAr by mutableStateOf("")

    // Inputs for adding a transaction
    var recipientNumber by mutableStateOf("")
        private set
    var amountText by mutableStateOf("")
        private set
    var transactionNotes by mutableStateOf("")
        private set
    var isDepositInput by mutableStateOf(false) // NEW State for deposit vs withdrawal
        private set
    var transactionTimestamp by mutableStateOf(System.currentTimeMillis())
        private set

    // Settings inputs
    var dailyLimitInput by mutableStateOf("")
    var monthlyLimitInput by mutableStateOf("")
    var dailyDepositLimitInput by mutableStateOf("")
    var monthlyDepositLimitInput by mutableStateOf("")
    var warningPercentageInput by mutableStateOf(0.8f)
    var isSettingsOpen by mutableStateOf(false)

    // Search & Filter
    var searchQuery by mutableStateOf("")
    var filterPeriod by mutableStateOf(FilterPeriod.ALL)

    enum class FilterPeriod { ALL, TODAY, THIS_MONTH }

    // Wallets / Accounts Setup
    var selectedWalletNumber by mutableStateOf("")
        private set

    // Add Wallet dialogue inputs
    var walletNumberInput by mutableStateOf("")
        private set
    var walletLabelInput by mutableStateOf("")
        private set
    var walletDailyLimitInput by mutableStateOf("60000")
        private set
    var walletMonthlyLimitInput by mutableStateOf("200000")
        private set
    var walletDailyDepositLimitInput by mutableStateOf("60000")
        private set
    var walletMonthlyDepositLimitInput by mutableStateOf("200000")
        private set
    var walletInitialBalanceInput by mutableStateOf("0") // NEW! Wallet initial starting balance
    var isAddWalletDialogOpen by mutableStateOf(false)

    // Repository states
    val allTransactions: StateFlow<List<Transaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settings: StateFlow<LimitSettings> = repository.settings
        .map { it ?: LimitSettings() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LimitSettings())

    // Wallet Balances maps each phoneNumber -> its current real balance
    val walletBalances: StateFlow<Map<String, Double>> = combine(
        allTransactions,
        repository.allWallets
    ) { transactionsList, walletsList ->
        val resultMap = mutableMapOf<String, Double>()
        val transactionsByWallet = transactionsList.groupBy { it.senderWalletNumber }
        
        walletsList.forEach { wallet ->
            val initial = wallet.initialBalance
            val txs = transactionsByWallet[wallet.phoneNumber] ?: emptyList()
            var current = initial
            txs.forEach { tx ->
                if (tx.isDeposit) {
                    current += tx.amount
                } else {
                    current -= tx.amount
                }
            }
            resultMap[wallet.phoneNumber] = current
        }
        resultMap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val allWallets: StateFlow<List<Wallet>> = combine(
        repository.allWallets,
        walletBalances
    ) { walletsList, balancesMap ->
        walletsList.sortedWith(
            compareByDescending<Wallet> { (balancesMap[it.phoneNumber] ?: 0.0) > 0 }
                .thenByDescending { balancesMap[it.phoneNumber] ?: 0.0 }
                .thenBy { it.label }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active wallet flow
    val activeWallet: StateFlow<Wallet?> = combine(
        allWallets,
        snapshotFlow { selectedWalletNumber }
    ) { wallets, num ->
        wallets.find { it.phoneNumber == num } ?: wallets.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun getWalletBalanceAtStartOfMonth(
        wallet: Wallet?,
        allTxs: List<Transaction>
    ): Double {
        val walletNum = wallet?.phoneNumber ?: ""
        if (walletNum.isEmpty()) return 0.0
        val initial = wallet?.initialBalance ?: 0.0
        val startOfThisMonth = getStartOfThisMonth()
        
        // Filter transactions before this month
        val prevTxs = allTxs.filter {
            it.timestamp < startOfThisMonth && it.senderWalletNumber == walletNum
        }
        
        var current = initial
        prevTxs.forEach { tx ->
            if (tx.isDeposit) {
                current += tx.amount
            } else {
                current -= tx.amount
            }
        }
        return current.coerceAtLeast(0.0)
    }

    val carriedOverTransferLimit: StateFlow<Double> = MutableStateFlow(0.0)

    val carriedOverDepositLimit: StateFlow<Double> = combine(
        allTransactions,
        activeWallet
    ) { list, wallet ->
        getWalletBalanceAtStartOfMonth(wallet, list)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // UI state derived from transactions and settings (only withdrawal counts toward outbound transfer limits)
    val spentToday: StateFlow<Double> = combine(
        allTransactions,
        activeWallet
    ) { list, wallet ->
        val startOfToday = getStartOfToday()
        val walletNum = wallet?.phoneNumber ?: ""
        list.filter { 
            it.timestamp >= startOfToday && (it.senderWalletNumber == walletNum || walletNum.isEmpty()) && !it.isDeposit
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val spentThisMonth: StateFlow<Double> = combine(
        allTransactions,
        activeWallet
    ) { list, wallet ->
        val startOfThisMonth = getStartOfThisMonth()
        val walletNum = wallet?.phoneNumber ?: ""
        list.filter { 
            it.timestamp >= startOfThisMonth && (it.senderWalletNumber == walletNum || walletNum.isEmpty()) && !it.isDeposit
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val depositedToday: StateFlow<Double> = combine(
        allTransactions,
        activeWallet
    ) { list, wallet ->
        val startOfToday = getStartOfToday()
        val walletNum = wallet?.phoneNumber ?: ""
        list.filter { 
            it.timestamp >= startOfToday && (it.senderWalletNumber == walletNum || walletNum.isEmpty()) && it.isDeposit
        }.sumOf { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val depositedThisMonth: StateFlow<Double> = combine(
        allTransactions,
        activeWallet,
        carriedOverDepositLimit
    ) { list, wallet, extraDeposit ->
        val startOfThisMonth = getStartOfThisMonth()
        val walletNum = wallet?.phoneNumber ?: ""
        val currentMonthDeposits = list.filter { 
            it.timestamp >= startOfThisMonth && (it.senderWalletNumber == walletNum || walletNum.isEmpty()) && it.isDeposit
        }.sumOf { it.amount }
        currentMonthDeposits + extraDeposit
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Sequential running balances after each transaction for every wallet
    val balancesAfterTransactions: StateFlow<Map<Int, Double>> = combine(
        allTransactions,
        allWallets
    ) { transactionsList, walletsList ->
        val resultMap = mutableMapOf<Int, Double>()
        val transactionsByWallet = transactionsList.groupBy { it.senderWalletNumber }
        
        walletsList.forEach { wallet ->
            val initial = wallet.initialBalance
            // Sort ascending by timestamp to calculate sequential running balance
            val sortedTxs = (transactionsByWallet[wallet.phoneNumber] ?: emptyList())
                .sortedBy { it.timestamp }
            
            var current = initial
            sortedTxs.forEach { tx ->
                if (tx.isDeposit) {
                    current += tx.amount
                } else {
                    current -= tx.amount
                }
                resultMap[tx.id] = current
            }
        }
        
        // Handling fallback / legacy untracked keys
        val fallbackKeys = transactionsByWallet.keys.filter { key -> walletsList.none { it.phoneNumber == key } }
        fallbackKeys.forEach { key ->
            val sortedTxs = (transactionsByWallet[key] ?: emptyList()).sortedBy { it.timestamp }
            var current = 0.0
            sortedTxs.forEach { tx ->
                if (tx.isDeposit) {
                    current += tx.amount
                } else {
                    current -= tx.amount
                }
                resultMap[tx.id] = current
            }
        }
        
        resultMap
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())


    // Combined Total Balance for all wallets active
    val totalBalance: StateFlow<Double> = walletBalances.map { map ->
        map.values.sum()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Warnings computed dynamically based on current spend plus current inputs (only if withdrawal is selected)
    val liveWarning: StateFlow<LiveWarningState> = combine(
        spentToday,
        spentThisMonth,
        settings,
        activeWallet,
        snapshotFlow { Pair(amountText, isDepositInput) }
    ) { spentTodayVal, spentMonthVal, currentSettings, wallet, inputsPair ->
        val (inputAmt, isDep) = inputsPair
        if (isDep) {
            LiveWarningState(level = LiveWarningLevel.NONE, message = "")
        } else {
            val amt = inputAmt.toDoubleOrNull() ?: 0.0
            val targetToday = spentTodayVal + amt
            val targetMonth = spentMonthVal + amt

            val todayLimit = wallet?.dailyLimit ?: currentSettings.dailyLimit
            val monthBaseLimit = wallet?.monthlyLimit ?: currentSettings.monthlyLimit

            val monthLimit = monthBaseLimit
            val warningRatio = currentSettings.warningPercentage

            var reason = ""
            var level = LiveWarningLevel.NONE

            if (targetToday > todayLimit) {
                level = LiveWarningLevel.CRITICAL
                reason = "تنبيه: هذا المبلغ يتجاوز الحد اليومي للتحويل (${todayLimit.toInt()} جنيه)!"
            } else if (targetMonth > monthLimit) {
                level = LiveWarningLevel.CRITICAL
                reason = "تنبيه: هذا المبلغ يتجاوز الحد الشهري للتحويل (${monthLimit.toInt()} جنيه)!"
            } else if (targetToday >= todayLimit * warningRatio) {
                level = LiveWarningLevel.WARNING
                reason = "تنبيه: هذا المبلغ يقترب من الحد اليومي المسموح به (${(todayLimit * warningRatio).toInt()} جنيه)."
            } else if (targetMonth >= monthLimit * warningRatio) {
                level = LiveWarningLevel.WARNING
                reason = "تنبيه: هذا المبلغ يقترب من الحد الشهري المسموح به (${(monthLimit * warningRatio).toInt()} جنيه)."
            }

            LiveWarningState(level = level, message = reason)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LiveWarningState())

    init {
        // Seed default settings on start if empty
        viewModelScope.launch {
            var currentSettings = repository.settings.first()
            if (currentSettings == null) {
                currentSettings = LimitSettings()
                repository.updateSettings(currentSettings)
            }

            // Check security settings & initialize lock state
            if (currentSettings.isPinLockEnabled && currentSettings.pinHash.isNotEmpty()) {
                isAppUnlocked = false
                showPinUnlockScreen = true
            } else {
                isAppUnlocked = true
                showPinUnlockScreen = false
            }

            // Seed seed default wallet if empty
            val wallets = repository.allWallets.first()
            if (wallets.isEmpty()) {
                repository.insertWallet(
                    Wallet(
                        phoneNumber = "01000000000",
                        label = "المحفظة الافتراضية",
                        dailyLimit = 60000.0,
                        monthlyLimit = 200000.0,
                        initialBalance = 0.0
                    )
                )
            }
        }
    }

    fun selectWallet(num: String) {
        selectedWalletNumber = num
    }

    fun onPhoneNumberChange(phone: String) {
        // Sanitize to only numbers
        if (phone.all { it.isDigit() }) {
            recipientNumber = phone
        }
    }

    fun onAmountChange(amount: String) {
        if (amount.isEmpty() || amount.toDoubleOrNull() != null) {
            amountText = amount
        }
    }

    fun onNotesChange(notes: String) {
        transactionNotes = notes
    }

    fun setDepositMode(value: Boolean) {
        isDepositInput = value
    }

    fun onTimestampChange(time: Long) {
        transactionTimestamp = time
    }

    fun addTransaction() {
        val amt = amountText.toDoubleOrNull() ?: return
        if (amt <= 0) return

        viewModelScope.launch {
            val wallet = activeWallet.value
            val senderNum = wallet?.phoneNumber ?: "01000000000"
            val transaction = Transaction(
                amount = amt,
                recipientNumber = if (isDepositInput) "شحن / إيداع" else recipientNumber.ifEmpty { "تحويل فودافون كاش" },
                senderWalletNumber = senderNum,
                timestamp = transactionTimestamp,
                notes = transactionNotes,
                isDeposit = isDepositInput
            )
            repository.insertTransaction(transaction)
            
            // Clean inputs upon success
            recipientNumber = ""
            amountText = ""
            transactionNotes = ""
            isDepositInput = false // Reset toggle to withdrawal (default)
            transactionTimestamp = System.currentTimeMillis()
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clearAllTransactions()
        }
    }

    fun openSettings() {
        val wallet = activeWallet.value
        val currentSettings = settings.value
        
        dailyLimitInput = (wallet?.dailyLimit ?: currentSettings.dailyLimit).toInt().toString()
        monthlyLimitInput = (wallet?.monthlyLimit ?: currentSettings.monthlyLimit).toInt().toString()
        dailyDepositLimitInput = (wallet?.dailyDepositLimit ?: currentSettings.dailyDepositLimit).toInt().toString()
        monthlyDepositLimitInput = (wallet?.monthlyDepositLimit ?: currentSettings.monthlyDepositLimit).toInt().toString()
        walletInitialBalanceInput = (wallet?.initialBalance ?: 0.0).toInt().toString()
        warningPercentageInput = currentSettings.warningPercentage
        isSettingsOpen = true
    }

    fun saveSettings() {
        val daily = dailyLimitInput.toDoubleOrNull() ?: 60000.0
        val monthly = monthlyLimitInput.toDoubleOrNull() ?: 200000.0
        val dailyDep = dailyDepositLimitInput.toDoubleOrNull() ?: 60000.0
        val monthlyDep = monthlyDepositLimitInput.toDoubleOrNull() ?: 200000.0
        val initial = walletInitialBalanceInput.toDoubleOrNull() ?: 0.0
        val p = warningPercentageInput

        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(
                current.copy(
                    dailyLimit = daily,
                    monthlyLimit = monthly,
                    dailyDepositLimit = dailyDep,
                    monthlyDepositLimit = monthlyDep,
                    warningPercentage = p
                )
            )

            val wallet = activeWallet.value
            if (wallet != null) {
                repository.insertWallet(
                    wallet.copy(
                        dailyLimit = daily,
                        monthlyLimit = monthly,
                        dailyDepositLimit = dailyDep,
                        monthlyDepositLimit = monthlyDep,
                        initialBalance = initial
                    )
                )
            }
            isSettingsOpen = false
        }
    }

    fun verifyUnlockPin(pin: String): Boolean {
        val hashedAttempt = SecurityUtils.hashString(pin)
        val currentHash = settings.value.pinHash
        return if (hashedAttempt == currentHash) {
            isAppUnlocked = true
            showPinUnlockScreen = false
            unlockPinInput = ""
            pinMessageAr = ""
            true
        } else {
            pinMessageAr = "الرمز السري غير صحيح. حاول مجدداً!"
            false
        }
    }

    fun setupPinAndEnable(pin: String) {
        val hashedPin = SecurityUtils.hashString(pin)
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(
                current.copy(
                    isPinLockEnabled = true,
                    pinHash = hashedPin
                )
            )
            isAppUnlocked = true
            showPinSetupDialog = false
            firstPinInput = ""
            secondPinInput = ""
        }
    }

    fun disablePinLock() {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(
                current.copy(
                    isPinLockEnabled = false,
                    pinHash = ""
                )
            )
            isAppUnlocked = true
            showPinSetupDialog = false
            firstPinInput = ""
            secondPinInput = ""
        }
    }

    fun setPreventRooted(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(current.copy(preventRooted = enabled))
        }
    }

    fun setPreventDebugger(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(current.copy(preventDebugger = enabled))
        }
    }

    fun lockApp() {
        if (settings.value.isPinLockEnabled) {
            isAppUnlocked = false
            showPinUnlockScreen = true
        }
    }

    fun getUssdCode(): String {
        val amt = amountText.toDoubleOrNull() ?: 0.0
        val phone = recipientNumber
        if (phone.isEmpty() || amt <= 0) return "*9*7#"
        return "*9*7*$phone*${amt.toInt()}#"
    }

    // Wallet Inputs Handlers
    fun onWalletNumberChange(value: String) {
        if (value.all { it.isDigit() }) {
            walletNumberInput = value
        }
    }

    fun onWalletLabelChange(value: String) {
        walletLabelInput = value
    }

    fun onWalletDailyLimitChange(value: String) {
        if (value.all { it.isDigit() }) {
            walletDailyLimitInput = value
        }
    }

    fun onWalletMonthlyLimitChange(value: String) {
        if (value.all { it.isDigit() }) {
            walletMonthlyLimitInput = value
        }
    }

    fun onWalletDailyDepositLimitChange(value: String) {
        if (value.all { it.isDigit() }) {
            walletDailyDepositLimitInput = value
        }
    }

    fun onWalletMonthlyDepositLimitChange(value: String) {
        if (value.all { it.isDigit() }) {
            walletMonthlyDepositLimitInput = value
        }
    }

    fun onWalletInitialBalanceChange(value: String) {
        if (value.isEmpty() || value.all { it.isDigit() }) {
            walletInitialBalanceInput = value
        }
    }

    fun openAddWalletDialog() {
        walletNumberInput = ""
        walletLabelInput = ""
        walletDailyLimitInput = "60000"
        walletMonthlyLimitInput = "200000"
        walletDailyDepositLimitInput = "60000"
        walletMonthlyDepositLimitInput = "200000"
        walletInitialBalanceInput = "0"
        isAddWalletDialogOpen = true
    }

    fun addNewWallet() {
        if (walletNumberInput.isEmpty()) return
        val daily = walletDailyLimitInput.toDoubleOrNull() ?: 60000.0
        val monthly = walletMonthlyLimitInput.toDoubleOrNull() ?: 200000.0
        val dailyDep = walletDailyDepositLimitInput.toDoubleOrNull() ?: 60000.0
        val monthlyDep = walletMonthlyDepositLimitInput.toDoubleOrNull() ?: 200000.0
        val initial = walletInitialBalanceInput.toDoubleOrNull() ?: 0.0
        val label = walletLabelInput.ifEmpty { "محفظة $walletNumberInput" }

        viewModelScope.launch {
            val wallet = Wallet(
                phoneNumber = walletNumberInput,
                label = label,
                dailyLimit = daily,
                monthlyLimit = monthly,
                dailyDepositLimit = dailyDep,
                monthlyDepositLimit = monthlyDep,
                initialBalance = initial
            )
            repository.insertWallet(wallet)
            selectedWalletNumber = wallet.phoneNumber // switch to new wallet automatically
            isAddWalletDialogOpen = false
        }
    }

    fun deleteWallet(wallet: Wallet) {
        viewModelScope.launch {
            repository.deleteWallet(wallet)
        }
    }

    suspend fun exportBackupToJSON(): String {
        val transactionsList = repository.allTransactions.first()
        val walletsList = repository.allWallets.first()
        val currentSettings = repository.settings.first() ?: LimitSettings()

        val backupObj = JSONObject()
        backupObj.put("backup_version", 2)
        backupObj.put("backup_date", System.currentTimeMillis())

        // Settings
        val settingsObj = JSONObject()
        settingsObj.put("dailyLimit", currentSettings.dailyLimit)
        settingsObj.put("monthlyLimit", currentSettings.monthlyLimit)
        settingsObj.put("dailyDepositLimit", currentSettings.dailyDepositLimit)
        settingsObj.put("monthlyDepositLimit", currentSettings.monthlyDepositLimit)
        settingsObj.put("warningPercentage", currentSettings.warningPercentage.toDouble())
        settingsObj.put("isPinLockEnabled", currentSettings.isPinLockEnabled)
        settingsObj.put("pinHash", currentSettings.pinHash)
        settingsObj.put("preventRooted", currentSettings.preventRooted)
        settingsObj.put("preventDebugger", currentSettings.preventDebugger)
        backupObj.put("settings", settingsObj)

        // Wallets
        val walletsArr = JSONArray()
        for (wallet in walletsList) {
            val wObj = JSONObject()
            wObj.put("phoneNumber", wallet.phoneNumber)
            wObj.put("label", wallet.label)
            wObj.put("dailyLimit", wallet.dailyLimit)
            wObj.put("monthlyLimit", wallet.monthlyLimit)
            wObj.put("dailyDepositLimit", wallet.dailyDepositLimit)
            wObj.put("monthlyDepositLimit", wallet.monthlyDepositLimit)
            wObj.put("initialBalance", wallet.initialBalance)
            walletsArr.put(wObj)
        }
        backupObj.put("wallets", walletsArr)

        // Transactions
        val transArr = JSONArray()
        for (trans in transactionsList) {
            val tObj = JSONObject()
            tObj.put("id", trans.id)
            tObj.put("amount", trans.amount)
            tObj.put("recipientNumber", trans.recipientNumber)
            tObj.put("senderWalletNumber", trans.senderWalletNumber)
            tObj.put("timestamp", trans.timestamp)
            tObj.put("notes", trans.notes)
            tObj.put("isDeposit", trans.isDeposit)
            transArr.put(tObj)
        }
        backupObj.put("transactions", transArr)

        return backupObj.toString(4)
    }

    suspend fun importBackupFromJSON(jsonString: String): Boolean {
        return try {
            val backupObj = JSONObject(jsonString)
            
            // 1. Restore Settings
            if (backupObj.has("settings")) {
                val sObj = backupObj.getJSONObject("settings")
                val settings = LimitSettings(
                    id = 1,
                    dailyLimit = sObj.optDouble("dailyLimit", 60000.0),
                    monthlyLimit = sObj.optDouble("monthlyLimit", 200000.0),
                    dailyDepositLimit = sObj.optDouble("dailyDepositLimit", 60000.0),
                    monthlyDepositLimit = sObj.optDouble("monthlyDepositLimit", 200000.0),
                    warningPercentage = sObj.optDouble("warningPercentage", 0.8).toFloat(),
                    isPinLockEnabled = sObj.optBoolean("isPinLockEnabled", false),
                    pinHash = sObj.optString("pinHash", ""),
                    preventRooted = sObj.optBoolean("preventRooted", false),
                    preventDebugger = sObj.optBoolean("preventDebugger", false)
                )
                repository.updateSettings(settings)
            }

            // 2. Clear & Restore Wallets
            if (backupObj.has("wallets")) {
                val walletsArr = backupObj.getJSONArray("wallets")
                repository.clearAllWallets()
                for (i in 0 until walletsArr.length()) {
                    val wObj = walletsArr.getJSONObject(i)
                    val wallet = Wallet(
                        phoneNumber = wObj.getString("phoneNumber"),
                        label = wObj.getString("label"),
                        dailyLimit = wObj.optDouble("dailyLimit", 60000.0),
                        monthlyLimit = wObj.optDouble("monthlyLimit", 200000.0),
                        dailyDepositLimit = wObj.optDouble("dailyDepositLimit", 60000.0),
                        monthlyDepositLimit = wObj.optDouble("monthlyDepositLimit", 200000.0),
                        initialBalance = wObj.optDouble("initialBalance", 0.0)
                    )
                    repository.insertWallet(wallet)
                }
            }

            // 3. Clear & Restore Transactions
            if (backupObj.has("transactions")) {
                val transArr = backupObj.getJSONArray("transactions")
                repository.clearAllTransactions()
                for (i in 0 until transArr.length()) {
                    val tObj = transArr.getJSONObject(i)
                    val trans = Transaction(
                        id = tObj.optInt("id", 0),
                        amount = tObj.getDouble("amount"),
                        recipientNumber = tObj.getString("recipientNumber"),
                        senderWalletNumber = tObj.optString("senderWalletNumber", "الافتراضية"),
                        timestamp = tObj.getLong("timestamp"),
                        notes = tObj.optString("notes", ""),
                        isDeposit = tObj.optBoolean("isDeposit", false)
                    )
                    repository.insertTransaction(trans)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun getStartOfToday(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun getStartOfThisMonth(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}

enum class LiveWarningLevel { NONE, WARNING, CRITICAL }

data class RolloverResult(
    val carryoverTransfer: Double,
    val carryoverDeposit: Double
)

data class LiveWarningState(
    val level: LiveWarningLevel = LiveWarningLevel.NONE,
    val message: String = ""
)

class LimitGuardViewModelFactory(private val repository: LimitRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LimitGuardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LimitGuardViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
