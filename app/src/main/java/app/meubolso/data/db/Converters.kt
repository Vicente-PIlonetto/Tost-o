package app.meubolso.data.db

import androidx.room.TypeConverter
import java.time.LocalDate
import java.time.YearMonth

class Converters {
    @TypeConverter fun dateToLong(d: LocalDate?): Long? = d?.toEpochDay()
    @TypeConverter fun longToDate(v: Long?): LocalDate? = v?.let(LocalDate::ofEpochDay)

    @TypeConverter fun ymToString(ym: YearMonth?): String? = ym?.toString()
    @TypeConverter fun stringToYm(s: String?): YearMonth? = s?.let(YearMonth::parse)

    @TypeConverter fun txTypeToString(t: TxType?): String? = t?.name
    @TypeConverter fun stringToTxType(s: String?): TxType? = s?.let(TxType::valueOf)

    @TypeConverter fun pmToString(p: PaymentMethod?): String? = p?.name
    @TypeConverter fun stringToPm(s: String?): PaymentMethod? = s?.let { runCatching { PaymentMethod.valueOf(it) }.getOrNull() }

    @TypeConverter fun savingsKindToString(k: SavingsKind?): String? = k?.name
    @TypeConverter fun stringToSavingsKind(s: String?): SavingsKind? = s?.let(SavingsKind::valueOf)

    @TypeConverter fun walletKindToString(k: WalletKind?): String? = k?.name
    @TypeConverter fun stringToWalletKind(s: String?): WalletKind? = s?.let { runCatching { WalletKind.valueOf(it) }.getOrNull() }

    @TypeConverter fun originToString(o: TxOrigin?): String? = o?.name
    @TypeConverter fun stringToOrigin(s: String?): TxOrigin? = s?.let { runCatching { TxOrigin.valueOf(it) }.getOrNull() }
}
