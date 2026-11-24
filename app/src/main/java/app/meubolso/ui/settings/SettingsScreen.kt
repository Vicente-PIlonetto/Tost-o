package app.meubolso.ui.settings

import androidx.compose.material.icons.filled.AccountBalanceWallet
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meubolso.notify.DailyReminder
import app.meubolso.security.AppLock
import androidx.compose.material.icons.filled.Fingerprint
import app.meubolso.ui.components.SectionTitle
import app.meubolso.ui.components.rememberNotificationPermission
import app.meubolso.ui.components.requestExactAlarms

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    contentPadding: PaddingValues,
    onCategories: () -> Unit,
    onDeleted: () -> Unit,
    onSavings: () -> Unit,
    onSections: () -> Unit,
    onWallets: () -> Unit,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val sampleCount by vm.sampleCount.collectAsStateWithLifecycle()
    val deletedCount by vm.deletedCount.collectAsStateWithLifecycle()
    val s = settings ?: return
    val ctx = LocalContext.current
    var dayMenu by remember { mutableStateOf(false) }
    var confirmSamples by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }

    // Liga a função e, se precisar, pede a permissão de notificações nesse momento.
    var pendingEnable by remember { mutableStateOf<(() -> Unit)?>(null) }
    val askPermission = rememberNotificationPermission { granted -> if (granted) pendingEnable?.invoke(); pendingEnable = null }
    fun enableWithPermission(action: () -> Unit) { pendingEnable = action; askPermission() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(contentPadding)) {
        SectionTitle("Organização")
        NavItem("Carteiras", "Conta, cartão de crédito, vale e dinheiro: limites, fatura e recarga", Icons.Filled.AccountBalanceWallet, onWallets)
        NavItem("Categorias", "Criar, editar, reordenar, limites mensais e arquivar", Icons.Filled.Category, onCategories)
        Box {
            ListItem(
                headlineContent = { Text("Início do mês financeiro") },
                supportingContent = {
                    Text(if (s.monthStartDay == 1) "Dia 1 (mês do calendário)"
                    else "Dia ${s.monthStartDay} — o mês vai do dia ${s.monthStartDay} até a véspera no mês seguinte")
                },
                leadingContent = { Icon(Icons.Filled.CalendarMonth, null) },
                modifier = Modifier.clickable { dayMenu = true },
            )
            DropdownMenu(dayMenu, { dayMenu = false }) {
                (1..28).forEach { d -> DropdownMenuItem({ Text("Dia $d") }, { vm.setMonthStartDay(d); dayMenu = false }) }
            }
        }
        NavItem("Seções do mercado", "Ordem das seções no percurso e itens do catálogo", Icons.Filled.Storefront, onSections)
        NavItem("Poupança", "Saldo guardado, meta e histórico", Icons.Filled.Savings, onSavings)

        HorizontalDivider()
        SectionTitle("Notificações")
        ToggleItem("Lembretes de contas", "Avisa antes do vencimento, com botão \"Marcar como paga\"", Icons.Filled.EventRepeat, s.notifyBills) {
            if (it) enableWithPermission { vm.setNotifyBills(true) } else vm.setNotifyBills(false)
        }
        ToggleItem("Confirmação de entradas", "No dia do salário: \"O salário caiu?\"", Icons.Filled.Payments, s.notifyIncome) {
            if (it) enableWithPermission { vm.setNotifyIncome(true) } else vm.setNotifyIncome(false)
        }
        ToggleItem("Alerta de limite", "Quando uma categoria chega a 80% e 100% do limite", Icons.Filled.Speed, s.notifyLimits) {
            if (it) enableWithPermission { vm.setNotifyLimits(true) } else vm.setNotifyLimits(false)
        }
        ToggleItem("Lembrete diário", "\"Lançou seus gastos de hoje?\"", Icons.Filled.EditNote, s.dailyReminder) {
            if (it) enableWithPermission {
                vm.setDaily(true, s.dailyHour, s.dailyMinute)
                requestExactAlarms(ctx)
            } else vm.setDaily(false, s.dailyHour, s.dailyMinute)
        }
        if (s.dailyReminder) {
            ListItem(
                headlineContent = { Text("Horário do lembrete") },
                supportingContent = {
                    Text("%02d:%02d".format(s.dailyHour, s.dailyMinute) +
                        if (Build.VERSION.SDK_INT >= 31 && !DailyReminder.canExact(ctx)) " · pode atrasar alguns minutos (toque para permitir horário exato)" else "")
                },
                leadingContent = { Icon(Icons.Filled.AccessTime, null) },
                modifier = Modifier.clickable { pickTime = true; requestExactAlarms(ctx) },
            )
        }

        HorizontalDivider()
        SectionTitle("Segurança")
        val activity = ctx as? androidx.fragment.app.FragmentActivity
        ToggleItem(
            "Bloquear com digital ou senha",
            if (AppLock.isAvailable(ctx)) "Pede a digital ou a senha do celular ao abrir (e após 1 min fora do app)"
            else "Configure um bloqueio de tela no celular para usar",
            Icons.Filled.Fingerprint, s.appLock, enabled = AppLock.isAvailable(ctx) || s.appLock,
        ) { on ->
            // Confirma com a digital/senha antes de ligar ou desligar.
            if (activity != null) AppLock.authenticate(activity, if (on) "Ativar bloqueio" else "Desativar bloqueio",
                onSuccess = { vm.setAppLock(on) })
        }

        HorizontalDivider()
        SectionTitle("Aparência")
        ToggleItem(
            "Cores do papel de parede",
            if (Build.VERSION.SDK_INT >= 31) "Desligado: usa o verde do Tostão" else "Disponível a partir do Android 12",
            Icons.Filled.Palette, s.dynamicColor, enabled = Build.VERSION.SDK_INT >= 31, onChange = vm::setDynamicColor,
        )

        HorizontalDivider()
        SectionTitle("Dados")
        NavItem("Lançamentos excluídos", if (deletedCount > 0) "$deletedCount registro(s), com a justificativa" else "Nada excluído",
            Icons.Filled.History, onDeleted)
        ListItem(
            headlineContent = { Text("Apagar dados de exemplo") },
            supportingContent = { Text(if (sampleCount > 0) "$sampleCount lançamentos de exemplo (e as contas de exemplo)" else "Não há dados de exemplo") },
            leadingContent = { Icon(Icons.Filled.DeleteSweep, null) },
            modifier = if (sampleCount > 0) Modifier.clickable { confirmSamples = true } else Modifier,
        )
    }

    if (confirmSamples) AlertDialog(
        onDismissRequest = { confirmSamples = false },
        title = { Text("Apagar dados de exemplo?") },
        text = { Text("Só os lançamentos, contas e entradas de exemplo serão removidos. O que você lançou continua.") },
        confirmButton = { TextButton(onClick = { vm.deleteSamples(); confirmSamples = false }) { Text("Apagar") } },
        dismissButton = { TextButton(onClick = { confirmSamples = false }) { Text("Cancelar") } },
    )
    if (pickTime) {
        val tp = rememberTimePickerState(s.dailyHour, s.dailyMinute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Horário do lembrete") },
            text = { TimePicker(tp) },
            confirmButton = { TextButton(onClick = { vm.setDaily(true, tp.hour, tp.minute); pickTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun NavItem(title: String, subtitle: String, icon: ImageVector, onClick: () -> Unit) = ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(subtitle) },
    leadingContent = { Icon(icon, null) },
    trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
    modifier = Modifier.clickable(onClick = onClick),
)

@Composable
private fun ToggleItem(
    title: String, subtitle: String, icon: ImageVector, checked: Boolean,
    enabled: Boolean = true, onChange: (Boolean) -> Unit,
) = ListItem(
    headlineContent = { Text(title) },
    supportingContent = { Text(subtitle) },
    leadingContent = { Icon(icon, null) },
    trailingContent = { Switch(checked, onChange, enabled = enabled) },
    modifier = Modifier.clickable(enabled = enabled) { onChange(!checked) },
)
