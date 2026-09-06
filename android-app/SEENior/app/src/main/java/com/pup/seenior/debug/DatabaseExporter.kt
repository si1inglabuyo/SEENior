package com.pup.seenior.debug

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.FileProvider
import com.pup.seenior.database.SeniorAppDatabase
import java.io.File

/**
 * DEBUG-ONLY. Lets the app hand its own local database to the OS share sheet, entirely on-device
 * -- no computer, no `adb run-as`, no root. Exists solely so the database can be pulled off a
 * phone that only the senior (or whoever is testing) has physically in hand.
 *
 * Delete this whole file, the "Export Database" button in HomeScreen.kt's SimulationRow, the
 * <provider> entry in AndroidManifest.xml, and res/xml/file_paths.xml before shipping to a real
 * senior's phone. All four are tagged DEBUG-ONLY with the same instruction so a single search for
 * that phrase finds every piece.
 *
 * Deliberately not a privacy-boundary concern under CLAUDE.md 11 in the way a network upload would
 * be: nothing here leaves the device on its own. It only ever hands a local file to the share
 * sheet the phone's holder explicitly picks an app from -- the same trust boundary as attaching a
 * photo to an email.
 */
object DatabaseExporter {

    private const val TAG = "DatabaseExporter"
    private const val DB_FILE_NAME = "senior_app.db"
    private const val EXPORT_FILE_NAME = "sampledb.db"

    /**
     * Checkpoints the WAL into the main file, copies it to a location FileProvider is allowed to
     * expose, and launches the share sheet. Returns false (and logs) on any failure -- there is
     * nothing more to tell the caller than "it didn't work", since this never runs on a path any
     * real senior depends on.
     */
    fun export(context: Context, db: SeniorAppDatabase): Boolean {
        return try {
            // Room writes recent rows to the WAL file first. Without this, a copy of the main
            // .db file alone can be missing whatever hasn't been checkpointed back yet -- the
            // same gap that corrupted the first adb-based pull attempt of this same database.
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL);").close()

            val source = context.getDatabasePath(DB_FILE_NAME)
            val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
            val exported = File(exportDir, EXPORT_FILE_NAME)
            source.copyTo(exported, overwrite = true)

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                exported
            )

            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                // Launched from a ViewModel via the application context, not an Activity --
                // required for any activity-launching intent to start from that context.
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Export SEENior database").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
            true
        } catch (e: Exception) {
            Log.w(TAG, "database export failed", e)
            false
        }
    }
}
