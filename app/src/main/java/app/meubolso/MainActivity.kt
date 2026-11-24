package app.meubolso

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.meubolso.notify.Notifications
import app.meubolso.security.AppLock
import app.meubolso.ui.LockScreen
import app.meubolso.ui.MainScreen
import app.meubolso.ui.OpenRequest
import app.meubolso.ui.theme.MeuBolsoTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : FragmentActivity() {
    private val openRequest = MutableStateFlow<OpenRequest?>(null)

    /** true = precisa desbloquear (só tem efeito se o bloqueio estiver ligado em Ajustes). */
    private val locked = MutableStateFlow(true)
    private val lockError = MutableStateFlow<String?>(null)
    private var backgroundAt = 0L
    private var authInProgress = false

    private fun Intent.toRequest(): OpenRequest? = getStringExtra(Notifications.EXTRA_OPEN)?.let {
        OpenRequest(it, getLongExtra(Notifications.EXTRA_ID, -1), getStringExtra(Notifications.EXTRA_PERIOD))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.toRequest()?.let { openRequest.value = it }
    }

    private fun unlock() {
        if (authInProgress) return
        authInProgress = true
        AppLock.authenticate(this, "Desbloquear o Tostão",
            onSuccess = { authInProgress = false; lockError.value = null; locked.value = false },
            onFail = { authInProgress = false; lockError.value = it })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRequest.value = intent.toRequest()
        else locked.value = savedInstanceState.getBoolean("locked", true)
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val req by openRequest.collectAsStateWithLifecycle()
            val isLocked by locked.collectAsStateWithLifecycle()
            val err by lockError.collectAsStateWithLifecycle()
            val s = settings
            // Com o bloqueio desligado, o app fica "destravado"; assim, ao ligar em Ajustes
            // (já confirmado com a digital), não tranca na mesma hora.
            LaunchedEffect(s?.appLock) { if (s?.appLock == false) locked.value = false }
            // Esconde o conteúdo na tela de apps recentes quando o bloqueio está ligado.
            if (s?.appLock == true) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            MeuBolsoTheme(dynamicColor = s?.dynamicColor == true) {
                when {
                    s == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
                    else -> AnimatedContent(
                        s.appLock && isLocked,
                        transitionSpec = { (fadeIn(tween(300)) + scaleIn(initialScale = 0.96f)).togetherWith(fadeOut(tween(200))) },
                        label = "lock",
                    ) { showLock ->
                        if (showLock) LockScreen(err, ::unlock)
                        else MainScreen(req, onRequestHandled = { openRequest.value = null })
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("locked", locked.value)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) backgroundAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (backgroundAt != 0L && SystemClock.elapsedRealtime() - backgroundAt > AppLock.RELOCK_AFTER_MS) locked.value = true
        backgroundAt = 0L
    }
}
