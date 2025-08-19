package com.torto.trgym.database.dto

import kotlinx.serialization.Serializable


@Serializable
data class SupabaseBodyMeasurement(
    val id: String? = null,
    val user_id: String, // Snake case per Supabase
    val name: String,
    val value: Double,
    val unit: String,
    val measurement_date: String, // Snake case per Supabase - ISO timestamp
    val notes: String? = null,
    val created_at: String? = null, // Snake case per Supabase
    val updated_at: String? = null // Snake case per Supabase
)