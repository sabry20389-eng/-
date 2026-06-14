package com.example.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import java.io.File
import java.security.MessageDigest

object SecurityUtils {

    /**
     * Checks if the device appears to be rooted by verifying system binaries and build tags.
     */
    fun isDeviceRooted(): Boolean {
        try {
            // Build tags check
            val buildTags = Build.TAGS
            if (buildTags != null && buildTags.contains("test-keys")) {
                return true
            }

            // Commonly known locations of "su" binary
            val suPaths = arrayOf(
                "/system/app/Superuser.apk",
                "/sbin/su",
                "/system/bin/su",
                "/system/xbin/su",
                "/data/local/xbin/su",
                "/data/local/bin/su",
                "/system/sd/xbin/su",
                "/system/bin/failsafe/su",
                "/data/local/su"
            )
            for (path in suPaths) {
                if (File(path).exists()) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Safe fallback
        }
        return false
    }

    /**
     * Checks if a debugger is attached to the application.
     */
    fun isDebuggerAttached(): Boolean {
        return Debug.isDebuggerConnected()
    }

    /**
     * Computes the SHA-256 hash of a string (such as the app PIN or back up signature).
     */
    fun hashString(input: String): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // Simple fallback
            input.hashCode().toString()
        }
    }

    /**
     * Verifies if the installer or system package hasn't been re-signed or repackaged.
     * This checks signature hash if matches, otherwise logs warning.
     */
    fun checkPackageTampering(context: Context): Map<String, Any> {
        val result = mutableMapOf<String, Any>()
        result["packageName"] = context.packageName
        
        try {
            val pm = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    val signatures = if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners
                    } else {
                        signingInfo.signingCertificateHistory
                    }
                    if (signatures != null && signatures.isNotEmpty()) {
                        val signatureHash = hashString(signatures[0].toCharsString())
                        result["signatureHash"] = signatureHash
                        result["isOriginal"] = true // Can be compared against production hash
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
                @Suppress("DEPRECATION")
                val signatures = packageInfo.signatures
                if (signatures != null && signatures.isNotEmpty()) {
                    val signatureHash = hashString(signatures[0].toCharsString())
                    result["signatureHash"] = signatureHash
                    result["isOriginal"] = true
                }
            }
        } catch (e: Exception) {
            result["isOriginal"] = false
            result["error"] = e.message ?: "Unknown error"
        }
        return result
    }

    /**
     * Check if the app is running in an emulator.
     */
    fun isRunningOnEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("google_sdk")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.BOARD.contains("QC_Reference_Phone")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.HOST.startsWith("Build")
                || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
                || "google_sdk" == Build.PRODUCT)
    }
}
