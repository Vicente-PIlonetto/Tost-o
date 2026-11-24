package app.meubolso.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.meubolso.core.Dates
import app.meubolso.core.Money
import app.meubolso.data.db.PaymentMethod
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Campo de valor "estilo banco": cada dígito empurra para os centavos. */
@Composable
fun MoneyField(cents: Long, onChange: (Long) -> Unit, label: String, modifier: Modifier = Modifier, isError: Boolean = false) {
    val text = Money.formatNumber(cents)
    OutlinedTextField(
        value = TextFieldValue(text, TextRange(text.length)),
        onValueChange = { onChange(Money.fromDigits(it.text)) },
        modifier = modifier, label = { Text(label) }, prefix = { Text("R$ ") }, singleLine = true, isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}

@Composable
fun DateChip(date: LocalDate, onChange: (LocalDate) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    AssistChip(onClick = { open = true }, label = { Text(Dates.format(date)) },
        leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) }, modifier = modifier)
    if (open) {
        val st = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    st.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        ) { DatePicker(st) }
    }
}

@Composable
fun PaymentChips(selected: PaymentMethod?, options: List<PaymentMethod>, onSelect: (PaymentMethod?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { pm ->
            FilterChip(selected == pm, { onSelect(if (selected == pm) null else pm) }, { Text(pm.label) })
        }
    }
}

/** Formas de pagamento padrão de uma conta fixa (a carteira sai daqui na hora de pagar). */
val BasicPaymentMethods = PaymentMethod.entries.toList()
