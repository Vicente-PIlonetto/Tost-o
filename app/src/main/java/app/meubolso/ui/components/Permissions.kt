package app.meubolso.ui.components

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import app.meubolso.notify.DailyReminder
import app.meubolso.notify.Notifications

/**
 * Pede a permissão de notificações (Android 13+) só quando o usuário liga algo que precisa dela.
 * Retorna uma função para chamar nesse momento.
 */
@Composable
fun rememberNotificationPermission(onResult: (Boolean) -> Unit = {}): () -> Unit {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult(it) }
    return {
        if (Notifications.canPost(ctx)) onResult(true)
        else if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else onResult(false)
    }
}

/** Abre a tela do sistema para permitir alarmes exatos (lembrete diário no horário certo). */
fun requestExactAlarms(ctx: android.content.Context) {
    if (Build.VERSION.SDK_INT >= 31 && !DailyReminder.canExact(ctx)) {
        runCatching {
            ctx.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:" + ctx.packageName))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
