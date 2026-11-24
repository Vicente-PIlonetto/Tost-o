package app.meubolso.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

val PtBr: Locale = Locale.forLanguageTag("pt-BR")

object Dates {
    private val full = DateTimeFormatter.ofPattern("dd/MM/yyyy", PtBr)
    private val short = DateTimeFormatter.ofPattern("dd/MM", PtBr)

    /** A semana do app começa no domingo. */
    val FirstDayOfWeek: DayOfWeek = DayOfWeek.SUNDAY

    fun format(d: LocalDate): String = d.format(full)
    fun formatShort(d: LocalDate): String = d.format(short)

    fun parse(text: String): LocalDate? = runCatching { LocalDate.parse(text.trim(), full) }.getOrNull()

    /** "Hoje", "Ontem" ou "qua., 12/11/2026". */
    fun dayHeader(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (d) {
        today -> "Hoje"
        today.minusDays(1) -> "Ontem"
        else -> {
            val dow = d.dayOfWeek.getDisplayName(TextStyle.FULL, PtBr).replaceFirstChar { it.uppercase() }
            "$dow, ${format(d)}"
        }
    }

    fun monthName(ym: YearMonth): String {
        val name = ym.month.getDisplayName(TextStyle.FULL, PtBr).replaceFirstChar { it.uppercase() }
        return "$name de ${ym.year}"
    }

    fun monthShort(ym: YearMonth): String =
        ym.month.getDisplayName(TextStyle.SHORT, PtBr).replace(".", "").replaceFirstChar { it.uppercase() }

    /** Dia do mês ajustado para meses curtos (dia 31 em fevereiro vira o último dia). */
    fun dayIn(ym: YearMonth, day: Int): LocalDate = ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))
}

/**
 * Mês financeiro: começa no dia [startDay] (1..28) do mês [ym] e vai até a véspera do mesmo dia
 * no mês seguinte. Com startDay = 1, é o mês do calendário.
 */
data class FinancialMonth(val ym: YearMonth, val startDay: Int = 1) {
    val start: LocalDate get() = Dates.dayIn(ym, startDay)
    /** Exclusivo. */
    val endExclusive: LocalDate get() = Dates.dayIn(ym.plusMonths(1), startDay)
    val endInclusive: LocalDate get() = endExclusive.minusDays(1)

    operator fun contains(d: LocalDate): Boolean = !d.isBefore(start) && d.isBefore(endExclusive)

    fun next() = copy(ym = ym.plusMonths(1))
    fun previous() = copy(ym = ym.minusMonths(1))

    val label: String get() = if (startDay == 1) Dates.monthName(ym)
    else "${Dates.monthName(ym)} (${Dates.formatShort(start)} a ${Dates.formatShort(endInclusive)})"

    companion object {
        fun of(date: LocalDate, startDay: Int): FinancialMonth {
            val sd = startDay.coerceIn(1, 28)
            val ym = YearMonth.from(date)
            return if (date.dayOfMonth >= sd) FinancialMonth(ym, sd) else FinancialMonth(ym.minusMonths(1), sd)
        }
    }
}
