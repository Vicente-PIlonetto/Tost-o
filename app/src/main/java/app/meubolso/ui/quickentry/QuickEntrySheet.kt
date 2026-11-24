package app.meubolso.ui.quickentry

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import app.meubolso.ui.components.WalletPicker
import app.meubolso.ui.components.Motion
import app.meubolso.ui.components.bounceClick
import app.meubolso.ui.components.enterUp
import app.meubolso.ui.components.shake
import app.meubolso.ui.theme.MoneyColors
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meubolso.core.Dates
import app.meubolso.core.Money
import app.meubolso.data.db.TxType
import app.meubolso.ui.components.CategoryBadge
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Folha de lançamento rápido: valor + toque na categoria = salvo. */
@Composable
fun QuickEntrySheet(vm: QuickEntryViewModel, onDismiss: () -> Unit, onSaved: (String) -> Unit, onDelete: ((app.meubolso.data.db.Transaction) -> Unit)? = null) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        QuickEntryContent(vm, onSaved = onSaved, onDelete = onDelete)
    }
}

@Composable
fun QuickEntryContent(
    vm: QuickEntryViewModel,
    onSaved: (String) -> Unit,
    modifier: Modifier = Modifier,
    onDelete: ((app.meubolso.data.db.Transaction) -> Unit)? = null,
) {
    val s by vm.state.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    var showDatePicker by remember { mutableStateOf(false) }

    // Campo "estilo banco": cada dígito empurra para os centavos.
    val display = "R$ " + Money.formatNumber(s.amount)
    val tfv = TextFieldValue(display, selection = TextRange(display.length))

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val haptics = LocalHapticFeedback.current
    val amountColor by animateColorAsState(
        when {
            s.amount == 0L -> MaterialTheme.colorScheme.onSurfaceVariant
            s.type == TxType.INCOME -> MoneyColors.income
            else -> MaterialTheme.colorScheme.onSurface
        }, label = "amountColor",
    )
    // "Pulinho" no valor a cada dígito digitado.
    val pulse = remember { Animatable(1f) }
    LaunchedEffect(s.amount) {
        if (s.amount == 0L) return@LaunchedEffect
        pulse.snapTo(1.06f); pulse.animateTo(1f, Motion.bouncy())
    }
    LaunchedEffect(s.errorTick) { if (s.errorTick > 0) haptics.performHapticFeedback(HapticFeedbackType.Reject) }

    Column(modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp)) {
        val editing = s.editing
        if (editing != null && onDelete != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Editando lançamento", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                TextButton(onClick = { onDelete(editing) }) {
                    Icon(Icons.Filled.DeleteOutline, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("Excluir", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(TxType.EXPENSE to "Saída", TxType.INCOME to "Entrada").forEachIndexed { i, (t, label) ->
                SegmentedButton(
                    selected = s.type == t, onClick = { vm.setType(t) },
                    shape = SegmentedButtonDefaults.itemShape(i, 2),
                ) { Text(label) }
            }
        }
        TextField(
            value = tfv,
            onValueChange = { vm.setAmount(Money.fromDigits(it.text)) },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).focusRequester(focus)
                .shake(s.errorTick)
                .graphicsLayer { scaleX = pulse.value; scaleY = pulse.value },
            textStyle = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Black, textAlign = TextAlign.Center, fontSize = 40.sp, color = amountColor,
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
            ),
            supportingText = s.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
        )

        // De onde sai (ou para onde entra) o dinheiro.
        WalletPicker(s.payment, vm::setPayment, if (s.type == TxType.EXPENSE) s.amount else 0, Modifier.padding(bottom = 4.dp))

        // Linha "mais detalhes"
        Row(
            Modifier.fillMaxWidth().clickable { vm.toggleDetails() }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val summary = buildList {
                add(if (s.date == LocalDate.now()) "Hoje" else Dates.format(s.date))
                if (s.description.isNotBlank()) add(s.description)
            }.joinToString(" · ")
            Text("Mais detalhes", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(summary, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val rot by animateFloatAsState(if (s.showDetails) 180f else 0f, Motion.bouncy(), label = "chev")
            Icon(Icons.Filled.ExpandMore, null, Modifier.rotate(rot))
        }
        AnimatedVisibility(
            s.showDetails,
            enter = expandVertically(Motion.soft()) + fadeIn(),
            exit = shrinkVertically(Motion.soft()) + fadeOut(),
        ) { Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text(Dates.format(s.date)) },
                    leadingIcon = { Icon(Icons.Filled.CalendarMonth, null) },
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = s.description, onValueChange = vm::setDescription,
                    modifier = Modifier.weight(1f), singleLine = true,
                    placeholder = { Text("Descrição (opcional)") },
                )
            }
        } }

        Text(
            if (s.editing != null) "Editando — toque na categoria para salvar" else "Toque na categoria para salvar",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            itemsIndexed(cats, key = { _, c -> c.id }) { i, cat ->
                val selected = s.categoryId == cat.id
                val bg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, label = "tileBg",
                )
                Box(
                    Modifier.animateItem().enterUp(i).clip(RoundedCornerShape(16.dp)).background(bg)
                        .bounceClick(scaleDown = 0.86f) {
                            if (s.amount > 0) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                            vm.saveWith(cat.id, onSaved)
                        },
                ) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        CategoryBadge(cat.icon, cat.color, 44.dp)
                        Text(cat.name, style = MaterialTheme.typography.labelSmall, maxLines = 2,
                            textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        Spacer(Modifier.padding(bottom = 8.dp))
    }

    if (showDatePicker) {
        val dps = rememberDatePickerState(
            initialSelectedDateMillis = s.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    dps.selectedDateMillis?.let { vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") } },
        ) { DatePicker(dps) }
    }
}
