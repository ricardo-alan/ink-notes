package app.inknotes.ui.common

import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.CreationExtras
import app.inknotes.InkApplication

val CreationExtras.inkApp: InkApplication get() = this[APPLICATION_KEY] as InkApplication
