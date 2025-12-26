package com.zabibtech.alkhair.data.models

@com.google.firebase.database.IgnoreExtraProperties
data class FeesOverviewData(
    val totalStudents: Int = 0,
    val totalFees: Double = 0.0,
    val totalCollected: Double = 0.0,
    val totalDiscount: Double = 0.0, // Added for discount
    val totalDue: Double = 0.0,
    val unpaidCount: Int = 0,
    val classWiseCollected: Map<String, Double> = emptyMap()
)
