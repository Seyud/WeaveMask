package io.github.seyud.weave.core.di

import android.annotation.SuppressLint
import android.content.Context
import android.text.method.LinkMovementMethod
import androidx.room.Room
import io.github.seyud.weave.core.AppContext
import io.github.seyud.weave.core.Const
import io.github.seyud.weave.core.data.SuLogDatabase
import io.github.seyud.weave.core.data.magiskdb.PolicyDao
import io.github.seyud.weave.core.data.magiskdb.SettingsDao
import io.github.seyud.weave.core.data.magiskdb.StringDao
import io.github.seyud.weave.core.ktx.deviceProtectedContext
import io.github.seyud.weave.core.repository.LogRepository
import io.github.seyud.weave.core.repository.ModuleRepoRepositoryImpl
import io.github.seyud.weave.core.repository.NetworkService
import io.noties.markwon.Markwon
import io.noties.markwon.utils.NoCopySpannableFactory

@SuppressLint("StaticFieldLeak")
object ServiceLocator {

    val deContext by lazy { AppContext.deviceProtectedContext }
    val timeoutPrefs by lazy { deContext.getSharedPreferences("su_timeout", 0) }

    // Preferences
    val settingsPrefs by lazy { AppContext.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    // Database
    val policyDB = PolicyDao()
    val settingsDB = SettingsDao()
    val stringDB = StringDao()
    val sulogDB by lazy { createSuLogDatabase(deContext).suLogDao() }
    val logRepo by lazy { LogRepository(sulogDB) }

    // Networking
    val okhttp by lazy { createOkHttpClient(AppContext) }
    val retrofit by lazy { createRetrofit(okhttp) }
    val markwon by lazy { createMarkwon(AppContext) }
    val networkService by lazy {
        NetworkService(
            createApiService(retrofit, Const.Url.INVALID_URL),
            createApiService(retrofit, Const.Url.GITHUB_API_URL),
        )
    }

    /** Module store (KernelSU Modules registry); one instance so the catalog is shared. */
    val moduleRepoRepository by lazy { ModuleRepoRepositoryImpl() }
}

private fun createSuLogDatabase(context: Context) =
    Room.databaseBuilder(context, SuLogDatabase::class.java, "sulogs.db")
        .addMigrations(SuLogDatabase.MIGRATION_1_2)
        .fallbackToDestructiveMigration(true)
        .build()

private fun createMarkwon(context: Context) =
    Markwon.builder(context).textSetter { textView, spanned, bufferType, onComplete ->
        textView.apply {
            movementMethod = LinkMovementMethod.getInstance()
            setSpannableFactory(NoCopySpannableFactory.getInstance())
            setText(spanned, bufferType)
            onComplete.run()
        }
    }.build()
