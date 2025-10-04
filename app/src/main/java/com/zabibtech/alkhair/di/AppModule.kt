package com.zabibtech.alkhair.di

import android.content.Context
import com.google.gson.Gson
import com.zabibtech.alkhair.data.datastore.AppDataStore
import com.zabibtech.alkhair.data.datastore.ClassDivisionStore
import com.zabibtech.alkhair.data.datastore.UserStore
import com.zabibtech.alkhair.data.repository.ClassManagerRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Suppress("unused")
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideUserStore(
        appDataStore: AppDataStore,
        gson: Gson
    ): UserStore {
        return UserStore(appDataStore, gson)
    }

    @Provides
    @Singleton
    fun provideAppDataStore(
        @ApplicationContext context: Context,
        gson: Gson
    ): AppDataStore {
        return AppDataStore(context, gson)
    }

    @Provides
    @Singleton
    fun provideClassDivisionStore(
        appDataStore: AppDataStore,
        classManagerRepository: ClassManagerRepository
    ): ClassDivisionStore {
        return ClassDivisionStore(appDataStore, classManagerRepository)
    }
}