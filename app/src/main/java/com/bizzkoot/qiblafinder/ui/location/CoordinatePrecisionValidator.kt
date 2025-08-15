package com.bizzkoot.qiblafinder.ui.location

import kotlin.math.*

/**
 * Coordinate precision validation utilities
 * Ensures mathematical accuracy throughout the coordinate transformation pipeline
 */
object CoordinatePrecisionValidator {
    
    // Precision thresholds
    private const val MIN_DECIMAL_PLACES = 6
    private const val MAX_DECIMAL_PLACES = 15
    private const val EPSILON = 1e-10 // For floating point comparisons
    
    // Geographic bounds
    private const val MAX_LATITUDE = 85.05112877980659
    private const val MIN_LATITUDE = -85.05112877980659
    private const val MAX_LONGITUDE = 180.0
    private const val MIN_LONGITUDE = -180.0
    
    /**
     * Validates that coordinates meet precision requirements
     */
    data class PrecisionValidationResult(
        val isValid: Boolean,
        val errors: List<String>,
        val warnings: List<String>,
        val precisionScore: Double // 0.0 to 1.0, where 1.0 is perfect precision
    )
    
    /**
     * Comprehensive validation of coordinate precision
     */
    fun validateCoordinatePrecision(
        latitude: Double, 
        longitude: Double,
        context: String = "coordinate"
    ): PrecisionValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var precisionScore = 1.0
        
        // Check for finite values
        if (!latitude.isFinite()) {
            errors.add("$context latitude is not finite: $latitude")
            precisionScore -= 0.5
        }
        
        if (!longitude.isFinite()) {
            errors.add("$context longitude is not finite: $longitude")
            precisionScore -= 0.5
        }
        
        // Check geographic bounds
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            errors.add("$context latitude $latitude is outside valid range [$MIN_LATITUDE, $MAX_LATITUDE]")
            precisionScore -= 0.3
        }
        
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            errors.add("$context longitude $longitude is outside valid range [$MIN_LONGITUDE, $MAX_LONGITUDE]")
            precisionScore -= 0.3
        }
        
        // Check decimal precision
        val latPrecision = getDecimalPlaces(latitude)
        val lngPrecision = getDecimalPlaces(longitude)
        
        if (latPrecision < MIN_DECIMAL_PLACES) {
            warnings.add("$context latitude has low precision: $latPrecision decimal places (minimum $MIN_DECIMAL_PLACES)")
            precisionScore -= 0.1
        }
        
        if (lngPrecision < MIN_DECIMAL_PLACES) {
            warnings.add("$context longitude has low precision: $lngPrecision decimal places (minimum $MIN_DECIMAL_PLACES)")
            precisionScore -= 0.1
        }
        
        if (latPrecision > MAX_DECIMAL_PLACES) {
            warnings.add("$context latitude has excessive precision: $latPrecision decimal places (maximum $MAX_DECIMAL_PLACES)")
            precisionScore -= 0.05
        }
        
        if (lngPrecision > MAX_DECIMAL_PLACES) {
            warnings.add("$context longitude has excessive precision: $lngPrecision decimal places (maximum $MAX_DECIMAL_PLACES)")
            precisionScore -= 0.05
        }
        
        // Check for suspicious values (like exactly zero or whole numbers in geographic context)
        if (abs(latitude) < EPSILON && abs(longitude) < EPSILON) {
            warnings.add("$context appears to be at null island (0,0) - verify this is intentional")
            precisionScore -= 0.1
        }
        
        return PrecisionValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            precisionScore = maxOf(0.0, precisionScore)
        )
    }
    
    /**
     * Validates tile coordinate precision
     */
    fun validateTileCoordinatePrecision(
        tileX: Double, 
        tileY: Double, 
        zoomLevel: Int,
        context: String = "tile coordinate"
    ): PrecisionValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var precisionScore = 1.0
        
        // Check for finite values
        if (!tileX.isFinite()) {
            errors.add("$context tileX is not finite: $tileX")
            precisionScore -= 0.5
        }
        
        if (!tileY.isFinite()) {
            errors.add("$context tileY is not finite: $tileY")
            precisionScore -= 0.5
        }
        
        // Check tile bounds for zoom level
        val maxTileCoord = 2.0.pow(zoomLevel.toDouble())
        
        if (tileX < 0.0 || tileX > maxTileCoord) {
            errors.add("$context tileX $tileX is outside valid range [0, $maxTileCoord] for zoom $zoomLevel")
            precisionScore -= 0.3
        }
        
        if (tileY < 0.0 || tileY > maxTileCoord) {
            errors.add("$context tileY $tileY is outside valid range [0, $maxTileCoord] for zoom $zoomLevel")
            precisionScore -= 0.3
        }
        
        // Check precision relative to zoom level
        val requiredPrecision = calculateRequiredTilePrecision(zoomLevel)
        val tilePrecisionX = getDecimalPlaces(tileX)
        val tilePrecisionY = getDecimalPlaces(tileY)
        
        if (tilePrecisionX < requiredPrecision) {
            warnings.add("$context tileX precision ($tilePrecisionX) may be insufficient for zoom $zoomLevel (recommended: $requiredPrecision)")
            precisionScore -= 0.1
        }
        
        if (tilePrecisionY < requiredPrecision) {
            warnings.add("$context tileY precision ($tilePrecisionY) may be insufficient for zoom $zoomLevel (recommended: $requiredPrecision)")
            precisionScore -= 0.1
        }
        
        return PrecisionValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            precisionScore = maxOf(0.0, precisionScore)
        )
    }
    
    /**
     * Validates transformation accuracy by round-trip testing
     */
    fun validateTransformationAccuracy(
        originalLat: Double, 
        originalLng: Double, 
        zoomLevel: Int
    ): PrecisionValidationResult {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var precisionScore = 1.0
        
        try {
            // Forward transformation
            val (tileX, tileY) = PrecisionCoordinateTransformer.latLngToHighPrecisionTile(
                originalLat, originalLng, zoomLevel
            )
            
            // Backward transformation
            val (reconstructedLat, reconstructedLng) = PrecisionCoordinateTransformer.highPrecisionTileToLatLng(
                tileX, tileY, zoomLevel
            )
            
            // Calculate transformation error
            val latError = abs(originalLat - reconstructedLat)
            val lngError = abs(originalLng - reconstructedLng)
            
            // Acceptable error thresholds based on zoom level
            val acceptableError = calculateAcceptableTransformationError(zoomLevel)
            
            if (latError > acceptableError) {
                errors.add("Latitude transformation error ($latError) exceeds acceptable threshold ($acceptableError)")
                precisionScore -= 0.3
            } else if (latError > acceptableError * 0.5) {
                warnings.add("Latitude transformation error ($latError) is approaching threshold ($acceptableError)")
                precisionScore -= 0.1
            }
            
            if (lngError > acceptableError) {
                errors.add("Longitude transformation error ($lngError) exceeds acceptable threshold ($acceptableError)")
                precisionScore -= 0.3
            } else if (lngError > acceptableError * 0.5) {
                warnings.add("Longitude transformation error ($lngError) is approaching threshold ($acceptableError)")
                precisionScore -= 0.1
            }
            
            // Check for precision loss
            val originalLatPrecision = getDecimalPlaces(originalLat)
            val reconstructedLatPrecision = getDecimalPlaces(reconstructedLat)
            val precisionLoss = originalLatPrecision - reconstructedLatPrecision
            
            if (precisionLoss > 2) {
                warnings.add("Significant precision loss in latitude: $precisionLoss decimal places")
                precisionScore -= 0.05
            }
            
        } catch (e: Exception) {
            errors.add("Transformation failed: ${e.message}")
            precisionScore = 0.0
        }
        
        return PrecisionValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            warnings = warnings,
            precisionScore = maxOf(0.0, precisionScore)
        )
    }
    
    /**
     * Gets the number of decimal places in a Double value
     */
    private fun getDecimalPlaces(value: Double): Int {
        if (!value.isFinite()) return 0
        
        val valueString = value.toString()
        val decimalIndex = valueString.indexOf('.')
        
        return if (decimalIndex >= 0) {
            val afterDecimal = valueString.substring(decimalIndex + 1)
            // Remove trailing zeros and scientific notation
            afterDecimal.trimEnd('0').replace(Regex("[eE].*"), "").length
        } else {
            0
        }
    }
    
    /**
     * Calculates required tile precision based on zoom level
     */
    private fun calculateRequiredTilePrecision(zoomLevel: Int): Int {
        // Higher zoom levels require more precision
        return when {
            zoomLevel <= 5 -> 3
            zoomLevel <= 10 -> 6
            zoomLevel <= 15 -> 8
            else -> 10
        }
    }
    
    /**
     * Calculates acceptable transformation error based on zoom level
     */
    private fun calculateAcceptableTransformationError(zoomLevel: Int): Double {
        // Error tolerance decreases with higher zoom levels
        return when {
            zoomLevel <= 5 -> 1e-4   // ~11 meters at equator
            zoomLevel <= 10 -> 1e-6  // ~0.11 meters at equator  
            zoomLevel <= 15 -> 1e-8  // ~0.001 meters at equator
            else -> 1e-10            // Sub-millimeter precision
        }
    }
    
    /**
     * Validates that two coordinates are equivalent within acceptable precision
     */
    fun areCoordinatesEquivalent(
        lat1: Double, lng1: Double,
        lat2: Double, lng2: Double,
        toleranceMeters: Double = 1.0
    ): Boolean {
        val distance = PrecisionCoordinateTransformer.calculateGreatCircleDistance(lat1, lng1, lat2, lng2)
        return distance <= toleranceMeters
    }
    
    /**
     * Generates a precision report for debugging
     */
    fun generatePrecisionReport(
        coordinates: List<Pair<Double, Double>>,
        zoomLevel: Int
    ): String {
        val report = StringBuilder()
        report.appendLine("=== Coordinate Precision Report ===")
        report.appendLine("Zoom Level: $zoomLevel")
        report.appendLine("Total Coordinates: ${coordinates.size}")
        report.appendLine()
        
        coordinates.forEachIndexed { index, (lat, lng) ->
            val validation = validateCoordinatePrecision(lat, lng, "coordinate $index")
            val transformationValidation = validateTransformationAccuracy(lat, lng, zoomLevel)
            
            report.appendLine("Coordinate $index: ($lat, $lng)")
            report.appendLine("  Precision Score: ${validation.precisionScore}")
            report.appendLine("  Transformation Score: ${transformationValidation.precisionScore}")
            
            if (validation.errors.isNotEmpty()) {
                report.appendLine("  Errors: ${validation.errors.joinToString("; ")}")
            }
            
            if (validation.warnings.isNotEmpty()) {
                report.appendLine("  Warnings: ${validation.warnings.joinToString("; ")}")
            }
            
            report.appendLine()
        }
        
        return report.toString()
    }
}