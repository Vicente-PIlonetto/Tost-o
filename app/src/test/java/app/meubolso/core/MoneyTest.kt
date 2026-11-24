package app.meubolso.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {
    @Test fun formatsBrazilian() {
        assertEquals("R$ 1.234,56", Money.format(123456))
        assertEquals("R$ 0,05", Money.format(5))
        assertEquals("-R$ 12,00", Money.format(-1200))
        assertEquals("+R$ 1.000.000,00", Money.format(100000000, showPlus = true))
    }

    @Test fun parsesBrazilianAndDot() {
        assertEquals(123456L, Money.parse("1.234,56"))
        assertEquals(123456L, Money.parse("R$ 1.234,56"))
        assertEquals(1250L, Money.parse("12,5"))
        assertEquals(-4590L, Money.parse("-45.90"))
        assertEquals(123400L, Money.parse("1.234"))
        assertEquals(1000L, Money.parse("10"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse(""))
    }

    @Test fun digitsShiftIntoCents() {
        assertEquals(1234L, Money.fromDigits("R$ 12,34"))
        assertEquals(0L, Money.fromDigits("R$ 0,00"))
        assertEquals(12345L, Money.fromDigits("R$ 12,345"))
    }
}
