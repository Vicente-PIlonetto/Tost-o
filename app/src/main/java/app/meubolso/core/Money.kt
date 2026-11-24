package app.meubolso.core

import kotlin.math.abs

/** Valores monetários sempre em centavos (Long). Nunca Double. */
object Money {
    /** 123456 -> "R$ 1.234,56"; -500 -> "-R$ 5,00". */
    fun format(cents: Long, showPlus: Boolean = false): String {
        val sign = when {
            cents < 0 -> "-"
            showPlus && cents > 0 -> "+"
            else -> ""
        }
        return "${sign}R$ ${formatNumber(abs(cents))}"
    }

    /** 123456 -> "1.234,56" (sem símbolo, sem sinal). */
    fun formatNumber(absCents: Long): String {
        val reais = absCents / 100
        val cent = absCents % 100
        val grouped = reais.toString().reversed().chunked(3).joinToString(".").reversed()
        return "$grouped,${cent.toString().padStart(2, '0')}"
    }

    /**
     * Interpreta texto no formato brasileiro ("1.234,56", "12,5", "-3,20", "R$ 10") ou
     * decimal com ponto ("1234.56", vindo do OFX). Retorna null se inválido.
     */
    fun parse(text: String): Long? {
        var s = text.trim().replace("R$", "").replace(" ", "").replace(" ", "")
        if (s.isEmpty()) return null
        val negative = s.startsWith("-")
        s = s.removePrefix("-").removePrefix("+")
        val lastComma = s.lastIndexOf(',')
        val lastDot = s.lastIndexOf('.')
        val decimalSep: Char? = when {
            lastComma >= 0 && lastDot >= 0 -> if (lastComma > lastDot) ',' else '.'
            lastComma >= 0 -> ','
            // Ponto sozinho: decimal se tiver 1-2 dígitos depois, senão milhar ("1.234").
            lastDot >= 0 -> if (s.length - lastDot - 1 in 1..2 && s.count { it == '.' } == 1) '.' else null
            else -> null
        }
        val intPart: String
        val fracPart: String
        if (decimalSep != null) {
            val idx = s.lastIndexOf(decimalSep)
            intPart = s.substring(0, idx).filter { it.isDigit() }
            fracPart = s.substring(idx + 1)
        } else {
            intPart = s.filter { it.isDigit() || it == '.' || it == ',' }.filter { it.isDigit() }
            fracPart = ""
        }
        if (!s.all { it.isDigit() || it == '.' || it == ',' }) return null
        if (fracPart.any { !it.isDigit() }) return null
        val frac = (fracPart + "00").take(2)
        val reais = if (intPart.isEmpty()) 0L else intPart.toLongOrNull() ?: return null
        // Arredonda pelo terceiro dígito decimal, se houver.
        var cents = reais * 100 + frac.toLong()
        if (fracPart.length > 2 && fracPart[2] >= '5') cents += 1
        return if (negative) -cents else cents
    }

    /** Para o campo de digitação "estilo banco": só dígitos, que deslocam para os centavos. */
    fun fromDigits(digits: String): Long =
        digits.filter { it.isDigit() }.take(12).toLongOrNull() ?: 0L
}
