package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

var VodafoneRed by mutableStateOf(Color(0xFFE60000))
var VodafoneDarkRed by mutableStateOf(Color(0xFFB30000))
var VodafoneLightRed by mutableStateOf(Color(0xFFFF3333))

var CharcoalBg by mutableStateOf(Color(0xFF110E0E))
var CharcoalSurface by mutableStateOf(Color(0xFF1A1515))
var CharcoalCard by mutableStateOf(Color(0xFF211D1D))
var CharcoalBorder by mutableStateOf(Color(0xFF2F2929))

var SafeGreen by mutableStateOf(Color(0xFF2ECC71))
var WarningOrange by mutableStateOf(Color(0xFFF39C12))
var ExceededRed by mutableStateOf(Color(0xFFE74C3C))

val White = Color(0xFFFFFFFF)
var DynamicWhite by mutableStateOf(Color(0xFFFFFFFF))
var MutedText by mutableStateOf(Color(0xFF9E9292))
val LightGray = Color(0xFFF5F5F5)

data class AppThemeData(
    val id: Int,
    val nameAr: String,
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val background: Color,
    val surface: Color,
    val cardBg: Color,
    val cardBorder: Color,
    val safeGreen: Color,
    val warningOrange: Color,
    val errorColor: Color,
    val mutedText: Color,
)

val AppThemesList = listOf(
    AppThemeData(
        id = 0,
        nameAr = "كلاسيك (أحمر فودافون)",
        primary = Color(0xFFE60000),
        primaryDark = Color(0xFFB30000),
        primaryLight = Color(0xFFFF3333),
        background = Color(0xFF110E0E),
        surface = Color(0xFF1A1515),
        cardBg = Color(0xFF211D1D),
        cardBorder = Color(0xFF2F2929),
        safeGreen = Color(0xFF2ECC71),
        warningOrange = Color(0xFFF39C12),
        errorColor = Color(0xFFE74C3C),
        mutedText = Color(0xFF9E9292)
    ),
    AppThemeData(
        id = 1,
        nameAr = "الكربون الفخم والذهب",
        primary = Color(0xFFD4AF37),
        primaryDark = Color(0xFFAA8822),
        primaryLight = Color(0xFFF3E5AB),
        background = Color(0xFF111111),
        surface = Color(0xFF1A1A1A),
        cardBg = Color(0xFF262626),
        cardBorder = Color(0xFF383838),
        safeGreen = Color(0xFF2ECC71),
        warningOrange = Color(0xFFE67E22),
        errorColor = Color(0xFFC0392B),
        mutedText = Color(0xFF9E9E9E)
    ),
    AppThemeData(
        id = 2,
        nameAr = "الفيروز الداكن والرمادي الهادئ (مريح للعين)",
        primary = Color(0xFF14B8A6), // Soft modern Teal
        primaryDark = Color(0xFF0F766E),
        primaryLight = Color(0xFFCCFBF1),
        background = Color(0xFF121824), // Soothing Midnight Slate/Grey
        surface = Color(0xFF1B2332), // Elegant dark slate surface
        cardBg = Color(0xFF232D40), // Calm deep gray card
        cardBorder = Color(0xFF2D3C54), // Subtle slate border
        safeGreen = Color(0xFF10B981),
        warningOrange = Color(0xFFF59E0B),
        errorColor = Color(0xFFEF4444),
        mutedText = Color(0xFF8E9CB2)
    )
)

fun getBrandTheme(label: String, phoneNumber: String, isDark: Boolean): AppThemeData {
    val lbl = label.lowercase()
    val num = phoneNumber.trim()
    return when {
        lbl.contains("فودافون") || lbl.contains("vodafone") || lbl.contains("voda") || num.startsWith("010") -> {
            if (isDark) {
                AppThemeData(
                    id = 0, nameAr = "فودافون كاش", primary = Color(0xFFE60000), primaryDark = Color(0xFFB30000), primaryLight = Color(0xFFFF3333),
                    background = Color(0xFF110E0E), surface = Color(0xFF1A1515), cardBg = Color(0xFF211D1D), cardBorder = Color(0xFF2F2929),
                    safeGreen = Color(0xFF2ECC71), warningOrange = Color(0xFFF39C12), errorColor = Color(0xFFE74C3C), mutedText = Color(0xFF9E9292)
                )
            } else {
                AppThemeData(
                    id = 0, nameAr = "فودافون كاش", primary = Color(0xFFD20000), primaryDark = Color(0xFF990000), primaryLight = Color(0xFFFFECEC),
                    background = Color(0xFFFAF2F2), surface = Color(0xFFF4ECEC), cardBg = Color(0xFFEDE0E0), cardBorder = Color(0xFFDFCDCD),
                    safeGreen = Color(0xFF16A34A), warningOrange = Color(0xFFD97706), errorColor = Color(0xFFDC2626), mutedText = Color(0xFF7F6A6A)
                )
            }
        }
        lbl.contains("أورنج") || lbl.contains("اورنج") || lbl.contains("orange") || lbl.contains("موبينيل") || lbl.contains("mobinil") || num.startsWith("012") -> {
            if (isDark) {
                AppThemeData(
                    id = 10, nameAr = "أورنج كاش", primary = Color(0xFFFF6600), primaryDark = Color(0xFFCC5200), primaryLight = Color(0xFFFF8833),
                    background = Color(0xFF12100E), surface = Color(0xFF1C1815), cardBg = Color(0xFF241F1C), cardBorder = Color(0xFF332A25),
                    safeGreen = Color(0xFF2ECC71), warningOrange = Color(0xFFE67E22), errorColor = Color(0xFFC0392B), mutedText = Color(0xFF9E9085)
                )
            } else {
                AppThemeData(
                    id = 10, nameAr = "أورنج كاش", primary = Color(0xFFFF6600), primaryDark = Color(0xFFCC5200), primaryLight = Color(0xFFFFF2E6),
                    background = Color(0xFFFAF4EE), surface = Color(0xFFF4EBE2), cardBg = Color(0xFFECDFD3), cardBorder = Color(0xFFDECDBE),
                    safeGreen = Color(0xFF16A34A), warningOrange = Color(0xFFD97706), errorColor = Color(0xFFDC2626), mutedText = Color(0xFF7F6E60)
                )
            }
        }
        lbl.contains("اتصالات") || lbl.contains("etisalat") || lbl.contains("et") || num.startsWith("011") -> {
            if (isDark) {
                AppThemeData(
                    id = 11, nameAr = "اتصالات كاش", primary = Color(0xFF7FBA00), primaryDark = Color(0xFF5A8E00), primaryLight = Color(0xFFA6E25F),
                    background = Color(0xFF0F120E), surface = Color(0xFF151B14), cardBg = Color(0xFF1C241B), cardBorder = Color(0xFF283626),
                    safeGreen = Color(0xFF2ECC71), warningOrange = Color(0xFFF39C12), errorColor = Color(0xFFE74C3C), mutedText = Color(0xFF8C9E85)
                )
            } else {
                AppThemeData(
                    id = 11, nameAr = "اتصالات كاش", primary = Color(0xFF7FBA00), primaryDark = Color(0xFF5A8E00), primaryLight = Color(0xFFF4FBEB),
                    background = Color(0xFFF3F7F2), surface = Color(0xFFE9EFE7), cardBg = Color(0xFFDEE7DB), cardBorder = Color(0xFFCEDBC9),
                    safeGreen = Color(0xFF16A34A), warningOrange = Color(0xFFD97706), errorColor = Color(0xFFDC2626), mutedText = Color(0xFF63705C)
                )
            }
        }
        lbl.contains("وي") || lbl.contains("we") || lbl.contains("المصرية") || num.startsWith("015") -> {
            if (isDark) {
                AppThemeData(
                    id = 12, nameAr = "وي باي", primary = Color(0xFFBA1B7A), primaryDark = Color(0xFF5E134C), primaryLight = Color(0xFFDF53B5),
                    background = Color(0xFF120E12), surface = Color(0xFF1A141B), cardBg = Color(0xFF231C24), cardBorder = Color(0xFF322533),
                    safeGreen = Color(0xFF2ECC71), warningOrange = Color(0xFFF39C12), errorColor = Color(0xFFE74C3C), mutedText = Color(0xFF9E859A)
                )
            } else {
                AppThemeData(
                    id = 12, nameAr = "وي باي", primary = Color(0xFFBA1B7A), primaryDark = Color(0xFF5E134C), primaryLight = Color(0xFFFDF2FA),
                    background = Color(0xFFF7F2F7), surface = Color(0xFFECE2EC), cardBg = Color(0xFFE1D1E1), cardBorder = Color(0xFFD2BED2),
                    safeGreen = Color(0xFF16A34A), warningOrange = Color(0xFFD97706), errorColor = Color(0xFFDC2626), mutedText = Color(0xFF7F637C)
                )
            }
        }
        lbl.contains("إنستا") || lbl.contains("انستا") || lbl.contains("instapay") || lbl.contains("insta") || lbl.contains("بنك") || lbl.contains("bank") -> {
            if (isDark) {
                AppThemeData(
                    id = 13, nameAr = "إنستاباي / بنك", primary = Color(0xFF8B5CF6), primaryDark = Color(0xFF6D28D9), primaryLight = Color(0xFFA78BFA),
                    background = Color(0xFF0F0E17), surface = Color(0xFF161524), cardBg = Color(0xFF1E1D33), cardBorder = Color(0xFF2B2947),
                    safeGreen = Color(0xFF2ECC71), warningOrange = Color(0xFFF39C12), errorColor = Color(0xFFE74C3C), mutedText = Color(0xFF8C8AA1)
                )
            } else {
                AppThemeData(
                    id = 13, nameAr = "إنستاباي / بنك", primary = Color(0xFF8B5CF6), primaryDark = Color(0xFF6D28D9), primaryLight = Color(0xFFF5F3FF),
                    background = Color(0xFFF5F4FA), surface = Color(0xFFEAE7F5), cardBg = Color(0xFFDDD8F0), cardBorder = Color(0xFFCDC5E9),
                    safeGreen = Color(0xFF16A34A), warningOrange = Color(0xFFD97706), errorColor = Color(0xFFDC2626), mutedText = Color(0xFF6D6A85)
                )
            }
        }
        else -> {
            if (isDark) {
                AppThemeData(
                    id = 2, nameAr = "كاشاتي", primary = Color(0xFF14B8A6), primaryDark = Color(0xFF0F766E), primaryLight = Color(0xFFCCFBF1),
                    background = Color(0xFF121824), surface = Color(0xFF1B2332), cardBg = Color(0xFF232D40), cardBorder = Color(0xFF2D3C54),
                    safeGreen = Color(0xFF2ECC71), warningOrange = Color(0xFFF39C12), errorColor = Color(0xFFE74C3C), mutedText = Color(0xFF8E9CB2)
                )
            } else {
                AppThemeData(
                    id = 2, nameAr = "كاشاتي", primary = Color(0xFF0D9488), primaryDark = Color(0xFF115E59), primaryLight = Color(0xFFF0FDFA),
                    background = Color(0xFFF2F5F8), surface = Color(0xFFE7EDF3), cardBg = Color(0xFFDCE4EE), cardBorder = Color(0xFFCBD6E4),
                    safeGreen = Color(0xFF10B981), warningOrange = Color(0xFFF59E0B), errorColor = Color(0xFFEF4444), mutedText = Color(0xFF64748B)
                )
            }
        }
    }
}

fun applyThemeById(themeId: Int, isDark: Boolean = true, activeWalletLabel: String? = null, activeWalletNumber: String? = null) {
    if (themeId == 3) {
        val theme = getBrandTheme(activeWalletLabel ?: "", activeWalletNumber ?: "", isDark)
        VodafoneRed = theme.primary
        VodafoneDarkRed = theme.primaryDark
        VodafoneLightRed = theme.primaryLight
        CharcoalBg = theme.background
        CharcoalSurface = theme.surface
        CharcoalCard = theme.cardBg
        CharcoalBorder = theme.cardBorder
        SafeGreen = theme.safeGreen
        WarningOrange = theme.warningOrange
        ExceededRed = theme.errorColor
        MutedText = theme.mutedText
        DynamicWhite = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1E293B)
        return
    }
    if (isDark) {
        val theme = AppThemesList.find { it.id == themeId } ?: AppThemesList[0]
        VodafoneRed = theme.primary
        VodafoneDarkRed = theme.primaryDark
        VodafoneLightRed = theme.primaryLight
        CharcoalBg = theme.background
        CharcoalSurface = theme.surface
        CharcoalCard = theme.cardBg
        CharcoalBorder = theme.cardBorder
        SafeGreen = theme.safeGreen
        WarningOrange = theme.warningOrange
        ExceededRed = theme.errorColor
        MutedText = theme.mutedText
        DynamicWhite = Color(0xFFFFFFFF)
    } else {
        DynamicWhite = Color(0xFF1E293B) // Dark charcoal gray for superior contrast in Light mode
        // High fidelity Light mode theme adaptation to keep contemporary elegance
        when (themeId) {
            0 -> { // Classic Red (Light Mode)
                VodafoneRed = Color(0xFFD20000)
                VodafoneDarkRed = Color(0xFF990000)
                VodafoneLightRed = Color(0xFFFFECEC)
                CharcoalBg = Color(0xFFFAF2F2)
                CharcoalSurface = Color(0xFFF4ECEC)
                CharcoalCard = Color(0xFFEDE0E0)
                CharcoalBorder = Color(0xFFDFCDCD)
                SafeGreen = Color(0xFF16A34A)
                WarningOrange = Color(0xFFD97706)
                ExceededRed = Color(0xFFDC2626)
                MutedText = Color(0xFF7F6A6A)
            }
            1 -> { // Carbon Luxurious Gold (Light Mode)
                VodafoneRed = Color(0xFFB59422)
                VodafoneDarkRed = Color(0xFF8A6B10)
                VodafoneLightRed = Color(0xFFFEF9E7)
                CharcoalBg = Color(0xFFFAF8F2)
                CharcoalSurface = Color(0xFFF3EFE3)
                CharcoalCard = Color(0xFFE8E2D1)
                CharcoalBorder = Color(0xFFDAD3BF)
                SafeGreen = Color(0xFF16A34A)
                WarningOrange = Color(0xFFD97706)
                ExceededRed = Color(0xFFDC2626)
                MutedText = Color(0xFF7C755E)
            }
            else -> { // Elegant Teal & Soft Slate (Light Mode)
                VodafoneRed = Color(0xFF0D9488) // Soft modern Teal
                VodafoneDarkRed = Color(0xFF115E59)
                VodafoneLightRed = Color(0xFFF0FDFA)
                CharcoalBg = Color(0xFFF2F5F8) // Slate 50
                CharcoalSurface = Color(0xFFE7EDF3) // Slate 100 Card backgrounds
                CharcoalCard = Color(0xFFDCE4EE)
                CharcoalBorder = Color(0xFFCBD6E4)
                SafeGreen = Color(0xFF10B981)
                WarningOrange = Color(0xFFF59E0B)
                ExceededRed = Color(0xFFEF4444)
                MutedText = Color(0xFF64748B)
            }
        }
    }
}
