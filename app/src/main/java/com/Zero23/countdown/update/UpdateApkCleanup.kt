package com.Zero23.countdown.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Name the release APK is downloaded under; DownloadManager appends "-1", "-2", ... on clashes. */
const val UPDATE_APK_FILE_NAME = "CountDayDown_Update.apk"

private const val UPDATE_APK_BASE_NAME = "CountDayDown_Update"

/**
 * True when [fileName] is a package downloaded by the self-update flow.
 *
 * A prefix / suffix check is used instead of an equality check because DownloadManager renames
 * the file when one with the same destination already exists, e.g. `CountDayDown_Update-1.apk`
 * after an earlier download that was never installed.
 */
internal fun isUpdateApkFileName(fileName: String?): Boolean =
    fileName != null &&
        fileName.startsWith(UPDATE_APK_BASE_NAME) &&
        fileName.endsWith(".apk")

/**
 * Whether a finished update download is no longer needed.
 *
 * A failed download never produced a usable package. A successful one is obsolete once the app
 * was updated after the download finished, i.e. the package it contained has already been
 * installed. A package that finished after the last app update may still be waiting to be
 * installed, so it is kept and the notification keeps working.
 */
internal fun isUpdateDownloadStale(
    failed: Boolean,
    downloadedAt: Long,
    lastUpdateTime: Long
): Boolean = failed || downloadedAt <= lastUpdateTime

/**
 * Deletes the APK left behind by the self-update flow.
 *
 * DownloadManager owns the file in the public Downloads folder, so [DownloadManager.remove] is
 * used: it drops the download record and its data, while a direct `File.delete` is refused for
 * that location under scoped storage. Only downloads of this app are visible to the query.
 *
 * Called once per app start as a fallback and from [UpdateApkCleanupReceiver] right after the
 * new version is installed, so the ~15 MB package does not stay in Downloads forever.
 * Safe to call repeatedly and from a background thread.
 */
fun cleanupUpdateApkDownloads(context: Context) {
    val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager ?: return
    val lastUpdateTime = try {
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
    } catch (e: Exception) {
        e.printStackTrace()
        return
    }

    val staleIds = mutableListOf<Long>()
    try {
        val query = DownloadManager.Query().setFilterByStatus(
            DownloadManager.STATUS_SUCCESSFUL or DownloadManager.STATUS_FAILED
        )
        dm.query(query).use { cursor ->
            if (cursor == null) return
            val idIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
            val uriIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI)
            val modifiedIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)
            val statusIndex = cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            while (cursor.moveToNext()) {
                val localUri = cursor.getString(uriIndex) ?: continue
                val fileName = try {
                    Uri.parse(localUri).lastPathSegment
                } catch (_: Exception) {
                    null
                }
                if (!isUpdateApkFileName(fileName)) continue
                val failed = cursor.getInt(statusIndex) == DownloadManager.STATUS_FAILED
                val downloadedAt = cursor.getLong(modifiedIndex)
                if (isUpdateDownloadStale(failed, downloadedAt, lastUpdateTime)) {
                    staleIds += cursor.getLong(idIndex)
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    staleIds.forEach { id ->
        try {
            // Removes the record and the downloaded APK in one step.
            dm.remove(id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Runs the cleanup as soon as this app has been replaced by a freshly installed version.
 *
 * The installer kills the old process, so the update is only ever noticed by the new version;
 * reacting to the broadcast also covers users who install from the download notification and
 * never open the app afterwards.
 */
class UpdateApkCleanupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        Thread {
            try {
                cleanupUpdateApkDownloads(appContext)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }
}
