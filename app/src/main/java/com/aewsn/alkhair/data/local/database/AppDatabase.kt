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
        PendingDeletion::class,
        com.aewsn.alkhair.data.models.Leave::class,
        com.aewsn.alkhair.data.models.Syllabus::class,
        com.aewsn.alkhair.data.models.Subject::class,
        com.aewsn.alkhair.data.models.Timetable::class,
        com.aewsn.alkhair.data.models.Exam::class,
        com.aewsn.alkhair.data.models.Result::class
    ],
    version = 13, // v13: Paginated attendance sync to fetch all records beyond 1000 limit
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
    abstract fun leaveDao(): com.aewsn.alkhair.data.local.dao.LeaveDao
    abstract fun syllabusDao(): com.aewsn.alkhair.data.local.dao.SyllabusDao
    abstract fun subjectDao(): com.aewsn.alkhair.data.local.dao.SubjectDao
    abstract fun timetableDao(): com.aewsn.alkhair.data.local.dao.TimetableDao
    abstract fun resultDao(): com.aewsn.alkhair.data.local.dao.ResultDao


}