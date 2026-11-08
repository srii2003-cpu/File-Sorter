package com.narasa.imageorganiser.di

import android.content.Context
import com.narasa.imageorganiser.data.crypto.DatabaseKeyStore
import com.narasa.imageorganiser.data.db.DatabaseProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    @Provides
    @Singleton
    fun keyStore(@ApplicationContext context: Context) = DatabaseKeyStore(context, "index_key_wrap", "index_key.bin")

    @Provides
    @Singleton
    fun databaseProvider(@ApplicationContext context: Context, keyStore: DatabaseKeyStore) =
        DatabaseProvider(context, keyStore, "index.db")
}
