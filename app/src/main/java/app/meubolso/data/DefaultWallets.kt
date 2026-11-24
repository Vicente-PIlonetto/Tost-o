package app.meubolso.data

import app.meubolso.data.db.Wallet
import app.meubolso.data.db.WalletKind

object DefaultWallets {
    const val ACCOUNT_COLOR = 0xFF1565C0
    const val CASH_COLOR = 0xFF2E7D32
    const val CARD_COLOR = 0xFF6A1B9A
    const val VOUCHER_COLOR = 0xFFEF6C00

    /** Cores para escolher ao criar uma carteira. */
    val palette = listOf(
        ACCOUNT_COLOR, CARD_COLOR, VOUCHER_COLOR, CASH_COLOR,
        0xFF00838F, 0xFFC2185B, 0xFF5D4037, 0xFF37474F, 0xFFD32F2F, 0xFFF9A825,
    )

    fun colorFor(kind: WalletKind) = when (kind) {
        WalletKind.ACCOUNT -> ACCOUNT_COLOR
        WalletKind.CREDIT_CARD -> CARD_COLOR
        WalletKind.VOUCHER -> VOUCHER_COLOR
        WalletKind.CASH -> CASH_COLOR
    }

    /** Instalação nova: conta e dinheiro. Cartão e vale o usuário cria com os dados dele. */
    val all: List<Wallet> = listOf(
        Wallet(name = "Conta", kind = WalletKind.ACCOUNT, color = ACCOUNT_COLOR, sortOrder = 0),
        Wallet(name = "Dinheiro", kind = WalletKind.CASH, color = CASH_COLOR, sortOrder = 1),
    )
}
