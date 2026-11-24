package app.meubolso.ui.shopping

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.vector.ImageVector

internal fun Modifier.verticalScrollCompat(): Modifier = composed { verticalScroll(rememberScrollState()) }

internal val Icons.Filled.MoreVertCompat: ImageVector get() = Icons.Filled.MoreVert
