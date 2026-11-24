package app.meubolso.data

import app.meubolso.data.db.AppDatabase
import app.meubolso.data.db.Bill
import app.meubolso.data.db.BillPayment
import app.meubolso.data.db.IncomeReceipt
import app.meubolso.data.db.PaymentMethod
import app.meubolso.data.db.RecurringIncome
import app.meubolso.data.db.Transaction
import app.meubolso.data.db.TxOrigin
import app.meubolso.data.db.TxType
import java.time.LocalDate
import java.time.YearMonth
import app.meubolso.data.db.ListEntry
import app.meubolso.data.db.Purchase
import app.meubolso.data.db.ShoppingItem
import kotlinx.coroutines.flow.first
import kotlin.random.Random

/** Dados de exemplo (marcados com isSample) para conhecer o app. Podem ser apagados em Ajustes. */
object SampleData {
    /** Catálogo com histórico: alguns itens já "estão acabando" para mostrar as sugestões. */
    private suspend fun insertShopping(db: AppDatabase, today: LocalDate) {
        val dao = db.shopping()
        val sections = dao.observeSections().first().associateBy { it.name }
        fun sec(n: String) = (sections[n] ?: sections.values.first()).id
        // nome, seção, unidade, intervalo em dias, dias desde a última compra, preço
        val items = listOf(
            listOf("Leite", "Frios e laticínios", "L", 7, 7, 549L),
            listOf("Pão francês", "Padaria", "kg", 3, 1, 1690L),
            listOf("Café", "Mercearia", "pct", 20, 19, 1890L),
            listOf("Arroz", "Mercearia", "kg", 30, 12, 2790L),
            listOf("Banana", "Hortifrúti", "kg", 7, 9, 699L),
            listOf("Detergente", "Limpeza", "un", 25, 10, 289L),
            listOf("Papel higiênico", "Higiene", "pct", 30, 33, 2190L),
        )
        for (row in items) {
            val name = row[0] as String
            val interval = (row[3] as Int).toLong()
            val since = (row[4] as Int).toLong()
            val id = dao.insertItem(ShoppingItem(name = name, sectionId = sec(row[1] as String), unit = row[2] as String, isSample = true))
            dao.insertPurchases((0..3).map { k ->
                Purchase(itemId = id, date = today.minusDays(since + k * interval), price = (row[5] as Long) + k * 20)
            })
        }
        // Dois itens já na lista.
        for (n in listOf("Arroz", "Detergente")) dao.itemByName(n)?.let { dao.insertEntry(ListEntry(itemId = it.id)) }
    }

    suspend fun insert(db: AppDatabase, today: LocalDate = LocalDate.now()) {
        val cats = db.categories().getAll().filter { !it.archived }
        fun cat(type: TxType, name: String) = cats.first { it.type == type && it.name == name }.id
        val rnd = Random(42)
        val txDao = db.transactions()
        val thisMonth = YearMonth.from(today)

        // Entradas fixas e contas de exemplo.
        val salary = RecurringIncome(name = "Salário", amount = 520000, categoryId = cat(TxType.INCOME, "Salário"), day = 5,
            anchorMonth = thisMonth.minusMonths(3), isSample = true)
        val salaryId = db.incomes().insert(salary)
        val rent = Bill(name = "Aluguel", amount = 150000, categoryId = cat(TxType.EXPENSE, "Moradia"), dueDay = 10,
            anchorMonth = thisMonth.minusMonths(3), paymentMethod = PaymentMethod.PIX, isSample = true)
        val rentId = db.bills().insert(rent)
        val light = Bill(name = "Conta de luz", amount = 22000, categoryId = cat(TxType.EXPENSE, "Contas da casa"), dueDay = 12,
            anchorMonth = thisMonth.minusMonths(3), paymentMethod = PaymentMethod.BOLETO, keywords = "ENEL, CEMIG, LIGHT", isSample = true)
        val lightId = db.bills().insert(light)
        db.bills().insert(Bill(name = "Netflix", amount = 4490, categoryId = cat(TxType.EXPENSE, "Assinaturas"),
            dueDay = (today.dayOfMonth + 2).coerceAtMost(28), anchorMonth = thisMonth, paymentMethod = PaymentMethod.DEBITO,
            keywords = "NETFLIX", isSubscription = true, isSample = true))
        db.bills().insert(Bill(name = "Spotify", amount = 2190, categoryId = cat(TxType.EXPENSE, "Assinaturas"), dueDay = 20,
            anchorMonth = thisMonth, keywords = "SPOTIFY", isSubscription = true, isSample = true))
        db.bills().insert(Bill(name = "Internet", amount = 9990, categoryId = cat(TxType.EXPENSE, "Contas da casa"),
            dueDay = (today.dayOfMonth + 5).coerceAtMost(28), anchorMonth = thisMonth, paymentMethod = PaymentMethod.PIX, isSample = true))
        db.bills().insert(Bill(name = "IPVA", amount = 120000, categoryId = cat(TxType.EXPENSE, "Transporte"), dueDay = 15,
            intervalMonths = 12, anchorMonth = YearMonth.of(today.year + if (today.monthValue > 3) 1 else 0, 3), isSample = true))

        val expenses = listOf(
            Triple("Mercado", "Assaí", 18000L..42000L),
            Triple("Alimentação fora", "Almoço", 2500L..6000L),
            Triple("Transporte", "Uber", 1500L..4500L),
            Triple("Lazer", "Cinema", 3000L..9000L),
            Triple("Saúde", "Farmácia", 2000L..12000L),
            Triple("Compras", "Loja", 5000L..25000L),
        )
        for (monthsAgo in 0..3) {
            val ym = thisMonth.minusMonths(monthsAgo.toLong())
            val lastDay = if (monthsAgo == 0) today.dayOfMonth else ym.lengthOfMonth()
            fun day(d: Int) = ym.atDay(d.coerceIn(1, lastDay))
            if (lastDay >= 5) {
                val id = txDao.insert(Transaction(type = TxType.INCOME, amount = salary.amount, categoryId = salary.categoryId,
                    date = day(5), description = "Salário", origin = TxOrigin.CONTA_FIXA, isSample = true))
                db.incomes().insertReceipt(IncomeReceipt(incomeId = salaryId, period = ym, amount = salary.amount, transactionId = id))
            }
            if (lastDay >= 10) {
                val id = txDao.insert(Transaction(type = TxType.EXPENSE, amount = rent.amount, categoryId = rent.categoryId,
                    date = day(10), description = "Aluguel", paymentMethod = PaymentMethod.PIX, origin = TxOrigin.CONTA_FIXA, isSample = true))
                db.bills().insertPayment(BillPayment(billId = rentId, period = ym, amount = rent.amount, paidDate = day(10), transactionId = id))
            }
            if (lastDay >= 12) {
                val amount = 18000L + rnd.nextLong(0, 9000)
                val id = txDao.insert(Transaction(type = TxType.EXPENSE, amount = amount, categoryId = light.categoryId,
                    date = day(12), description = "Conta de luz", paymentMethod = PaymentMethod.BOLETO, origin = TxOrigin.CONTA_FIXA, isSample = true))
                db.bills().insertPayment(BillPayment(billId = lightId, period = ym, amount = amount, paidDate = day(12), transactionId = id))
            }
            val list = mutableListOf<Transaction>()
            repeat((lastDay / 2).coerceAtLeast(1)) {
                val (name, desc, range) = expenses[rnd.nextInt(expenses.size)]
                list += Transaction(
                    type = TxType.EXPENSE, amount = rnd.nextLong(range.first, range.last),
                    categoryId = cat(TxType.EXPENSE, name), date = day(rnd.nextInt(1, lastDay + 1)),
                    description = desc,
                    paymentMethod = listOf(PaymentMethod.PIX, PaymentMethod.DEBITO, PaymentMethod.DINHEIRO)[rnd.nextInt(3)],
                    isSample = true,
                )
            }
            txDao.insertAll(list)
        }
        insertShopping(db, today)

        // Limites de exemplo para mostrar as barras de progresso.
        db.categories().getAll().filter { it.type == TxType.EXPENSE && it.name in setOf("Mercado", "Alimentação fora", "Lazer") }
            .forEach { db.categories().update(it.copy(monthlyLimit = when (it.name) { "Mercado" -> 100000L; "Lazer" -> 20000L; else -> 30000L })) }
    }
}
