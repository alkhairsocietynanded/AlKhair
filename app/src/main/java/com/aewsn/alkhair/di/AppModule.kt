package com.aewsn.alkhair.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.aewsn.alkhair.data.datastore.AppDataStore
import com.aewsn.alkhair.data.datastore.ShiftDataStore
import com.aewsn.alkhair.data.local.dao.ClassDao
import com.aewsn.alkhair.data.local.dao.DivisionDao
import com.aewsn.alkhair.data.local.database.AppDatabase
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
    fun provideAppDataStore(
        @ApplicationContext context: Context,
        gson: Gson
    ): AppDataStore {
        return AppDataStore(context, gson)
    }

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideShiftDataStore(
        appDataStore: AppDataStore
    ): ShiftDataStore {
        return ShiftDataStore(appDataStore)
    }

    @Provides
    @Singleton
    fun provideSharedPreferences(@ApplicationContext context: Context): android.content.SharedPreferences {
        return context.getSharedPreferences("alkhair_prefs", Context.MODE_PRIVATE)
    }

    // =============================
    // Room Database
    // =============================
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "alkhair_database.db"
        ).fallbackToDestructiveMigration(true)
            .build()
    }

    // =============================
    // DAOs
    // =============================
    @Provides
    @Singleton
    fun provideAnnouncementDao(db: AppDatabase) = db.announcementDao()

    @Provides
    @Singleton
    fun provideClassDao(db: AppDatabase): ClassDao = db.classDao()

    @Provides
    @Singleton
    fun provideDivisionDao(db: AppDatabase): DivisionDao = db.divisionDao()

    @Provides
    @Singleton
    fun provideUserDao(db: AppDatabase) = db.userDao()

    @Provides
    @Singleton
    fun provideFeesDao(db: AppDatabase) = db.feesDao()

    @Provides
    @Singleton
    fun provideAttendanceDao(db: AppDatabase) = db.attendanceDao()

    @Provides
    @Singleton
    fun provideSalaryDao(db: AppDatabase) = db.salaryDao()

    @Provides
    @Singleton
    fun provideHomeworkDao(db: AppDatabase) = db.homeworkDao()

    @Provides
    @Singleton
    fun providePendingDeletionDao(db: AppDatabase) = db.pendingDeletionDao()

    @Provides
    @Singleton
    fun provideLeaveDao(db: AppDatabase) = db.leaveDao()

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): androidx.work.WorkManager {
        return androidx.work.WorkManager.getInstance(context)
    }

    @Provides
    @Singleton
    fun provideN8nApiService(): com.aewsn.alkhair.data.remote.n8nApiService {
        val n8nBaseUrl = "https://revlum4e5b.app.n8n.cloud/"
        
        val client = okhttp3.OkHttpClient.Builder()
            .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
            .build()

        return retrofit2.Retrofit.Builder()
            .baseUrl(n8nBaseUrl)
            .client(client)
            .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
            .build()
            .create(com.aewsn.alkhair.data.remote.n8nApiService::class.java)
    }

    @Provides
    @Singleton
    fun provideSyllabusDao(db: AppDatabase) = db.syllabusDao()
}