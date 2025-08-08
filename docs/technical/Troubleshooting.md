# Qibla Application: Troubleshooting Guide

This document provides a guide for users to troubleshoot common issues they might encounter with the Qibla application. The app will link to these solutions from a "Help" or "FAQ" section.

## 1. Compass is Inaccurate or Not Moving

This is the most common issue, usually caused by magnetic interference.

**Symptoms:**
*   The compass needle jumps around erratically.
*   The needle points in a direction you know is wrong.
*   The needle doesn't move when you turn the phone.

**Solutions:**

1.  **Calibrate the Compass:**
    *   **How:** Move your phone in a figure-8 pattern several times. This allows the device's sensors to get a clear sense of the Earth's magnetic field.
    *   **When:** Do this whenever the app shows the `🔄 Calibrating...` or `⚠️ Interference` status.

2.  **Avoid Magnetic Interference:**
    *   **What:** Move away from metal objects and electronics. Common sources of interference include:
        *   Metal desks or table legs
        *   Computers and monitors
        *   Other phones or tablets
        *   Magnetic phone cases or mounts (remove the case)
        *   Steel-frame buildings or reinforced concrete

3.  **Restart the App:** Close the application completely and reopen it.

## 2. "Interference Detected" Warning Persists

If the `⚠️ Interference` warning does not go away even after moving to a clear area.

**Solutions:**

1.  **Perform the Figure-8 Calibration:** Do this again for at least 15-20 seconds.
2.  **Check Your Phone Case:** Ensure your phone case does not contain any magnets. Remove it and see if the warning disappears.
3.  **Restart Your Phone:** A full device restart can sometimes reset faulty sensor states.

## 3. Location is Inaccurate or "Loading..."

The app needs your location to calculate the Qibla.

**Symptoms:**
*   The status bar shows `Location: Loading...` for a long time.
*   The status bar shows a very large accuracy radius, like `📶 WiFi (±100m)`.

**Solutions:**

1.  **Go Outdoors:** For the best accuracy, get a clear view of the sky. GPS works best outdoors.
2.  **Enable High Accuracy Location:**
    *   Go to your Android Settings -> Location.
    *   Ensure "Use location" is turned ON.
    *   Check App Permissions and ensure the Qibla app has permission to access your location.
    *   Enable "Google Location Accuracy" to allow the use of Wi-Fi and mobile networks for a faster, though less precise, initial fix.
3.  **Turn Wi-Fi and Bluetooth On:** Even if not connected, Android can use Wi-Fi and Bluetooth signals to get a faster location fix.

## 4. AR (Augmented Reality) View is Not Working

**Symptoms:**
*   The AR button is grayed out or disabled.
*   The camera feed appears, but the AR object (arrow/Kaaba) does not.

**Solutions:**

1.  **Check Device Compatibility:** Ensure your device supports ARCore. The app should handle this gracefully, but you can check Google's official list of supported devices.
2.  **Install/Update ARCore:** Go to the Google Play Store and search for "Google Play Services for AR". Make sure it is installed and up to date.
3.  **Scan Your Environment:** Move your phone around slowly, pointing it at textured surfaces (like a rug, a wall with pictures, etc.). AR needs to detect feature points to understand the space. Avoid pointing it at blank white walls or reflective surfaces.

## 5. Sun Calibration is Not Working

**Symptoms:**
*   The "Sun Calibration" button is disabled.
*   You cannot see the sun in the camera view.

**Solutions:**

1.  **Wait for a Clear Day:** This feature requires the sun to be clearly visible. It will not work at night or on very overcast days.
2.  **Check Camera Permissions:** Ensure the app has permission to use the camera in your phone's settings.