# Qibla Finder - MVVM Architecture Design

## 1. Introduction

This document outlines the Model-View-ViewModel (MVVM) architecture for the Qibla Finder application. The application aims to provide an accurate and reliable Qibla direction for users. This architecture promotes separation of concerns, testability, and maintainability.

## 2. Architecture Overview

The application follows the MVVM (Model-View-ViewModel) architecture.

### 2.1. Model

The Model layer is responsible for data access and business logic. It encapsulates data sources, such as location services, sensor data, and any persistent storage.

*   **Responsibilities:**
    *   Data retrieval from various sources (e.g., `FusedLocationProviderClient`, `SensorManager`).
    *   Data caching and persistence (if needed).
    *   Business logic related to data manipulation and validation.
    *   Providing data to the ViewModel in a usable format (e.g., `Flow`).

*   **Key Components:**
    *   `LocationRepository`: Provides location data using `FusedLocationProviderClient`. Handles permission requests and provides `LocationState` updates.
    *   `SensorRepository`: Provides sensor data (orientation, magnetic field) using `SensorManager`. Provides `OrientationState` updates.
    *   Data classes for representing location and sensor data.

### 2.2. View

The View layer is responsible for displaying the UI and handling user interactions. It is passive and observes the ViewModel for data updates.

*   **Responsibilities:**
    *   Displaying data from the ViewModel.
    *   Handling user input and events.
    *   Updating the UI based on the ViewModel's state.
    *   Using Jetpack Compose for UI implementation.

*   **Key Components:**
    *   Compose UI: Implements the UI using Jetpack Compose.
    *   `Canvas` Composable: Renders the compass rose and Qibla needle.
    *   Status bar: Displays real-time information about location and compass status.
    *   Augmented Reality (AR) View: Renders a 3D arrow or Kaaba model in the live camera feed.
    *   Sun Calibration View: Allows the user to calibrate the compass using the sun's position.

### 2.3. ViewModel

The ViewModel layer acts as an intermediary between the View and the Model. It prepares data for the View and handles user interactions.

*   **Responsibilities:**
    *   Retrieving data from the Model.
    *   Transforming data into a format suitable for the View.
    *   Exposing data to the View via `StateFlow`.
    *   Handling user input and updating the Model.
    *   Managing the UI state.

*   **Key Components:**
    *   `QiblaViewModel`: Manages the app's state and business logic.
    *   `QiblaUiState`: A data class that encapsulates the entire UI state.
    *   `LocationState`: Represents the state of the location data.
    *   `OrientationState`: Represents the state of the orientation data.
    *   `CompassStatus`: Represents the status of the compass (OK, NEEDS_CALIBRATION, INTERFERENCE).

## 3. Data Flow

1.  The `QiblaViewModel` starts and requests location permissions.
2.  A `Flow` from `LocationRepository` provides `LocationState` updates.
3.  A `Flow` from `SensorRepository` combines `ROTATION_VECTOR` and `MAGNETIC_FIELD` data to provide `OrientationState` updates.
4.  The ViewModel uses `combine` on these flows. When the first valid location arrives, it calculates the `qiblaBearing`. It continuously updates the `orientationState`.
5.  The Compose UI collects the `QiblaUiState` `StateFlow` and renders the entire UI based on its contents.

## 4. State Management

The entire UI state is encapsulated in a single data class (`QiblaUiState`) to ensure consistency.

```kotlin
data class QiblaUiState(
    val locationState: LocationState = LocationState.Loading,
    val orientationState: OrientationState = OrientationState.Initializing,
    val qiblaBearing: Float? = null
)

sealed interface LocationState {
    object Loading : LocationState
    data class Available(val location: Location) : LocationState
    data class Error(val message: String) : LocationState
}

sealed interface OrientationState {
    object Initializing : OrientationState
    data class Available(
        val trueHeading: Float,
        val compassStatus: CompassStatus
    ) : OrientationState
}

enum class CompassStatus {
    OK,
    NEEDS_CALIBRATION,
    INTERFERENCE
}
```

## 5. Diagram

```mermaid
graph LR
    A[QiblaViewModel] --> B(QiblaUiState)
    B --> C[Compose UI]
    D[LocationRepository] -- Flow --> A
    E[SensorRepository] -- Flow --> A
    A --> F{combine}
    F --> G[qiblaBearing]
    F --> H[orientationState]
    D --> I(FusedLocationProviderClient)
    E --> J(SensorManager)
    I --> K((Location Data))
    J --> L((Sensor Data))
    K -- LocationState updates --> D
    L -- OrientationState updates --> E