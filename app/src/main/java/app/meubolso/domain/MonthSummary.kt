package app.meubolso.domain

/**
 * Resumo do mês financeiro. Todos os valores em centavos.
 *
 * - [incomeReceived]: entradas já lançadas no mês.
 * - [incomePending]: entradas recorrentes previstas para o mês que ainda não foram confirmadas.
 * - [expenses]: saídas lançadas no mês (sem pagamento de fatura de cartão).
 * - [committed]: contas fixas ativas que ainda vão vencer/não foram pagas no mês.
 * - [saved]: líquido guardado na poupança no mês (guardado − resgatado). Não é gasto, mas sai do livre.
 */
data class MonthSummary(
    val incomeReceived: Long,
    val incomePending: Long,
    val expenses: Long,
    val committed: Long,
    val saved: Long = 0,
) {
    /** Entradas previstas = recebidas + as que ainda vão cair. */
    val incomeExpected: Long get() = incomeReceived + incomePending

    /** Livre pra gastar = entradas previstas − saídas − comprometido − guardado na poupança. */
    val freeToSpend: Long get() = incomeExpected - expenses - committed - saved

    val isNegative: Boolean get() = freeToSpend < 0

    companion object {
        val EMPTY = MonthSummary(0, 0, 0, 0)
    }
}
