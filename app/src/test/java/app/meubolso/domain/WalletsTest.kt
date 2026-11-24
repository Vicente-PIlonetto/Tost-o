package app.meubolso.domain

import app.meubolso.data.db.PaymentMethod
import app.meubolso.data.db.Wallet
import app.meubolso.data.db.WalletFlow
import app.meubolso.data.db.WalletKind
import app.meubolso.data.db.WalletTransfer
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WalletsTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test fun invoiceBeforeClosingIsThisMonth() {
        val i = WalletCycle.invoiceFor(d("2026-11-02"), closingDay = 3, dueDay = 10)
        assertEquals(Invoice(d("2026-10-03"), d("2026-11-03"), d("2026-11-10")), i)
    }

    @Test fun purchaseOnClosingDayGoesToNextInvoice() {
        val i = WalletCycle.invoiceFor(d("2026-11-03"), closingDay = 3, dueDay = 10)
        assertEquals(Invoice(d("2026-11-03"), d("2026-12-03"), d("2026-12-10")), i)
    }

    @Test fun dueBeforeClosingFallsNextMonth() {
        val i = WalletCycle.invoiceFor(d("2026-12-20"), closingDay = 28, dueDay = 5)
        assertEquals(Invoice(d("2026-11-28"), d("2026-12-28"), d("2027-01-05")), i)
    }

    @Test fun closingDay31InFebruary() {
        val i = WalletCycle.invoiceFor(d("2027-02-15"), closingDay = 31, dueDay = 8)
        assertEquals(Invoice(d("2027-01-31"), d("2027-02-28"), d("2027-03-08")), i)
    }

    @Test fun voucherCycle() {
        assertEquals(VoucherCycle(d("2026-10-25"), d("2026-11-25")), WalletCycle.voucherCycle(d("2026-11-23"), 25))
        assertEquals(VoucherCycle(d("2026-11-25"), d("2026-12-25")), WalletCycle.voucherCycle(d("2026-11-25"), 25))
    }

    private val card = Wallet(id = 3, name = "Nubank", kind = WalletKind.CREDIT_CARD, limitAmount = 200_000, closingDay = 3, dueDay = 10, color = 0)
    private fun flow(date: String, cents: Long, w: Long? = 3) = WalletFlow(w, null, d(date), cents)
    private fun pay(cents: Long) = WalletTransfer(fromWalletId = 1, toWalletId = 3, amount = cents, date = d("2026-11-08"))

    @Test fun cardSplitsClosedAndOpenInvoice() {
        val flows = listOf(flow("2026-10-20", 30_000), flow("2026-11-05", 10_000))
        val s = Wallets.status(card, flows, emptyList(), emptyList(), d("2026-11-06"))
        assertEquals(30_000, s.closedDue)
        assertEquals(10_000, s.openInvoice)
        assertEquals(40_000, s.balance)
        assertEquals(160_000, s.available)
    }

    @Test fun payingInvoiceFreesLimit() {
        val flows = listOf(flow("2026-10-20", 30_000), flow("2026-11-05", 10_000))
        val s = Wallets.status(card, flows, listOf(pay(30_000)), emptyList(), d("2026-11-08"))
        assertEquals(0, s.closedDue)
        assertEquals(10_000, s.openInvoice)
        assertEquals(190_000, s.available)
    }

    @Test fun overpaymentReducesOpenInvoice() {
        val flows = listOf(flow("2026-10-20", 30_000), flow("2026-11-05", 10_000))
        val s = Wallets.status(card, flows, listOf(pay(35_000)), emptyList(), d("2026-11-08"))
        assertEquals(0, s.closedDue)
        assertEquals(5_000, s.openInvoice)
    }

    @Test fun voucherSpentInCycleOnly() {
        val vr = Wallet(id = 4, name = "VR", kind = WalletKind.VOUCHER, limitAmount = 80_000, rechargeDay = 1, color = 0)
        val flows = listOf(WalletFlow(4, null, d("2026-10-30"), 50_000), WalletFlow(4, null, d("2026-11-10"), 12_000))
        val s = Wallets.status(vr, flows, emptyList(), emptyList(), d("2026-11-23"))
        assertEquals(12_000, s.spent)
        assertEquals(68_000, s.available)
    }

    @Test fun accountBalance() {
        val acc = Wallet(id = 1, name = "Conta", kind = WalletKind.ACCOUNT, initialBalance = 100_000, color = 0)
        val flows = listOf(WalletFlow(1, null, d("2026-11-01"), -500_000), WalletFlow(1, null, d("2026-11-02"), 20_000))
        val s = Wallets.status(acc, flows, emptyList(), listOf(pay(30_000)), d("2026-11-23"))
        assertEquals(100_000 + 500_000 - 20_000 - 30_000, s.balance)
    }

    @Test fun legacyTransactionsResolveByPayment() {
        val acc = Wallet(id = 1, name = "Conta", kind = WalletKind.ACCOUNT, color = 0)
        val cash = Wallet(id = 2, name = "Dinheiro", kind = WalletKind.CASH, color = 0)
        val all = listOf(acc, cash)
        assertEquals(cash, Wallets.resolve(null, PaymentMethod.DINHEIRO, all))
        assertEquals(acc, Wallets.resolve(null, PaymentMethod.PIX, all))
        assertEquals(acc, Wallets.resolve(null, null, all))
        assertEquals(cash, Wallets.resolve(2, PaymentMethod.PIX, all))
    }
}
