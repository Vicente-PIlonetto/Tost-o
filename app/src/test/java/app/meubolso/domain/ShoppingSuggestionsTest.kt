package app.meubolso.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShoppingSuggestionsTest {
    private val today = LocalDate.of(2026, 11, 19)
    private fun d(daysAgo: Long) = today.minusDays(daysAgo)

    @Test fun needsAtLeastTwoPurchases() {
        assertNull(ShoppingSuggestions.stats(1, listOf(d(30)), today))
        assertNull(ShoppingSuggestions.stats(1, emptyList(), today))
    }

    @Test fun sameDayPurchasesCountOnce() {
        assertNull(ShoppingSuggestions.stats(1, listOf(d(10), d(10)), today))
    }

    @Test fun averageInterval() {
        // Compras há 30, 20 e 10 dias: intervalo médio 10, última há 10 dias.
        val s = ShoppingSuggestions.stats(1, listOf(d(30), d(20), d(10)), today)!!
        assertEquals(10.0, s.avgIntervalDays, 0.001)
        assertEquals(10L, s.daysSinceLast)
        assertEquals(1.0, s.ratio, 0.001)
    }

    @Test fun suggestsFrom85Percent() {
        val purchases = mapOf(
            1L to listOf(d(27), d(7)),   // intervalo 20, passou 7  -> 35%  (não)
            2L to listOf(d(37), d(17)),  // intervalo 20, passou 17 -> 85%  (sim, no limite)
            3L to listOf(d(36), d(16)),  // intervalo 20, passou 16 -> 80%  (não)
            4L to listOf(d(40), d(30)),  // intervalo 10, passou 30 -> 300% (sim, primeiro)
            5L to listOf(d(5)),          // só uma compra (não)
        )
        val s = ShoppingSuggestions.suggest(purchases, inList = emptySet(), today = today)
        assertEquals(listOf(4L, 2L), s.map { it.itemId })
    }

    @Test fun skipsItemsAlreadyInList() {
        val purchases = mapOf(4L to listOf(d(40), d(30)))
        assertTrue(ShoppingSuggestions.suggest(purchases, inList = setOf(4L), today = today).isEmpty())
    }

    @Test fun unorderedDates() {
        val s = ShoppingSuggestions.stats(1, listOf(d(10), d(40), d(25)), today)!!
        assertEquals(15.0, s.avgIntervalDays, 0.001)
        assertEquals(d(10), s.lastDate)
    }
}
