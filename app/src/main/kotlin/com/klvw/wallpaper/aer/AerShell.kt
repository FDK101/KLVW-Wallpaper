package com.klvw.wallpaper.aer

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.DocumentsContract
import android.widget.Toast

object AerShell {

    fun open(context: Context) {
        if (AerLockStore.isLocked) {
            context.startActivity(
                Intent(context, AerMountActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(AerMountActivity.OPEN_FILES_AFTER_MOUNT, true)
            )
        } else {
            openFilesApp(context)
        }
    }

    fun openFilesApp(context: Context) {
        // Pattern from anemo-aer/LauncherActivity: ACTION_VIEW + buildRootsUri, explicit class targets.
        val aerUri = DocumentsContract.buildRootsUri(AerDocumentsProvider.AUTHORITY)
        val pm = context.packageManager

        val candidates = arrayOf(
            // AOSP DocumentsUI, up to Android 11
            Intent(Intent.ACTION_VIEW, aerUri)
                .setClassName("com.android.documentsui", "com.android.documentsui.files.FilesActivity"),
            Intent(Intent.ACTION_VIEW, aerUri)
                .setClassName("com.android.documentsui", "com.android.documentsui.FilesActivity"),
            // Pixel / Google, Android 12+
            Intent(Intent.ACTION_VIEW, aerUri)
                .setClassName("com.google.android.documentsui", "com.android.documentsui.files.FilesActivity"),
            // Android 13+ generic fallback
            Intent(Intent.ACTION_VIEW, aerUri)
                .setType("vnd.android.document/directory")
                .setFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT or Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP),
        )

        val resolved = candidates.firstOrNull { pm.resolveActivity(it, PackageManager.MATCH_ALL) != null }
        if (resolved != null) {
            context.startActivity(resolved.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            Toast.makeText(context, "No file manager found to open Aer storage", Toast.LENGTH_LONG).show()
        }
    }
}
