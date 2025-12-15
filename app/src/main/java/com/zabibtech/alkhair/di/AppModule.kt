package com.zabibtech.alkhair.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.zabibtech.alkhair.data.datastore.AppDataStore
import com.zabibtech.alkhair.data.datastore.ShiftDataStore
import com.zabibtech.alkhair.data.local.dao.ClassDao
import com.zabibtech.alkhair.data.local.dao.DivisionDao
import com.zabibtech.alkhair.data.local.database.AppDatabase
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
        ).build()
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

}