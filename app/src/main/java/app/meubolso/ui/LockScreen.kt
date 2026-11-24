package app.meubolso.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.meubolso.ui.components.floating
import app.meubolso.ui.theme.BrandGreen
import app.meubolso.ui.theme.BrandGreenDark
import app.meubolso.ui.theme.CoinGold

@Composable
fun LockScreen(error: String?, onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BrandGreen, BrandGreenDark))),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.padding(32.dp)) {
            Box(
                Modifier.floating().size(104.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Lock, null, tint = CoinGold, modifier = Modifier.size(52.dp)) }
            Spacer(Modifier.height(24.dp))
            Text("Tostão", style = MaterialTheme.typography.headlineMedium, color = Color.White)
            Text("Seu bolso está trancado", style = MaterialTheme.typography.bodyLarge, color = Color.White.copy(alpha = 0.8f))
            if (error != null) Text(error, Modifier.padding(top = 12.dp), color = Color(0xFFFFDAD6),
                style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onUnlock,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = BrandGreen),
            ) {
                Icon(Icons.Filled.Fingerprint, null)
                Spacer(Modifier.size(8.dp))
                Text("Desbloquear")
            }
        }
    }
}
