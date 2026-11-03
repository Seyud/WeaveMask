package io.github.seyud.weave.arch

import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.seyud.weave.core.Info
import io.github.seyud.weave.core.di.ServiceLocator
import io.github.seyud.weave.ui.MainViewModel
import io.github.seyud.weave.ui.deny.DenyListViewModel
import io.github.seyud.weave.ui.home.HomeViewModel
import io.github.seyud.weave.ui.install.InstallViewModel
import io.github.seyud.weave.ui.log.LogViewModel
import io.github.seyud.weave.ui.module.ModuleViewModel
import io.github.seyud.weave.ui.module.state.ModulePreferencesRepository
import io.github.seyud.weave.ui.modulerepo.ModuleRepoViewModel
import io.github.seyud.weave.ui.settings.SettingsViewModel
import io.github.seyud.weave.ui.superuser.SuperuserViewModel
import io.github.seyud.weave.ui.surequest.SuRequestViewModel

interface ViewModelHolder : LifecycleOwner, ViewModelStoreOwner {

    val viewModel: BaseViewModel

    fun startObserveLiveData() {
        viewModel.uiEvents.observe(this, this::onUiEventDispatched)
        Info.isConnected.observe(this, viewModel::onNetworkChanged)
    }

    /**
     * Called for all [UiEvent]s published by the associated viewModel.
     */
    fun onUiEventDispatched(event: UiEvent) {}
}

/**
 * 类型安全的 ViewModel 工厂（对齐上游 apk-ng 的 viewModelFactory DSL 写法）：
 * 编译期检查构造器签名，无反射、无 unchecked cast。
 */
val VMFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { MainViewModel() }
    initializer { HomeViewModel(ServiceLocator.networkService) }
    initializer { LogViewModel(ServiceLocator.logRepo) }
    initializer { ModuleRepoViewModel() }
    initializer { ModuleViewModel(ModulePreferencesRepository(ServiceLocator.settingsPrefs)) }
    initializer { SuperuserViewModel(ServiceLocator.policyDB) }
    initializer { InstallViewModel(ServiceLocator.networkService, ServiceLocator.markwon) }
    initializer { SuRequestViewModel(ServiceLocator.policyDB, ServiceLocator.timeoutPrefs) }
    initializer { DenyListViewModel() }
    initializer { SettingsViewModel() }
}

inline fun <reified VM : ViewModel> ViewModelHolder.viewModel() =
    lazy(LazyThreadSafetyMode.NONE) {
        ViewModelProvider(this, VMFactory)[VM::class.java]
    }
