package app.meubolso.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.meubolso.MainActivity
import app.meubolso.R
import app.meubolso.core.Dates
import app.meubolso.core.Money
import app.meubolso.data.db.Bill
import app.meubolso.data.db.Category
import app.meubolso.data.db.RecurringIncome
import java.time.LocalDate
import java.time.YearMonth

object Notifications {
    const val CH_BILLS = "contas"
    const val CH_INCOME = "entradas"
    const val CH_LIMITS = "limites"
    const val CH_DAILY = "diario"

    /** Extras para abrir o app numa ação específica. */
    const val EXTRA_OPEN = "open"
    const val OPEN_QUICK_ENTRY = "quick_entry"
    const val OPEN_BILL = "bill"
    const val OPEN_INCOME = "income"
    const val EXTRA_ID = "id"
    const val EXTRA_PERIOD = "period"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(listOf(
            NotificationChannel(CH_BILLS, "Lembretes de contas", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "Avisos antes do vencimento das contas fixas e assinaturas" },
            NotificationChannel(CH_INCOME, "Confirmação de entradas", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Pergunta se o salário e outras entradas fixas caíram" },
            NotificationChannel(CH_LIMITS, "Alertas de limite", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Quando uma categoria chega a 80% e 100% do limite do mês" },
            NotificationChannel(CH_DAILY, "Lembrete diário", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "\"Lançou seus gastos de hoje?\"" },
        ))
    }

    fun canPost(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    private fun openApp(ctx: Context, requestCode: Int, extras: Intent.() -> Unit = {}): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            extras()
        }
        return PendingIntent.getActivity(ctx, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun action(ctx: Context, requestCode: Int, action: String, id: Long, period: YearMonth): PendingIntent {
        val i = Intent(ctx, ActionReceiver::class.java).apply {
            this.action = action
            putExtra(EXTRA_ID, id); putExtra(EXTRA_PERIOD, period.toString())
        }
        return PendingIntent.getBroadcast(ctx, requestCode, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun billNotificationId(billId: Long) = 10_000 + billId.toInt()
    fun incomeNotificationId(incomeId: Long) = 20_000 + incomeId.toInt()

    fun showBill(ctx: Context, bill: Bill, period: YearMonth, due: LocalDate, today: LocalDate, estimate: Long = bill.amount) {
        if (!canPost(ctx)) return
        val days = due.toEpochDay() - today.toEpochDay()
        val whenText = when {
            days < 0 -> "venceu em ${Dates.formatShort(due)}"
            days == 0L -> "vence hoje"
            days == 1L -> "vence amanhã"
            else -> "vence em $days dias (${Dates.formatShort(due)})"
        }
        val nid = billNotificationId(bill.id)
        val b = NotificationCompat.Builder(ctx, CH_BILLS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${bill.name} $whenText")
            .setContentText(
                if (bill.variableAmount) "Valor variável" + (if (estimate > 0) " (≈ ${Money.format(estimate)})" else "") + " · toque para informar quanto veio"
                else "${Money.format(bill.amount)} · toque para ajustar o valor"
            )
            .setContentIntent(openApp(ctx, nid) { putExtra(EXTRA_OPEN, OPEN_BILL); putExtra(EXTRA_ID, bill.id); putExtra(EXTRA_PERIOD, period.toString()) })
        // Conta variável não tem "marcar como paga" direto: precisa informar o valor.
        if (!bill.variableAmount) b.addAction(0, "Marcar como paga", action(ctx, nid, ActionReceiver.ACTION_BILL_PAID, bill.id, period))
        val n = b
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(nid, n) }
    }

    fun showIncome(ctx: Context, income: RecurringIncome, period: YearMonth) {
        if (!canPost(ctx)) return
        val nid = incomeNotificationId(income.id)
        val n = NotificationCompat.Builder(ctx, CH_INCOME)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("${income.name} caiu?")
            .setContentText("Previsto: ${Money.format(income.amount)}")
            .setContentIntent(openApp(ctx, nid) { putExtra(EXTRA_OPEN, OPEN_INCOME); putExtra(EXTRA_ID, income.id); putExtra(EXTRA_PERIOD, period.toString()) })
            .addAction(0, "Sim", action(ctx, nid, ActionReceiver.ACTION_INCOME_YES, income.id, period))
            .addAction(0, "Valor diferente", openApp(ctx, nid + 50_000) {
                putExtra(EXTRA_OPEN, OPEN_INCOME); putExtra(EXTRA_ID, income.id); putExtra(EXTRA_PERIOD, period.toString())
            })
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(nid, n) }
    }

    fun showLimit(ctx: Context, cat: Category, spent: Long, limit: Long, pct: Int) {
        if (!canPost(ctx)) return
        val title = if (pct >= 100) "Limite de ${cat.name} estourado" else "${cat.name} chegou a $pct% do limite"
        val n = NotificationCompat.Builder(ctx, CH_LIMITS)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText("${Money.format(spent)} de ${Money.format(limit)} neste mês")
            .setContentIntent(openApp(ctx, 30_000 + cat.id.toInt()))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(30_000 + cat.id.toInt(), n) }
    }

    fun showDaily(ctx: Context, countToday: Int) {
        if (!canPost(ctx)) return
        val text = if (countToday == 0) "Nada lançado hoje ainda. Gastou alguma coisa?"
        else "Já são $countToday lançamento${if (countToday > 1) "s" else ""} hoje. Faltou algum?"
        val n = NotificationCompat.Builder(ctx, CH_DAILY)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("Lançou seus gastos de hoje?")
            .setContentText(text)
            .setContentIntent(openApp(ctx, 40_000))
            .addAction(0, "Lançar agora", openApp(ctx, 40_001) { putExtra(EXTRA_OPEN, OPEN_QUICK_ENTRY) })
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify(40_000, n) }
    }

    fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)
}
