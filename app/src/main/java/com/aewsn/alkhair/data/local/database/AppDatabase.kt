package com.aewsn.alkhair.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.aewsn.alkhair.data.local.dao.AnnouncementDao
import com.aewsn.alkhair.data.local.dao.AttendanceDao
import com.aewsn.alkhair.data.local.dao.ClassDao
import com.aewsn.alkhair.data.local.dao.DivisionDao
import com.aewsn.alkhair.data.local.dao.FeesDao
import com.aewsn.alkhair.data.local.dao.HomeworkDao
import com.aewsn.alkhair.data.local.dao.SalaryDao
import com.aewsn.alkhair.data.local.dao.UserDao
import com.aewsn.alkhair.data.local.dao.PendingDeletionDao
import com.aewsn.alkhair.data.models.Announcement
import com.aewsn.alkhair.data.models.Attendance
import com.aewsn.alkhair.data.models.ClassModel
import com.aewsn.alkhair.data.models.DivisionModel
import com.aewsn.alkhair.data.models.FeesModel
import com.aewsn.alkhair.data.models.Homework
import com.aewsn.alkhair.data.models.SalaryModel
import com.aewsn.alkhair.data.models.User
import com.aewsn.alkhair.data.models.PendingDeletion

@Database(
    entities = [
        User::class,
        FeesModel::class,
        Attendance::class,
        SalaryModel::class,
        Announcement::class,
        Homework::class,
        ClassModel::class,
        DivisionModel::class,
        PendingDeletion::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun feesDao(): FeesDao
    abstract fun attendanceDao(): AttendanceDao
    abstract fun salaryDao(): SalaryDao
    abstract fun announcementDao(): AnnouncementDao
    abstract fun homeworkDao(): HomeworkDao
    abstract fun classDao(): ClassDao
    abstract fun divisionDao(): DivisionDao
    abstract fun pendingDeletionDao(): PendingDeletionDao

}