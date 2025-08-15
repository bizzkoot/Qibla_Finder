package com.bizzkoot.qiblafinder.ui.location

import kotlin.math.*

/**
 * High-precision coordinate transformation utilities
 * Uses double-precision arithmetic throughout to eliminate cumulative errors
 */
object PrecisionCoordinateTransformer {
    
    // WGS84 ellipsoid constants for maximum accuracy
    private const val EARTH_RADIUS = 6378137.0 // WGS84 semi-major axis in meters
    private const val EARTH_FLATTENING = 1.0 / 298.257223563 // WGS84 flattening
    private const val EARTH_SEMI_MINOR_AXIS = EARTH_RADIUS * (1.0 - EARTH_FLATTENING)
    private const val EARTH_ECCENTRICITY_SQUARED = 2.0 * EARTH_FLATTENING - EARTH_FLATTENING * EARTH_FLATTENING
    
    // Mercator projection constants
    private const val ORIGIN_SHIFT = PI * EARTH_RADIUS
    private const val TILE_SIZE = 256.0 // Double precision tile size
    
    // Coordinate validation constants
    private const val MAX_LATITUDE = 85.05112877980659 // Maximum latitude for Mercator projection
    private const val MIN_LATITUDE = -85.05112877980659
    private const val MAX_LONGITUDE = 180.0
    private const val MIN_LONGITUDE = -180.0
    
    /**
     * High-precision conversion from geographic to tile coordinates
     * Uses double precision throughout to eliminate cumulative errors
     */
    fun latLngToHighPrecisionTile(lat: Double, lng: Double, zoom: Int): Pair<Double, Double> {
        // Validate input coordinates
        require(lat in MIN_LATITUDE..MAX_LATITUDE) { 
            "Latitude $lat is outside valid range [$MIN_LATITUDE, $MAX_LATITUDE]" 
        }
        require(lng in MIN_LONGITUDE..MAX_LONGITUDE) { 
            "Longitude $lng is outside valid range [$MIN_LONGITUDE, $MAX_LONGITUDE]" 
        }
        require(zoom in 0..20) { "Zoom level $zoom is outside valid range [0, 20]" }
        
        val latRad = Math.toRadians(lat)
        val tileCount = 2.0.pow(zoom.toDouble())
        
        // High-precision tile X calculation
        val tileX = (lng + 180.0) / 360.0 * tileCount
        
        // High-precision tile Y calculation using exact Mercator projection
        val tileY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * tileCount
        
        return Pair(tileX, tileY)
    }
    
    /**
     * Inverse transformation with maintained precision
     * Converts high-precision tile coordinates back to geographic coordinates
     */
    fun highPrecisionTileToLatLng(tileX: Double, tileY: Double, zoom: Int): Pair<Double, Double> {
        require(zoom in 0..20) { "Zoom level $zoom is outside valid range [0, 20]" }
        
        val tileCount = 2.0.pow(zoom.toDouble())
        
        // Validate tile coordinates are within bounds for this zoom level
        require(tileX >= 0.0 && tileX <= tileCount) { 
            "TileX $tileX is outside valid range [0, $tileCount] for zoom $zoom" 
        }
        require(tileY >= 0.0 && tileY <= tileCount) { 
            "TileY $tileY is outside valid range [0, $tileCount] for zoom $zoom" 
        }
        
        // High-precision longitude calculation
        val lng = tileX / tileCount * 360.0 - 180.0
        
        // High-precision latitude calculation using inverse Mercator projection
        val latRad = atan(sinh(PI * (1.0 - 2.0 * tileY / tileCount)))
        val lat = Math.toDegrees(latRad)
        
        return Pair(lat, lng)
    }
    
    /**
     * Converts geographic coordinates to Web Mercator meters with high precision
     */
    fun latLngToWebMercator(lat: Double, lng: Double): Pair<Double, Double> {
        require(lat in MIN_LATITUDE..MAX_LATITUDE) { 
            "Latitude $lat is outside valid range [$MIN_LATITUDE, $MAX_LATITUDE]" 
        }
        require(lng in MIN_LONGITUDE..MAX_LONGITUDE) { 
            "Longitude $lng is outside valid range [$MIN_LONGITUDE, $MAX_LONGITUDE]" 
        }
        
        val x = lng * ORIGIN_SHIFT / 180.0
        var y = ln(tan((90.0 + lat) * PI / 360.0)) / (PI / 180.0)
        y = y * ORIGIN_SHIFT / 180.0
        
        return Pair(x, y)
    }
    
    /**
     * Converts Web Mercator meters to geographic coordinates with high precision
     */
    fun webMercatorToLatLng(x: Double, y: Double): Pair<Double, Double> {
        val lng = (x / ORIGIN_SHIFT) * 180.0
        var lat = (y / ORIGIN_SHIFT) * 180.0
        
        lat = 180.0 / PI * (2.0 * atan(exp(lat * PI / 180.0)) - PI / 2.0)
        
        return Pair(lat, lng)
    }
    
    /**
     * Calculates the great circle distance between two points using the Haversine formula
     * Returns distance in meters with high precision
     */
    fun calculateGreatCircleDistance(
        lat1: Double, lng1: Double, 
        lat2: Double, lng2: Double
    ): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lng1Rad = Math.toRadians(lng1)
        val lat2Rad = Math.toRadians(lat2)
        val lng2Rad = Math.toRadians(lng2)
        
        val deltaLat = lat2Rad - lat1Rad
        val deltaLng = lng2Rad - lng1Rad
        
        val a = sin(deltaLat / 2.0).pow(2.0) + 
                cos(lat1Rad) * cos(lat2Rad) * sin(deltaLng / 2.0).pow(2.0)
        val c = 2.0 * atan2(sqrt(a), sqrt(1.0 - a))
        
        return EARTH_RADIUS * c
    }
    
    /**
     * Calculates the bearing from point 1 to point 2 in degrees
     * Returns bearing in range [0, 360) degrees
     */
    fun calculateBearing(
        lat1: Double, lng1: Double, 
        lat2: Double, lng2: Double
    ): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val deltaLngRad = Math.toRadians(lng2 - lng1)
        
        val y = sin(deltaLngRad) * cos(lat2Rad)
        val x = cos(lat1Rad) * sin(lat2Rad) - sin(lat1Rad) * cos(lat2Rad) * cos(deltaLngRad)
        
        val bearingRad = atan2(y, x)
        var bearingDeg = Math.toDegrees(bearingRad)
        
        // Normalize to [0, 360) range
        bearingDeg = (bearingDeg + 360.0) % 360.0
        
        return bearingDeg
    }
    
    /**
     * Validates coordinate precision by checking for reasonable decimal places
     */
    fun validateCoordinatePrecision(lat: Double, lng: Double): Boolean {
        // Check that coordinates have reasonable precision (not NaN, Infinite, etc.)
        if (!lat.isFinite() || !lng.isFinite()) return false
        
        // Check that coordinates are within valid geographic bounds
        if (lat < MIN_LATITUDE || lat > MAX_LATITUDE) return false
        if (lng < MIN_LONGITUDE || lng > MAX_LONGITUDE) return false
        
        // Check precision - coordinates should have meaningful decimal places
        val latString = lat.toString()
        val lngString = lng.toString()
        
        // Ensure we have at least 6 decimal places for GPS precision
        val latDecimals = latString.substringAfter(".", "").length
        val lngDecimals = lngString.substringAfter(".", "").length
        
        return latDecimals >= 6 && lngDecimals >= 6
    }
    
    /**
     * Rounds coordinates to a specified precision to prevent excessive decimal places
     */
    fun roundCoordinatesToPrecision(lat: Double, lng: Double, decimalPlaces: Int = 10): Pair<Double, Double> {
        val factor = 10.0.pow(decimalPlaces.toDouble())
        val roundedLat = (lat * factor).roundToLong() / factor
        val roundedLng = (lng * factor).roundToLong() / factor
        return Pair(roundedLat, roundedLng)
    }
    
    /**
     * Calculates the tile size in meters at a given latitude and zoom level
     */
    fun calculateTileSizeInMeters(latitude: Double, zoomLevel: Int): Double {
        val latRad = Math.toRadians(latitude)
        val earthCircumference = 2.0 * PI * EARTH_RADIUS * cos(latRad)
        val tilesAtZoom = 2.0.pow(zoomLevel.toDouble())
        return earthCircumference / tilesAtZoom
    }
    
    /**
     * Calculates the pixel resolution in meters per pixel
     */
    fun calculatePixelResolution(latitude: Double, zoomLevel: Int, digitalZoom: Double = 1.0): Double {
        val tileSizeInMeters = calculateTileSizeInMeters(latitude, zoomLevel)
        return tileSizeInMeters / (TILE_SIZE * digitalZoom)
    }
}