package app.meubolso.domain

import java.time.LocalDate

/**
 * "Talvez esteja acabando": estatística simples, sem IA.
 * A partir de 2 compras (em dias diferentes), calcula o intervalo médio entre elas. Se o tempo desde a
 * última compra passar de [THRESHOLD] (85%) desse intervalo, o item é sugerido.
 */
object ShoppingSuggestions {
    const val THRESHOLD = 0.85

    data class Stats(val itemId: Long, val lastDate: LocalDate, val avgIntervalDays: Double, val daysSinceLast: Long) {
        /** Quanto do intervalo médio já passou (1.0 = exatamente no intervalo). */
        val ratio: Double get() = if (avgIntervalDays <= 0) 0.0 else daysSinceLast / avgIntervalDays
    }

    /** Estatística de um item a partir das datas em que foi comprado. null se tiver menos de 2 compras. */
    fun stats(itemId: Long, dates: Collection<LocalDate>, today: LocalDate): Stats? {
        val distinct = dates.toSortedSet()
        if (distinct.size < 2) return null
        val first = distinct.first()
        val last = distinct.last()
        val avg = (last.toEpochDay() - first.toEpochDay()).toDouble() / (distinct.size - 1)
        if (avg <= 0) return null
        return Stats(itemId, last, avg, today.toEpochDay() - last.toEpochDay())
    }

    /**
     * Itens sugeridos, do mais "atrasado" para o menos.
     * [purchases]: itemId → datas de compra. [inList]: itens que já estão na lista (não sugere).
     */
    fun suggest(purchases: Map<Long, Collection<LocalDate>>, inList: Set<Long>, today: LocalDate): List<Stats> =
        purchases.asSequence()
            .filter { it.key !in inList }
            .mapNotNull { (id, dates) -> stats(id, dates, today) }
            .filter { it.ratio >= THRESHOLD }
            .sortedByDescending { it.ratio }
            .toList()
}
