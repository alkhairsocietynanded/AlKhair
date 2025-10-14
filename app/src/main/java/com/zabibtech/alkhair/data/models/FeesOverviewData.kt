package com.zabibtech.alkhair.data.models

data class FeesOverviewData(
    val totalStudents: Int = 0,
    val totalFees: Double = 0.0,
    val totalCollected: Double = 0.0,
    val totalDue: Double = 0.0,
    val unpaidCount: Int = 0,
    val classWiseCollected: Map<String, Double> = emptyMap()
)

