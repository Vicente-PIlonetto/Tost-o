package app.meubolso.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Snackbar com "Desfazer" que some sozinho depois de [millis] (padrão 3,5 s).
 * Retorna true se o usuário tocou em Desfazer.
 */
suspend fun SnackbarHostState.showUndo(message: String, millis: Long = 3500): Boolean = coroutineScope {
    currentSnackbarData?.dismiss()
    val timer = launch { delay(millis); currentSnackbarData?.dismiss() }
    val r = showSnackbar(message, actionLabel = "Desfazer", duration = SnackbarDuration.Indefinite)
    timer.cancel()
    r == SnackbarResult.ActionPerformed
}
