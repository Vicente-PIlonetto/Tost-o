package app.meubolso.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.meubolso.AppContainer
import app.meubolso.container

/** Cria ViewModels com o container de dependências (DI manual). */
@Composable
inline fun <reified VM : ViewModel> appViewModel(key: String? = null, crossinline create: (AppContainer) -> VM): VM {
    val c = LocalContext.current.container
    return viewModel(key = key, factory = viewModelFactory { initializer { create(c) } })
}
