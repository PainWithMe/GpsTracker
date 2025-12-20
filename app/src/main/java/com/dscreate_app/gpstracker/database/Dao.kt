package com.dscreate_app.gpstracker.database

import androidx.room.*
import androidx.room.Dao
import kotlinx.coroutines.flow.Flow

@Dao
interface Dao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(trackItem: TrackItem)

    @Query("SELECT * FROM track ORDER BY id DESC")
    fun getAllTracks(): Flow<List<TrackItem>>

    @Delete
    suspend fun deleteTrack(trackItem: TrackItem)

    // User Profile
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserProfile(userProfile: UserProfile)

    @Query("SELECT * FROM user_profile LIMIT 1")
    fun getUserProfile(): Flow<UserProfile?>

    // Queries for total stats
    @Query("SELECT SUM(distance) FROM track")
    fun getTotalDistance(): Flow<Float?>

    @Query("SELECT SUM(time) FROM track")
    fun getTotalTime(): Flow<Long?>

    @Query("SELECT SUM(calories) FROM track")
    fun getTotalCalories(): Flow<Float?>

    @Query("SELECT AVG(speed) FROM track")
    fun getAverageSpeed(): Flow<Float?>

    // Queries for personal records (overall)
    @Query("SELECT MAX(distance) FROM track")
    fun getMaxDistance(): Flow<Float?>

    @Query("SELECT MAX(time) FROM track")
    fun getMaxTime(): Flow<Long?>

    @Query("SELECT MAX(speed) FROM track")
    fun getMaxSpeed(): Flow<Float?>

    @Query("SELECT MAX(calories) FROM track")
    fun getMaxCalories(): Flow<Float?>

    // Queries for stats by activity type
    @Query("SELECT SUM(distance) FROM track WHERE activity_type = :activityType")
    fun getTotalDistanceByType(activityType: String): Flow<Float?>

    @Query("SELECT SUM(time) FROM track WHERE activity_type = :activityType")
    fun getTotalTimeByType(activityType: String): Flow<Long?>

    @Query("SELECT SUM(calories) FROM track WHERE activity_type = :activityType")
    fun getTotalCaloriesByType(activityType: String): Flow<Float?>

    @Query("SELECT AVG(speed) FROM track WHERE activity_type = :activityType")
    fun getAverageSpeedByType(activityType: String): Flow<Float?>

    // Queries for personal records by activity type
    @Query("SELECT MAX(distance) FROM track WHERE activity_type = :activityType")
    fun getMaxDistanceByType(activityType: String): Flow<Float?>

    @Query("SELECT MAX(time) FROM track WHERE activity_type = :activityType")
    fun getMaxTimeByType(activityType: String): Flow<Long?>

    @Query("SELECT MAX(speed) FROM track WHERE activity_type = :activityType")
    fun getMaxSpeedByType(activityType: String): Flow<Float?>

    @Query("SELECT MAX(calories) FROM track WHERE activity_type = :activityType")
    fun getMaxCaloriesByType(activityType: String): Flow<Float?>

    // Query for Bar Chart (Most Frequent Activity)
    @Query("SELECT activity_type as activityType, COUNT(id) as count FROM track GROUP BY activity_type")
    fun getActivityCount(): Flow<List<ActivityCount>>

    // Query for Bar Chart by date
    @Query("SELECT * FROM track WHERE activity_type = :activityType AND date BETWEEN :startDate AND :endDate")
    fun getTracksForPeriod(activityType: String, startDate: Long, endDate: Long): Flow<List<TrackItem>>
}