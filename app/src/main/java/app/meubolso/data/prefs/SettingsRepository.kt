package app.meubolso.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    /** null = ainda não respondeu à pergunta dos dados de exemplo. */
    val sampleDataAnswered: Boolean = false,
    val monthStartDay: Int = 1,
    /** Cores do papel de parede (Android 12+). Padrão: verde do app. */
    val dynamicColor: Boolean = false,
    // Notificações
    val notifyBills: Boolean = true,
    val notifyLimits: Boolean = true,
    val notifyIncome: Boolean = true,
    val dailyReminder: Boolean = false,
    val dailyHour: Int = 21,
    val dailyMinute: Int = 0,
    /** Meta da poupança em centavos (0 = sem meta). */
    val savingsGoal: Long = 0,
    /** Pedir digital/senha do celular ao abrir o app. */
    val appLock: Boolean = false,
)

class SettingsRepository(private val context: Context) {
    private object K {
        val sampleAnswered = booleanPreferencesKey("sample_answered")
        val monthStartDay = intPreferencesKey("month_start_day")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val notifyBills = booleanPreferencesKey("notify_bills")
        val notifyLimits = booleanPreferencesKey("notify_limits")
        val notifyIncome = booleanPreferencesKey("notify_income")
        val dailyReminder = booleanPreferencesKey("daily_reminder")
        val dailyHour = intPreferencesKey("daily_hour")
        val dailyMinute = intPreferencesKey("daily_minute")
        val savingsGoal = longPreferencesKey("savings_goal")
        val appLock = booleanPreferencesKey("app_lock")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): AppSettings = settings.first()

    private fun Preferences.toSettings() = AppSettings(
        sampleDataAnswered = this[K.sampleAnswered] ?: false,
        monthStartDay = (this[K.monthStartDay] ?: 1).coerceIn(1, 28),
        dynamicColor = this[K.dynamicColor] ?: false,
        notifyBills = this[K.notifyBills] ?: true,
        notifyLimits = this[K.notifyLimits] ?: true,
        notifyIncome = this[K.notifyIncome] ?: true,
        dailyReminder = this[K.dailyReminder] ?: false,
        dailyHour = this[K.dailyHour] ?: 21,
        dailyMinute = this[K.dailyMinute] ?: 0,
        savingsGoal = this[K.savingsGoal] ?: 0,
        appLock = this[K.appLock] ?: false,
    )

    suspend fun setSampleAnswered() = context.dataStore.edit { it[K.sampleAnswered] = true }
    suspend fun setNotifyBills(on: Boolean) = context.dataStore.edit { it[K.notifyBills] = on }
    suspend fun setNotifyLimits(on: Boolean) = context.dataStore.edit { it[K.notifyLimits] = on }
    suspend fun setNotifyIncome(on: Boolean) = context.dataStore.edit { it[K.notifyIncome] = on }
    suspend fun setDailyReminder(on: Boolean, hour: Int, minute: Int) = context.dataStore.edit {
        it[K.dailyReminder] = on; it[K.dailyHour] = hour; it[K.dailyMinute] = minute
    }
    suspend fun setAppLock(on: Boolean) = context.dataStore.edit { it[K.appLock] = on }
    suspend fun setSavingsGoal(cents: Long) = context.dataStore.edit { it[K.savingsGoal] = cents.coerceAtLeast(0) }
    suspend fun setDynamicColor(on: Boolean) = context.dataStore.edit { it[K.dynamicColor] = on }
    suspend fun setMonthStartDay(day: Int) = context.dataStore.edit { it[K.monthStartDay] = day.coerceIn(1, 28) }
}
