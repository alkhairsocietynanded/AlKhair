package com.aewsn.alkhair.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Custom Qualifier to identify the Application-wide Coroutine Scope.
 */
@Retention(AnnotationRetention.RUNTIME)
@Qualifier
annotation class ApplicationScope

/**
 * Hilt Module that provides the Application-wide Coroutine Scope.
 * This scope lives as long as the application is running.
 */
@Module
@InstallIn(SingletonComponent::class)
object CoroutinesModule {

    @ApplicationScope
    @Singleton
    @Provides
    fun provideApplicationScope(): CoroutineScope =
        // SupervisorJob ensures that if one background task fails, others can continue.
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}