package app.meubolso.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.YearMonth

enum class TxType { EXPENSE, INCOME }

enum class PaymentMethod(val label: String) {
    PIX("Pix"), DEBITO("Débito"), DINHEIRO("Dinheiro"), BOLETO("Boleto"), CARTAO("Cartão de crédito");
}

enum class TxOrigin(val label: String) {
    MANUAL("Manual"), CONTA_FIXA("Conta fixa"), COMPRA("Compra do mercado"), IMPORTACAO("Importação");
}

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: TxType,
    /** Chave do ícone em [app.meubolso.ui.components.CategoryIcons]. */
    val icon: String,
    /** Cor ARGB. */
    val color: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    /** Limite mensal opcional (só saídas), em centavos. */
    @ColumnInfo(name = "monthly_limit") val monthlyLimit: Long? = null,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [ForeignKey(
        entity = Category::class, parentColumns = ["id"], childColumns = ["category_id"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("date"), Index("category_id"), Index("wallet_id")],
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    /** Sempre positivo, em centavos. O sinal vem de [type]. */
    val amount: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val date: LocalDate,
    val description: String = "",
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod? = null,
    val origin: TxOrigin = TxOrigin.MANUAL,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    /** Criado pelos dados de exemplo — pode ser apagado em Ajustes. */
    @ColumnInfo(name = "is_sample") val isSample: Boolean = false,
    /** Carteira de onde saiu ou para onde entrou o dinheiro (conta, cartão, vale, dinheiro). */
    @ColumnInfo(name = "wallet_id") val walletId: Long? = null,
) {
    val signedAmount: Long get() = if (type == TxType.INCOME) amount else -amount
}

/** Registro de lançamentos excluídos, com a justificativa. Guarda uma cópia completa do lançamento. */
@Entity(tableName = "deleted_transactions", indices = [Index("deleted_at")])
data class DeletedTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "original_id") val originalId: Long,
    val type: TxType,
    val amount: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val date: LocalDate,
    val description: String,
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod?,
    val origin: TxOrigin,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val reason: String,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "wallet_id") val walletId: Long? = null,
) {
    fun toTransaction() = Transaction(
        id = originalId, type = type, amount = amount, categoryId = categoryId, date = date,
        description = description, paymentMethod = paymentMethod, origin = origin, createdAt = createdAt,
        walletId = walletId,
    )

    companion object {
        fun of(t: Transaction, reason: String) = DeletedTransaction(
            originalId = t.id, type = t.type, amount = t.amount, categoryId = t.categoryId, date = t.date,
            description = t.description, paymentMethod = t.paymentMethod, origin = t.origin,
            createdAt = t.createdAt, reason = reason, walletId = t.walletId,
        )
    }
}

enum class SavingsKind(val label: String) {
    /** Guardou dinheiro: sai do "livre pra gastar" do mês, mas não é um gasto. */
    DEPOSIT("Guardado"),
    /** Resgatou: volta para o "livre pra gastar" do mês. */
    WITHDRAW("Resgatado"),
    /** Ajuste de saldo (ex.: saldo inicial) — não mexe no mês. */
    ADJUST("Ajuste de saldo"),
}

/** Movimento da poupança. [amount] sempre positivo; o efeito vem de [kind] (ADJUST pode ser negativo). */
@Entity(tableName = "savings_movements", indices = [Index("date")])
data class SavingsMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: SavingsKind,
    val amount: Long,
    val date: LocalDate,
    val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    /** Efeito no saldo guardado. */
    val balanceDelta: Long get() = when (kind) {
        SavingsKind.DEPOSIT -> amount
        SavingsKind.WITHDRAW -> -amount
        SavingsKind.ADJUST -> amount
    }
}

/**
 * Conta fixa ou assinatura. A periodicidade é [intervalMonths] (1 = mensal, 12 = anual, N = a cada N meses)
 * contada a partir de [anchorMonth] (primeiro mês em que vence).
 */
@Entity(
    tableName = "bills",
    foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("category_id")],
)
data class Bill(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    @ColumnInfo(name = "due_day") val dueDay: Int,
    @ColumnInfo(name = "interval_months") val intervalMonths: Int = 1,
    @ColumnInfo(name = "anchor_month") val anchorMonth: YearMonth,
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod? = null,
    @ColumnInfo(name = "remind_days_before") val remindDaysBefore: Int = 2,
    /** Palavras-chave para reconhecer no extrato, separadas por vírgula (ex.: "NETFLIX"). */
    val keywords: String = "",
    @ColumnInfo(name = "is_subscription") val isSubscription: Boolean = false,
    val active: Boolean = true,
    /**
     * Valor variável (condomínio, luz…): [amount] vira só uma estimativa (pode ser 0) e o valor real
     * é informado ao pagar. v4.
     */
    @ColumnInfo(name = "variable_amount", defaultValue = "0") val variableAmount: Boolean = false,
    @ColumnInfo(name = "is_sample") val isSample: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "bill_payments",
    foreignKeys = [
        ForeignKey(entity = Bill::class, parentColumns = ["id"], childColumns = ["bill_id"], onDelete = ForeignKey.CASCADE),
        // Excluir o lançamento desfaz o pagamento (a conta volta a ficar pendente).
        ForeignKey(entity = Transaction::class, parentColumns = ["id"], childColumns = ["transaction_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["bill_id", "period"], unique = true), Index("transaction_id")],
)
data class BillPayment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "bill_id") val billId: Long,
    /** Mês (do calendário) do vencimento pago. */
    val period: YearMonth,
    val amount: Long,
    @ColumnInfo(name = "paid_date") val paidDate: LocalDate,
    @ColumnInfo(name = "transaction_id") val transactionId: Long? = null,
)

/** Entrada fixa (salário etc.) que cai todo mês no dia [day]. */
@Entity(
    tableName = "recurring_incomes",
    foreignKeys = [ForeignKey(entity = Category::class, parentColumns = ["id"], childColumns = ["category_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("category_id")],
)
data class RecurringIncome(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val amount: Long,
    @ColumnInfo(name = "category_id") val categoryId: Long,
    val day: Int,
    @ColumnInfo(name = "anchor_month") val anchorMonth: YearMonth,
    val active: Boolean = true,
    @ColumnInfo(name = "is_sample") val isSample: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "income_receipts",
    foreignKeys = [
        ForeignKey(entity = RecurringIncome::class, parentColumns = ["id"], childColumns = ["income_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Transaction::class, parentColumns = ["id"], childColumns = ["transaction_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["income_id", "period"], unique = true), Index("transaction_id")],
)
data class IncomeReceipt(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "income_id") val incomeId: Long,
    val period: YearMonth,
    val amount: Long,
    @ColumnInfo(name = "transaction_id") val transactionId: Long? = null,
)

/** Notificações já enviadas (evita repetir o mesmo aviso). */
@Entity(tableName = "notified")
data class Notified(
    @PrimaryKey val key: String,
    val at: Long = System.currentTimeMillis(),
)


/** Seção do mercado (Hortifrúti, Padaria…). [sortOrder] = ordem em que o usuário anda no mercado. v4 */
@Entity(tableName = "market_sections")
data class MarketSection(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
)

/** Item do catálogo de compras. v4 */
@Entity(
    tableName = "shopping_items",
    foreignKeys = [ForeignKey(entity = MarketSection::class, parentColumns = ["id"], childColumns = ["section_id"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("section_id"), Index(value = ["name"], unique = true)],
)
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(name = "section_id") val sectionId: Long,
    /** "un", "kg", "L", "pct"… pode ficar vazio. */
    val unit: String = "",
    @ColumnInfo(name = "is_sample") val isSample: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/** Item na lista de compras atual. v4 */
@Entity(
    tableName = "shopping_list",
    foreignKeys = [ForeignKey(entity = ShoppingItem::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["item_id"], unique = true)],
)
data class ListEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "item_id") val itemId: Long,
    /** Quantidade opcional, texto livre ("2", "1,5"). */
    val quantity: String = "",
    val checked: Boolean = false,
    /** Preço anotado ao marcar (centavos), opcional. */
    val price: Long? = null,
    @ColumnInfo(name = "added_at") val addedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "checked_at") val checkedAt: Long? = null,
)

/** Compra finalizada (vira uma movimentação na categoria Mercado). v4 */
@Entity(
    tableName = "shopping_trips",
    foreignKeys = [ForeignKey(entity = Transaction::class, parentColumns = ["id"], childColumns = ["transaction_id"], onDelete = ForeignKey.SET_NULL)],
    indices = [Index("transaction_id"), Index("date")],
)
data class ShoppingTrip(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val total: Long,
    @ColumnInfo(name = "payment_method") val paymentMethod: PaymentMethod?,
    @ColumnInfo(name = "transaction_id") val transactionId: Long?,
    @ColumnInfo(name = "item_count") val itemCount: Int,
    @ColumnInfo(name = "is_sample") val isSample: Boolean = false,
)

/** Cada vez que um item foi comprado (base das sugestões e do histórico de preço). v4 */
@Entity(
    tableName = "purchase_history",
    foreignKeys = [
        ForeignKey(entity = ShoppingItem::class, parentColumns = ["id"], childColumns = ["item_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = ShoppingTrip::class, parentColumns = ["id"], childColumns = ["trip_id"], onDelete = ForeignKey.SET_NULL),
    ],
    indices = [Index("item_id"), Index("trip_id")],
)
data class Purchase(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "item_id") val itemId: Long,
    val date: LocalDate,
    val price: Long? = null,
    val quantity: String = "",
    @ColumnInfo(name = "trip_id") val tripId: Long? = null,
)

/** Tipo de carteira: de onde sai (ou para onde entra) o dinheiro. */
enum class WalletKind(val label: String) {
    /** Conta no banco: débito, Pix, boleto. Tem saldo. */
    ACCOUNT("Conta"),
    /** Cartão de crédito: limite, dia de fechamento e dia de vencimento da fatura. */
    CREDIT_CARD("Cartão de crédito"),
    /** Vale-alimentação ou refeição: valor por mês e dia da recarga. */
    VOUCHER("Vale"),
    /** Dinheiro em espécie. Tem saldo. */
    CASH("Dinheiro"),
}

@Entity(tableName = "wallets")
data class Wallet(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val kind: WalletKind,
    /** Cartão: limite total. Vale: valor que cai por mês. Em centavos. */
    @ColumnInfo(name = "limit_amount") val limitAmount: Long = 0,
    /** Cartão: dia em que a fatura fecha (compras desse dia em diante vão para a próxima). */
    @ColumnInfo(name = "closing_day") val closingDay: Int = 0,
    /** Cartão: dia do vencimento da fatura. */
    @ColumnInfo(name = "due_day") val dueDay: Int = 0,
    /** Vale: dia em que o valor do mês cai. */
    @ColumnInfo(name = "recharge_day") val rechargeDay: Int = 0,
    /** Conta e dinheiro: saldo antes do primeiro lançamento no app. */
    @ColumnInfo(name = "initial_balance") val initialBalance: Long = 0,
    /** Cor ARGB. */
    val color: Long,
    @ColumnInfo(name = "sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Dinheiro que muda de carteira sem ser gasto nem entrada: pagar a fatura do cartão com a conta,
 * sacar dinheiro, passar de uma conta para outra. Não mexe no "livre pra gastar".
 */
@Entity(
    tableName = "wallet_transfers",
    foreignKeys = [
        ForeignKey(entity = Wallet::class, parentColumns = ["id"], childColumns = ["from_wallet_id"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = Wallet::class, parentColumns = ["id"], childColumns = ["to_wallet_id"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("from_wallet_id"), Index("to_wallet_id"), Index("date")],
)
data class WalletTransfer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "from_wallet_id") val fromWalletId: Long,
    @ColumnInfo(name = "to_wallet_id") val toWalletId: Long,
    val amount: Long,
    val date: LocalDate,
    val note: String = "",
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
)
