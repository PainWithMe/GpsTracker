package com.dscreate_app.gpstracker.location

data class GeoPointItem(
    val latitude: Double,
    val longitude: Double,
    val time: String // Время в формате ISO 8601 (например, 2023-12-21T12:30:05Z)
)
