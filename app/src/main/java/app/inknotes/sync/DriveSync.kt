package app.inknotes.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.inknotes.InkApplication
import app.inknotes.data.NotesDao
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Optional Google Drive sync. Off until the user turns it on in Settings. All passes run through
 * WorkManager so they survive the app closing and wait for a network connection.
 */
class DriveSync(
    context: Context,
    private val dao: NotesDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val workManager get() = WorkManager.getInstance(appContext)
    private val mutex = Mutex()
    private val networkConstraint = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    val settings = SyncSettings(appContext)
    val auth = DriveAuth(appContext)

    /** True while a sync pass is running. */
    val isSyncing: Flow<Boolean> = combine(
        workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME),
        workManager.getWorkInfosForUniqueWorkFlow(PERIODIC),
    ) { a, b -> (a + b).any { it.state == WorkInfo.State.RUNNING } }

    /** Called once the user granted Drive access in Settings. */
    fun enable(account: String?) {
        settings.enable(account)
        schedulePeriodic()
        requestSync()
    }

    suspend fun disable() {
        val account = settings.current.account
        settings.disable()
        workManager.cancelUniqueWork(ONE_TIME)
        workManager.cancelUniqueWork(PERIODIC)
        // Revoking lets the user cleanly re-consent later; the Drive files themselves stay.
        if (account != null) runCatching { auth.revoke(account) }.onFailure { Log.w(TAG, "Revoke failed", it) }
    }

    /** Queues a sync pass if sync is on. Cheap to call often: passes queue up behind each other. */
    fun requestSync() {
        if (!settings.current.enabled) return
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setConstraints(networkConstraint).build()
        workManager.enqueueUniqueWork(ONE_TIME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    fun schedulePeriodic() {
        if (!settings.current.enabled) return
        val request = PeriodicWorkRequestBuilder<SyncWorker>(PERIOD_HOURS, TimeUnit.HOURS)
            .setConstraints(networkConstraint)
            .build()
        workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    enum class Outcome { SUCCESS, RETRY, FAILED }

    suspend fun syncNow(): Outcome = mutex.withLock {
        val status = settings.current
        if (!status.enabled) return Outcome.SUCCESS
        try {
            var token = auth.tokenWithoutUi(status.account)
                ?: return fail(SyncError.AUTH, Outcome.FAILED)
            try {
                SyncEngine(dao, DriveClient(token)).sync()
            } catch (_: DriveAuthException) {
                // Cached token expired or was revoked; ask for a fresh one once.
                auth.clearToken(token)
                token = auth.tokenWithoutUi(status.account) ?: return fail(SyncError.AUTH, Outcome.FAILED)
                SyncEngine(dao, DriveClient(token)).sync()
            }
            // The user may have turned sync off mid-pass.
            if (settings.current.enabled) settings.recordSuccess(clock())
            Outcome.SUCCESS
        } catch (e: DriveAuthException) {
            fail(SyncError.AUTH, Outcome.FAILED, e)
        } catch (e: DriveHttpException) {
            // 403/429 are usually rate limits; 5xx are transient.
            val retry = e.code == 403 || e.code == 429 || e.code >= 500
            fail(SyncError.OTHER, if (retry) Outcome.RETRY else Outcome.FAILED, e)
        } catch (e: IOException) {
            fail(SyncError.NETWORK, Outcome.RETRY, e)
        } catch (e: ApiException) {
            fail(SyncError.AUTH, Outcome.FAILED, e)
        } catch (e: SerializationException) {
            fail(SyncError.OTHER, Outcome.FAILED, e)
        }
    }

    private fun fail(error: SyncError, outcome: Outcome, cause: Throwable? = null): Outcome {
        Log.w(TAG, "Sync failed: $error", cause)
        if (settings.current.enabled) settings.recordError(error)
        return outcome
    }

    private companion object {
        const val TAG = "InkSync"
        const val ONE_TIME = "ink-sync"
        const val PERIODIC = "ink-sync-periodic"
        const val PERIOD_HOURS = 1L
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        when ((applicationContext as InkApplication).sync.syncNow()) {
            DriveSync.Outcome.SUCCESS -> Result.success()
            DriveSync.Outcome.RETRY -> Result.retry()
            // Success keeps queued passes running; the error is already shown in Settings.
            DriveSync.Outcome.FAILED -> Result.success()
        }
}
