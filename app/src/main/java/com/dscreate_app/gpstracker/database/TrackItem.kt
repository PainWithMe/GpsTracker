package com.dscreate_app.gpstracker.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track")
data class TrackItem(
    @PrimaryKey(autoGenerate = true)
    val id: Int?,
    @ColumnInfo(name = "time")
    val time: Long,
    @ColumnInfo(name = "date")
    val date: Long, // String -> Long
    @ColumnInfo(name = "distance")
    val distance: Float,
    @ColumnInfo(name = "speed")
    val speed: Float,
    @ColumnInfo(name = "geo_points")
    val geoPoints: String,
    @ColumnInfo(name = "activity_type")
    val activityType: String,
    @ColumnInfo(name = "calories")
    val calories: Float,
    @ColumnInfo(name = "weight")
    val weight: String
)
