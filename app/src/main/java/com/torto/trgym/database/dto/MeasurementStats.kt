package com.torto.trgym.database.dto

data class MeasurementStats(
    val count: Int,
    val minValue: Double,
    val maxValue: Double,
    val averageValue: Double,
    val latestValue: Double,
    val latestUnit: String
)