package app.meubolso.notify

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.meubolso.container
import java.util.concurrent.TimeUnit

/** Roda a cada ~3 horas verificando contas a vencer e entradas a confirmar. */
class ReminderWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        Reminders(applicationContext, applicationContext.container).runChecks()
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "reminders"
        private const val NOW = "reminders-now"

        fun schedule(ctx: Context) {
            val req = PeriodicWorkRequestBuilder<ReminderWorker>(3, TimeUnit.HOURS, 1, TimeUnit.HOURS).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, req)
        }

        /** Checagem imediata (ex.: depois de cadastrar uma conta que vence hoje). */
        fun runNow(ctx: Context) {
            WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, OneTimeWorkRequestBuilder<ReminderWorker>().build())
        }
    }
}
