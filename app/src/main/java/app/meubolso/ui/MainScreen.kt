package app.meubolso.ui

import androidx.compose.runtime.CompositionLocalProvider
import app.meubolso.ui.components.LocalWallets
import app.meubolso.ui.wallets.WalletsScreen
import app.meubolso.ui.wallets.WalletsViewModel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavBackStackEntry
import app.meubolso.ui.components.Motion
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.meubolso.container
import app.meubolso.BuildConfig
import app.meubolso.data.SampleData
import app.meubolso.data.db.Transaction
import app.meubolso.data.db.TxType
import app.meubolso.ui.bills.BillsScreen
import app.meubolso.ui.bills.BillsViewModel
import app.meubolso.ui.bills.ConfirmAmountDialog
import app.meubolso.ui.components.DeleteReasonDialog
import app.meubolso.ui.components.showUndo
import app.meubolso.ui.savings.SavingsScreen
import app.meubolso.ui.savings.SavingsViewModel
import app.meubolso.ui.settings.DeletedScreen
import app.meubolso.notify.Notifications
import app.meubolso.domain.BillOccurrence
import app.meubolso.domain.IncomeOccurrence
import app.meubolso.core.Dates
import app.meubolso.core.Money
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import java.time.YearMonth
import app.meubolso.ui.components.appViewModel
import app.meubolso.ui.home.HomeScreen
import app.meubolso.ui.home.HomeViewModel
import app.meubolso.ui.quickentry.QuickEntrySheet
import app.meubolso.ui.quickentry.QuickEntryViewModel
import app.meubolso.ui.settings.CategoriesScreen
import app.meubolso.ui.settings.SettingsScreen
import app.meubolso.ui.settings.SettingsViewModel
import app.meubolso.ui.shopping.SectionsScreen
import app.meubolso.ui.shopping.ShoppingScreen
import app.meubolso.ui.shopping.ShoppingViewModel
import app.meubolso.ui.transactions.TransactionsScreen
import app.meubolso.ui.transactions.TransactionsViewModel
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String, val icon: ImageVector, val iconOff: ImageVector)

private val tabs = listOf(
    Tab("home", "Início", Icons.Filled.Home, Icons.Outlined.Home),
    Tab("transactions", "Movimentações", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    Tab("bills", "Contas", Icons.Filled.EventRepeat, Icons.Outlined.EventRepeat),
    Tab("shopping", "Compras", Icons.Filled.ShoppingCart, Icons.Outlined.ShoppingCart),
)
private val tabRoutes = tabs.map { it.route }

// Transições: entre abas, "fade through" (some, e a nova surge crescendo levemente);
// telas internas (Ajustes) deslizam da direita.
private fun AnimatedContentTransitionScope<NavBackStackEntry>.isTabSwitch() =
    initialState.destination.route in tabRoutes && targetState.destination.route in tabRoutes

private val navEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isTabSwitch()) fadeIn(tween(260, delayMillis = 70)) + scaleIn(tween(320, delayMillis = 70, easing = Motion.Emphasized), initialScale = 0.94f)
    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(380, easing = Motion.Emphasized)) { it / 3 } + fadeIn(tween(250))
}
private val navExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isTabSwitch()) fadeOut(tween(90))
    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, tween(380, easing = Motion.Emphasized)) { it / 6 } + fadeOut(tween(200))
}
private val navPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    if (isTabSwitch()) fadeIn(tween(260, delayMillis = 70)) + scaleIn(tween(320, delayMillis = 70), initialScale = 0.94f)
    else slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(380, easing = Motion.Emphasized)) { it / 6 } + fadeIn(tween(250))
}
private val navPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    if (isTabSwitch()) fadeOut(tween(90))
    else slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, tween(320, easing = Motion.Emphasized)) { it / 3 } + fadeOut(tween(200))
}

private val titles = mapOf(
    "home" to "Tostão", "wallets" to "Carteiras", "transactions" to "Movimentações", "bills" to "Contas", "shopping" to "Compras",
    "settings" to "Ajustes", "categories" to "Categorias", "savings" to "Poupança", "sections" to "Seções do mercado", "deleted" to "Lançamentos excluídos",
)

/** Pedido vindo de fora (notificação): abrir lançamento rápido, pagar conta, confirmar entrada. */
data class OpenRequest(val what: String, val id: Long = -1, val period: String? = null)

@Composable
fun MainScreen(openRequest: OpenRequest? = null, onRequestHandled: () -> Unit = {}) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val isTab = tabs.any { it.route == route }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    val quickVm = appViewModel { QuickEntryViewModel(it) }
    var showQuick by remember { mutableStateOf(false) }
    fun openNew() { quickVm.startNew(); showQuick = true }
    fun openEdit(t: Transaction) { quickVm.startEdit(t); showQuick = true }

    // Exclusão com justificativa (lista, edição) + desfazer.
    val c = ctx.container
    var pendingDelete by remember { mutableStateOf<Transaction?>(null) }
    fun requestDelete(t: Transaction) { showQuick = false; pendingDelete = t }

    // Pedidos das notificações.
    var notifBill by remember { mutableStateOf<BillOccurrence?>(null) }
    var notifIncome by remember { mutableStateOf<IncomeOccurrence?>(null) }
    LaunchedEffect(openRequest) {
        val r = openRequest ?: return@LaunchedEffect
        val period = r.period?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
        when (r.what) {
            Notifications.OPEN_QUICK_ENTRY -> openNew()
            Notifications.OPEN_BILL -> c.bills.getBill(r.id)?.let { b ->
                if (period != null) notifBill = BillOccurrence(
                    b, period, Dates.dayIn(period, b.dueDay), null,
                    app.meubolso.domain.Planning.estimate(b, c.db.bills().getPayments()),
                )
            }
            Notifications.OPEN_INCOME -> c.bills.getIncome(r.id)?.let { i ->
                if (period != null) notifIncome = IncomeOccurrence(i, period, Dates.dayIn(period, i.day), null)
            }
        }
        onRequestHandled()
    }

    val walletStatuses by c.wallets.statuses.collectAsStateWithLifecycle(initialValue = emptyList())
    CompositionLocalProvider(LocalWallets provides walletStatuses) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    AnimatedContent(titles[route] ?: "", transitionSpec = {
                        (fadeIn(tween(220)) + slideInVertically { it / 2 }).togetherWith(fadeOut(tween(120)) + slideOutVertically { -it / 2 })
                    }, label = "title") { Text(it) }
                },
                navigationIcon = {
                    if (!isTab) IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                },
                actions = {
                    if (isTab) IconButton(onClick = { nav.navigate("settings") }) { Icon(Icons.Filled.Settings, "Ajustes") }
                },
            )
        },
        bottomBar = {
            AnimatedVisibility(isTab, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) { NavigationBar {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = {
                            nav.navigate(t.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true; restoreState = true
                            }
                        },
                        icon = {
                            val sel = route == t.route
                            val sc by animateFloatAsState(if (sel) 1.12f else 1f, Motion.bouncy(), label = "navIcon")
                            Icon(if (sel) t.icon else t.iconOff, null, Modifier.graphicsLayer { scaleX = sc; scaleY = sc })
                        },
                        label = { Text(t.label, maxLines = 1) },
                    )
                }
            } }
        },
        floatingActionButton = {
            AnimatedVisibility(
                route == "home" || route == "transactions",
                enter = scaleIn(Motion.bouncy()) + fadeIn(), exit = scaleOut(tween(150)) + fadeOut(tween(150)),
            ) {
                FloatingActionButton(onClick = ::openNew) {
                    Icon(Icons.Filled.Add, "Novo lançamento")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val inner = PaddingValues(bottom = padding.calculateBottomPadding())
        NavHost(
            nav, startDestination = "home", modifier = Modifier.padding(top = padding.calculateTopPadding()),
            enterTransition = navEnter, exitTransition = navExit,
            popEnterTransition = navPopEnter, popExitTransition = navPopExit,
        ) {
            composable("home") {
                fun goTab(r: String) = nav.navigate(r) { popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true }
                HomeScreen(
                    appViewModel { HomeViewModel(it) }, inner, onEdit = ::openEdit,
                    onSeeAll = { goTab("transactions") }, onBills = { goTab("bills") },
                    onSavings = { nav.navigate("savings") },
                    onWallets = { nav.navigate("wallets") },
                )
            }
            composable("wallets") {
                WalletsScreen(appViewModel { WalletsViewModel(it) }, PaddingValues(bottom = padding.calculateBottomPadding()))
            }
            composable("transactions") {
                TransactionsScreen(appViewModel { TransactionsViewModel(it) }, inner, onEdit = ::openEdit, onDelete = ::requestDelete)
            }
            composable("bills") { BillsScreen(appViewModel { BillsViewModel(it) }, inner) }
            composable("savings") {
                SavingsScreen(appViewModel { SavingsViewModel(it) }, PaddingValues(bottom = padding.calculateBottomPadding()), snackbar)
            }
            composable("deleted") {
                DeletedScreen(appViewModel { SettingsViewModel(it) }, PaddingValues(bottom = padding.calculateBottomPadding()), snackbar)
            }
            composable("shopping") { ShoppingScreen(appViewModel { ShoppingViewModel(it) }, inner, snackbar) }
            composable("sections") {
                SectionsScreen(appViewModel { ShoppingViewModel(it) }, PaddingValues(bottom = padding.calculateBottomPadding()))
            }
            composable("settings") {
                SettingsScreen(
                    appViewModel { SettingsViewModel(it) }, PaddingValues(bottom = padding.calculateBottomPadding()),
                    onCategories = { nav.navigate("categories") }, onDeleted = { nav.navigate("deleted") },
                    onSavings = { nav.navigate("savings") },
                    onSections = { nav.navigate("sections") },
                    onWallets = { nav.navigate("wallets") },
                )
            }
            composable("categories") {
                CategoriesScreen(appViewModel { SettingsViewModel(it) }, PaddingValues(bottom = padding.calculateBottomPadding()))
            }
        }
    }

    if (showQuick) {
        QuickEntrySheet(quickVm, onDismiss = { showQuick = false }, onSaved = { msg ->
            showQuick = false
            scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) }
        }, onDelete = ::requestDelete)
    }

    pendingDelete?.let { t ->
        DeleteReasonDialog(t, onDismiss = { pendingDelete = null }) { reason ->
            pendingDelete = null
            scope.launch {
                val handle = c.transactions.deleteWithReason(t, reason)
                if (snackbar.showUndo("Lançamento excluído")) c.transactions.undoDelete(handle)
            }
        }
    }
    notifBill?.let { o ->
        ConfirmAmountDialog(
            "Pagar ${o.bill.name}", o.estimate, "Marcar como paga", showPayment = true,
            initialPayment = o.bill.paymentMethod, showCreateTx = true, onDismiss = { notifBill = null },
        ) { amount, date, pm, create ->
            notifBill = null
            scope.launch {
                val ok = c.bills.markPaid(o.bill, o.period, amount, date, pm, create)
                Notifications.cancel(ctx, Notifications.billNotificationId(o.bill.id))
                snackbar.showSnackbar(if (ok) "${o.bill.name} paga!" else "${o.bill.name} já estava paga")
            }
        }
    }
    notifIncome?.let { o ->
        ConfirmAmountDialog(
            "${o.income.name} caiu?", o.income.amount, "Confirmar", showPayment = false,
            initialPayment = null, showCreateTx = false, onDismiss = { notifIncome = null },
        ) { amount, date, _, _ ->
            notifIncome = null
            scope.launch {
                val ok = c.bills.confirmIncome(o.income, o.period, amount, date)
                Notifications.cancel(ctx, Notifications.incomeNotificationId(o.income.id))
                snackbar.showSnackbar(if (ok) "Entrou! ${Money.format(amount)}" else "Já estava confirmada")
            }
        }
    }

    SampleDataPrompt()
    }
}

/** Primeira abertura: "Quer ver o app com dados de exemplo?" */
@Composable
private fun SampleDataPrompt() {
    val c = LocalContext.current.container
    val settings by c.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    if (settings?.sampleDataAnswered == false) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Bem-vindo ao Tostão") },
            text = { Text("Quer ver o app com dados de exemplo? Você pode apagá-los depois em Ajustes.\n\nTudo fica só neste celular: sem login, sem servidor.") },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    busy = true
                    scope.launch {
                        c.categories.ensureDefaults()
                        c.wallets.ensureDefaults()
                        c.shopping.ensureDefaultSections()
                        SampleData.insert(c.db, allWallets = BuildConfig.DEMO)
                        c.settings.setSampleAnswered()
                    }
                }) { Text("Sim, mostrar exemplo") }
            },
            dismissButton = { TextButton(onClick = { scope.launch { c.settings.setSampleAnswered() } }) { Text("Começar vazio") } },
        )
    }
}
