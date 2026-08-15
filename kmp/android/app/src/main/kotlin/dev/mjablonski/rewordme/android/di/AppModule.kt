package dev.mjablonski.rewordme.android.di

import dev.mjablonski.rewordme.android.platform.AndroidConfigStore
import dev.mjablonski.rewordme.android.platform.AndroidProviderAvailability
import dev.mjablonski.rewordme.android.platform.Clipboard
import dev.mjablonski.rewordme.android.platform.ClipboardWriter
import dev.mjablonski.rewordme.android.platform.KeystoreApiKeyStore
import dev.mjablonski.rewordme.data.RewordService
import dev.mjablonski.rewordme.domain.ApiKeyStore
import dev.mjablonski.rewordme.domain.ConfigStore
import dev.mjablonski.rewordme.domain.ModelListing
import dev.mjablonski.rewordme.domain.ModelResolver
import dev.mjablonski.rewordme.domain.ProviderAvailability
import dev.mjablonski.rewordme.domain.Rewording
import dev.mjablonski.rewordme.domain.usecase.HasApiKeyUseCase
import dev.mjablonski.rewordme.domain.usecase.ListModelsUseCase
import dev.mjablonski.rewordme.domain.usecase.LoadConfigUseCase
import dev.mjablonski.rewordme.domain.usecase.RewordTextUseCase
import dev.mjablonski.rewordme.domain.usecase.SaveApiKeyUseCase
import dev.mjablonski.rewordme.domain.usecase.UpdateConfigUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.bind
import org.koin.dsl.binds
import org.koin.dsl.module

val IO_DISPATCHER = named("io")

/**
 * The composition root, as Koin modules instead of the desktop's
 * AppDependencies class: singletons for stores and services, factories
 * for use cases (they are stateless and cheap).
 */
val appModule = module {
    single<CoroutineDispatcher>(IO_DISPATCHER) { Dispatchers.IO }

    single<ConfigStore> { AndroidConfigStore(androidContext()) }
    single<ApiKeyStore> { KeystoreApiKeyStore(androidContext()) }
    single<ProviderAvailability> { AndroidProviderAvailability() }
    single<Clipboard> { ClipboardWriter(androidContext()) }
    single { ModelResolver() }

    // The account-CLI and managed-local gateways keep their unsupported
    // defaults: AndroidProviderAvailability never offers those providers.
    single { RewordService() } binds arrayOf(Rewording::class, ModelListing::class)

    factory { RewordTextUseCase(get(), get(), get(), get(), get(IO_DISPATCHER)) }
    factory { ListModelsUseCase(get(), get(), get(), get(IO_DISPATCHER)) }
    factory { LoadConfigUseCase(get(), get(IO_DISPATCHER)) }
    factory { UpdateConfigUseCase(get(), get(), get(IO_DISPATCHER)) }
    factory { HasApiKeyUseCase(get(), get(IO_DISPATCHER)) }
    factory { SaveApiKeyUseCase(get(), get(), get(IO_DISPATCHER)) }
}
