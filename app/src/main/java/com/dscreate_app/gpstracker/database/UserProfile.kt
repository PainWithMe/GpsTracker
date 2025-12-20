package com.dscreate_app.gpstracker.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey(autoGenerate = true)
    val id: Int? = null,
    val name: String,
    val weight: Float
)
