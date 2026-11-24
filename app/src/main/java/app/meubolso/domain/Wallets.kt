package app.meubolso.domain

import app.meubolso.core.Dates
import app.meubolso.data.db.PaymentMethod
import app.meubolso.data.db.Wallet
import app.meubolso.data.db.WalletFlow
import app.meubolso.data.db.WalletKind
import app.meubolso.data.db.WalletTransfer
import java.time.LocalDate
import java.time.YearMonth

/** Uma fatura do cartão: compras de [start] (inclusive) até [closing] (exclusive), paga em [due]. */
data class Invoice(val start: LocalDate, val closing: LocalDate, val due: LocalDate) {
    operator fun contains(d: LocalDate) = !d.isBefore(start) && d.isBefore(closing)
}

/** Período do vale: de uma recarga (inclusive) até a próxima (exclusive). */
data class VoucherCycle(val start: LocalDate, val next: LocalDate) {
    operator fun contains(d: LocalDate) = !d.isBefore(start) && d.isBefore(next)
}

object WalletCycle {
    /**
     * Fatura em que cai uma compra feita em [date]. A fatura fecha no [closingDay]; compras do dia
     * do fechamento em diante já vão para a próxima. Vence no [dueDay] do mesmo mês do fechamento
     * quando o vencimento é depois do fechamento (fecha 3, vence 10), ou no mês seguinte (fecha 28, vence 5).
     */
    fun invoiceFor(date: LocalDate, closingDay: Int, dueDay: Int): Invoice {
        val ym = YearMonth.from(date)
        val closeThis = Dates.dayIn(ym, closingDay)
        val closingYm = if (date.isBefore(closeThis)) ym else ym.plusMonths(1)
        val closing = Dates.dayIn(closingYm, closingDay)
        val start = Dates.dayIn(closingYm.minusMonths(1), closingDay)
        val dueYm = if (dueDay > closingDay) closingYm else closingYm.plusMonths(1)
        return Invoice(start, closing, Dates.dayIn(dueYm, dueDay))
    }

    /** Período do vale que contém [date], com a recarga no [rechargeDay]. */
    fun voucherCycle(date: LocalDate, rechargeDay: Int): VoucherCycle {
        val ym = YearMonth.from(date)
        val r = Dates.dayIn(ym, rechargeDay)
        return if (date.isBefore(r)) VoucherCycle(Dates.dayIn(ym.minusMonths(1), rechargeDay), r)
        else VoucherCycle(r, Dates.dayIn(ym.plusMonths(1), rechargeDay))
    }
}

/** Onde foi pago: a carteira e, numa conta, se foi Pix, débito ou boleto. */
data class PaymentChoice(val walletId: Long? = null, val method: PaymentMethod? = null)

/** Situação de uma carteira hoje. Valores em centavos. */
data class WalletStatus(
    val wallet: Wallet,
    /** Conta e dinheiro: saldo. Cartão: quanto do limite está usado. Vale: quanto sobra no período. */
    val balance: Long,
    /** Cartão: fatura aberta (ainda não fechou). */
    val openInvoice: Long = 0,
    /** Cartão: fatura fechada que falta pagar. */
    val closedDue: Long = 0,
    /** Cartão: vencimento da fatura fechada. */
    val closedDueDate: LocalDate? = null,
    /** Cartão: fatura aberta agora. */
    val invoice: Invoice? = null,
    /** Vale: período atual. */
    val cycle: VoucherCycle? = null,
    /** Vale: quanto já gastou no período. */
    val spent: Long = 0,
) {
    val kind get() = wallet.kind
    val limit get() = wallet.limitAmount

    /** Cartão: limite que sobra. Vale: saldo do período. Conta e dinheiro: o saldo. */
    val available: Long
        get() = when (kind) {
            WalletKind.CREDIT_CARD -> limit - balance
            else -> balance
        }

    /** Fração usada (cartão e vale), para a barra. */
    val usedFraction: Float
        get() = when (kind) {
            WalletKind.CREDIT_CARD -> if (limit > 0) balance.toFloat() / limit else 0f
            WalletKind.VOUCHER -> if (limit > 0) spent.toFloat() / limit else 0f
            else -> 0f
        }
}

object Wallets {
    /**
     * Carteira de um lançamento. Lançamentos sem carteira (antigos ou de exemplo) ficam na primeira
     * carteira de dinheiro quando foram pagos em dinheiro, ou na primeira conta.
     */
    fun resolve(walletId: Long?, payment: PaymentMethod?, wallets: List<Wallet>): Wallet? {
        if (walletId != null) wallets.firstOrNull { it.id == walletId }?.let { return it }
        return defaultFor(payment, wallets)
    }

    fun defaultFor(payment: PaymentMethod?, wallets: List<Wallet>): Wallet? {
        val active = wallets.filter { !it.archived }.ifEmpty { wallets }
        val kind = when (payment) {
            PaymentMethod.DINHEIRO -> WalletKind.CASH
            PaymentMethod.CARTAO -> WalletKind.CREDIT_CARD
            else -> WalletKind.ACCOUNT
        }
        return active.firstOrNull { it.kind == kind } ?: active.firstOrNull { it.kind == WalletKind.ACCOUNT } ?: active.firstOrNull()
    }

    /** Forma de pagamento que combina com a carteira (para quem ainda olha o campo antigo). */
    fun paymentFor(kind: WalletKind, chosen: PaymentMethod?): PaymentMethod? = when (kind) {
        WalletKind.CASH -> PaymentMethod.DINHEIRO
        WalletKind.CREDIT_CARD -> PaymentMethod.CARTAO
        WalletKind.VOUCHER -> null
        WalletKind.ACCOUNT -> chosen?.takeIf { it != PaymentMethod.DINHEIRO && it != PaymentMethod.CARTAO }
    }

    /** Situação de todas as carteiras em [today]. */
    fun statuses(
        wallets: List<Wallet>, flows: List<WalletFlow>, transfers: List<WalletTransfer>, today: LocalDate,
    ): List<WalletStatus> {
        val byWallet = flows.groupBy { resolve(it.walletId, it.paymentMethod, wallets)?.id }
        return wallets.map { w ->
            val f = byWallet[w.id].orEmpty()
            val tin = transfers.filter { it.toWalletId == w.id }
            val tout = transfers.filter { it.fromWalletId == w.id }
            status(w, f, tin, tout, today)
        }
    }

    fun status(
        w: Wallet, flows: List<WalletFlow>, transfersIn: List<WalletTransfer>, transfersOut: List<WalletTransfer>, today: LocalDate,
    ): WalletStatus {
        val inSum = transfersIn.sumOf { it.amount }
        val outSum = transfersOut.sumOf { it.amount }
        return when (w.kind) {
            WalletKind.ACCOUNT, WalletKind.CASH -> WalletStatus(
                w, balance = w.initialBalance - flows.sumOf { it.charge } + inSum - outSum,
            )
            WalletKind.CREDIT_CARD -> {
                val inv = WalletCycle.invoiceFor(today, w.closingDay.coerceAtLeast(1), w.dueDay.coerceAtLeast(1))
                // Pagamentos (transferências para o cartão) quitam primeiro o que fechou antes.
                val paid = inSum - outSum
                val before = flows.filter { it.date.isBefore(inv.start) }.sumOf { it.charge }
                val open = flows.filter { it.date in inv }.sumOf { it.charge }
                val credit = (paid - before).coerceAtLeast(0)
                WalletStatus(
                    w,
                    balance = (flows.sumOf { it.charge } - paid).coerceAtLeast(0),
                    openInvoice = (open - credit).coerceAtLeast(0),
                    closedDue = (before - paid).coerceAtLeast(0),
                    invoice = inv,
                    closedDueDate = WalletCycle.invoiceFor(inv.start.minusDays(1), w.closingDay.coerceAtLeast(1), w.dueDay.coerceAtLeast(1)).due,
                )
            }
            WalletKind.VOUCHER -> {
                val cyc = WalletCycle.voucherCycle(today, w.rechargeDay.coerceAtLeast(1))
                val spent = flows.filter { it.date in cyc }.sumOf { it.charge }
                WalletStatus(w, balance = w.limitAmount - spent, cycle = cyc, spent = spent)
            }
        }
    }
}
