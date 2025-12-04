package com.zabibtech.alkhair.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.zabibtech.alkhair.data.local.dao.AnnouncementDao
import com.zabibtech.alkhair.data.local.dao.AttendanceDao
import com.zabibtech.alkhair.data.local.dao.ClassDao
import com.zabibtech.alkhair.data.local.dao.DivisionDao
import com.zabibtech.alkhair.data.local.dao.FeesDao
import com.zabibtech.alkhair.data.local.dao.HomeworkDao
import com.zabibtech.alkhair.data.local.dao.SalaryDao
import com.zabibtech.alkhair.data.local.dao.UserDao
import com.zabibtech.alkhair.data.models.Announcement
import com.zabibtech.alkhair.data.models.Attendance
import com.zabibtech.alkhair.data.models.ClassModel
import com.zabibtech.alkhair.data.models.DivisionModel
import com.zabibtech.alkhair.data.models.FeesModel
import com.zabibtech.alkhair.data.models.Homework
import com.zabibtech.alkhair.data.models.SalaryModel
import com.zabibtech.alkhair.data.models.User

@Database(
    entities = [
        User::class,
        FeesModel::class,
        Attendance::class,
        SalaryModel::class,
        Announcement::class,
        Homework::class,
        ClassModel::class,
        DivisionModel::class
    ],
    version = 1,
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
}