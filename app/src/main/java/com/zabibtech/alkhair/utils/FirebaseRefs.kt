package com.zabibtech.alkhair.utils

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

object FirebaseRefs {
    val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseDatabase by lazy { FirebaseDatabase.getInstance() }

    val usersDb: DatabaseReference get() = db.reference.child("users")
    val attendanceRef: DatabaseReference get() = db.reference.child("attendance")
    val userAttendanceRef: DatabaseReference get() = db.reference.child("user_attendance")
    val dateAttendanceRef: DatabaseReference get() = db.reference.child("date_attendance")
    val feesRef: DatabaseReference get() = db.reference.child("fees")
    val salariesRef: DatabaseReference get() = db.reference.child("salary")
    val classesRef: DatabaseReference get() = db.reference.child("classes")
    val divisionsRef: DatabaseReference get() = db.reference.child("divisions")
    val homeworkRef: DatabaseReference get() = db.reference.child("homework") // "homeworks" se "homework" kar diya
    val announcementsRef: DatabaseReference get() = db.reference.child("announcement")
    val deletedRecordsRef: DatabaseReference get() = db.reference.child("deleted_records")
}
