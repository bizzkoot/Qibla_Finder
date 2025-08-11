# Map Direction Line: Implementation Analysis

## 1. Feature Request

This document analyzes the feasibility and user experience implications of a frequently requested feature: **drawing a line on the manual location map to indicate the direction of the Kaaba.**

## 2. Analysis

While seemingly intuitive, implementing this feature presents significant challenges related to accuracy, user experience, and technical complexity.

### 2.1. The Problem of Map Projections and Accuracy

The core issue lies in the difference between a 2D map and the 3D reality of the Earth's surface.

-   **Map Projection:** The app, like most web maps, uses the **Web Mercator projection**. This projection is excellent for preserving local angles and shapes, making it ideal for navigation at a city-level scale.
-   **The "Straight Line" Illusion:** A straight line drawn on a Mercator map is called a **rhumb line (or loxodrome)**. It represents a path of constant bearing but is **not** the shortest distance between two points on a globe.
-   **The True Path:** The actual shortest path between two points on Earth is a **great-circle path (or orthodrome)**. When projected onto a Mercator map, this path appears as a **curve**.

**Conclusion:** Drawing a simple straight line from the user's location to the Kaaba on the map would be **geographically incorrect and misleading**. For a religious application where accuracy is paramount, providing visually intuitive but incorrect guidance is a significant issue.

### 2.2. User Experience (UX) and Screen Purpose

The application's design intentionally separates tasks to create a clear and focused user flow.

-   **Manual Location Screen Purpose:** The primary goal of this screen is **input**. Its sole function is to allow the user to manually select their geographical coordinates with precision. The UI is centered around the task of panning, zooming, and placing the pin.
-   **Compass Screen Purpose:** The primary goal of the main compass screen is **output**. It takes the location data and provides the definitive, accurate Qibla direction in an intuitive, real-world format.

**Conclusion:** Adding a directional line to the map screen would blur the purpose of the two screens. It would mix the "input" task with an "output" display, potentially cluttering the UI and confusing the user's objective on that specific screen. The current flow (1. Set location, 2. See direction) is cleaner and more effective.

### 2.3. Technical Complexity

To implement this feature *correctly*, we would need to:
1.  Calculate the great-circle path between the user's location and the Kaaba.
2.  Project this curved path onto the 2D Mercator map canvas.
3.  Dynamically recalculate and redraw this curve every time the user pans or zooms the map.

**Conclusion:** This introduces significant implementation and computational complexity for a feature that is already handled more effectively and accurately by the main compass screen.

## 3. Final Decision

Based on the analysis above, the decision has been made **not to implement a direction line on the manual location map.**

The current approach is superior because:
-   **It Prioritizes Accuracy:** It avoids presenting a misleading "straight line" and instead directs the user to the compass, which is the proper tool for accurate, real-world orientation.
-   **It Provides a Better UX:** It maintains a clear separation of concerns, making the app easier to understand and use.
-   **It Avoids Unnecessary Complexity:** It focuses development effort on the best tool for the job (the compass and AR views) rather than implementing a complex and potentially confusing map overlay.
