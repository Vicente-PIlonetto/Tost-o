package app.meubolso.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import app.meubolso.container
import app.meubolso.core.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId

/** Botões das notificações: "Marcar como paga" e "Sim" (salário caiu). */
class ActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val c = context.container
        val id = intent.getLongExtra(Notifications.EXTRA_ID, -1)
        val period = intent.getStringExtra(Notifications.EXTRA_PERIOD)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (id < 0 || period == null) return@launch
                val msg = when (intent.action) {
                    ACTION_BILL_PAID -> c.bills.getBill(id)?.takeIf { !it.variableAmount }?.let { b ->
                        c.bills.markPaid(b, period, b.amount)
                        Notifications.cancel(context, Notifications.billNotificationId(id))
                        "${b.name} paga: ${Money.format(b.amount)}"
                    }
                    ACTION_INCOME_YES -> c.bills.getIncome(id)?.let { i ->
                        c.bills.confirmIncome(i, period, i.amount)
                        Notifications.cancel(context, Notifications.incomeNotificationId(id))
                        "Entrou! ${Money.format(i.amount)} de ${i.name}"
                    }
                    else -> null
                }
                if (msg != null) withContext(Dispatchers.Main) { Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_BILL_PAID = "app.meubolso.BILL_PAID"
        const val ACTION_INCOME_YES = "app.meubolso.INCOME_YES"
    }
}

/** Lembrete diário "Lançou seus gastos de hoje?" no horário escolhido. */
object DailyReminder {
    private fun intent(ctx: Context) = PendingIntent.getBroadcast(
        ctx, 1, Intent(ctx, DailyReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun canExact(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun schedule(ctx: Context, hour: Int, minute: Int) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val now = LocalDateTime.now()
        var at = LocalDate.now().atTime(LocalTime.of(hour, minute))
        if (!at.isAfter(now)) at = at.plusDays(1)
        val millis = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (canExact(ctx)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, intent(ctx))
        else am.setWindow(AlarmManager.RTC_WAKEUP, millis, 10 * 60 * 1000L, intent(ctx))
    }

    fun cancel(ctx: Context) = ctx.getSystemService(AlarmManager::class.java).cancel(intent(ctx))

    /** Reagenda conforme os ajustes (abertura do app, boot, mudança de ajuste). */
    suspend fun sync(ctx: Context) {
        val s = ctx.container.settings.current()
        if (s.dailyReminder) schedule(ctx, s.dailyHour, s.dailyMinute) else cancel(ctx)
    }
}

class DailyReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val c = context.container
                val s = c.settings.current()
                if (s.dailyReminder) {
                    Notifications.showDaily(context, Reminders(context, c).todayCount())
                    DailyReminder.schedule(context, s.dailyHour, s.dailyMinute)
                }
            } finally { pending.finish() }
        }
    }
}

/** Depois de reiniciar o celular: reagenda o lembrete diário e os lembretes de contas. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                DailyReminder.sync(context)
                ReminderWorker.schedule(context)
            } finally { pending.finish() }
        }
    }
}
