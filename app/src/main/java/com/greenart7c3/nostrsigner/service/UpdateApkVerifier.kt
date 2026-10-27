package com.greenart7c3.nostrsigner.service

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Checks that a downloaded update APK is Amber itself before the system
 * installer is opened: same package name, and a signing certificate shared
 * with the installed app (either side's rotation lineage counts, so a planned
 * key rotation still updates). Without this, a release pointing at a
 * differently packaged look-alike would be offered as an "Amber update".
 */
object UpdateApkVerifier {
    fun isTrustedUpdate(
        installedPackage: String,
        installedSigners: Set<String>,
        archivePackage: String?,
        archiveSigners: Set<String>,
    ): Boolean = archivePackage == installedPackage &&
        installedSigners.isNotEmpty() &&
        archiveSigners.any { it in installedSigners }

    fun isTrustedUpdate(context: Context, apkFile: File): Boolean {
        val pm = context.packageManager
        val archive = archiveInfo(pm, apkFile.absolutePath) ?: return false
        val installed = installedInfo(pm, context.packageName)
        return isTrustedUpdate(context.packageName, signerDigests(installed), archive.packageName, signerDigests(archive))
    }

    private fun archiveInfo(pm: PackageManager, path: String): PackageInfo? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNING_CERTIFICATES)
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES)
    }

    private fun installedInfo(pm: PackageManager, packageName: String): PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
    }

    private fun signerDigests(info: PackageInfo): Set<String> {
        val certs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return certs.orEmpty().map { sha256Hex(it.toByteArray()) }.toSet()
    }

    private fun sha256Hex(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
