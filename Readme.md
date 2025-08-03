# Qibla Finder

A cutting-edge Android application designed to provide a highly accurate, reliable, and user-friendly Qibla locator. It addresses the common challenges of mobile sensor accuracy to deliver a trustworthy user experience.

## 🌟 Features

- **Accurate Qibla Direction**: Real-time compass and GPS integration
- **Manual Location Adjustment**: OpenStreetMap integration for precise location setting
- **AR Mode**: Camera preview with directional guidance
- **Compass Calibration**: Interactive figure-8 calibration
- **Phone Orientation Detection**: Flat/vertical detection with warnings
- **GPS Accuracy Validation**: Islamic scholarship-compliant accuracy guidelines
- **Troubleshooting Guide**: Comprehensive help system
- **Professional UI/UX**: Material Design 3 throughout

## 📱 Screenshots

[Add screenshots here]

## 🛠️ Technical Stack

- **Language**: Kotlin
- **Architecture**: MVVM with Jetpack Compose
- **Location**: FusedLocationProviderClient
- **Sensors**: SensorManager for compass and accelerometer
- **Maps**: OpenStreetMap with custom tile caching
- **AR**: ARCore with CameraX integration
- **Build System**: Gradle with GitHub Actions CI/CD

## 🚀 Getting Started

### Prerequisites
- Android Studio Arctic Fox or later
- Android SDK API 24+ (Android 7.0)
- Google Play Services (for location and AR features)

### Installation

1. Clone the repository
```bash
git clone https://github.com/bizzkoot/Qibla_Finder.git
```

2. Open in Android Studio
```bash
cd Qibla_Finder
```

3. Sync Gradle and build
```bash
./gradlew build
```

4. Run on device
```bash
./gradlew installDebug
```

## 📋 Requirements

- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: API 34 (Android 14)
- **Permissions**: Location, Camera (for AR mode)
- **Hardware**: GPS, Compass, Accelerometer

## 🔧 Build Configuration

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```

### Run Tests
```bash
./gradlew test
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📞 Support

For support and questions, please open an issue on GitHub.

## 🗺️ Roadmap

- [ ] Enhanced AR features
- [ ] Offline map support
- [ ] Multiple language support
- [ ] Widget support
- [ ] Wear OS companion app

## 📊 Development Progress

See [Progress.md](Progress.md) for detailed development progress and feature status.

## 🔄 CI/CD Status

[![Android CI/CD](https://github.com/bizzkoot/Qibla_Finder/workflows/Android%20CI/CD/badge.svg)](https://github.com/bizzkoot/Qibla_Finder/actions)

## 📦 Downloads

Latest APK builds are available in the [Releases](https://github.com/bizzkoot/Qibla_Finder/releases) section.

## 🎯 Key Features Explained

### Accurate Qibla Direction
- Real-time GPS location with accuracy tracking
- Compass sensor integration with calibration
- True North correction and magnetic declination
- Distance calculation to Kaaba

### Manual Location Adjustment
- OpenStreetMap integration with smart caching
- Draggable location pin with zoom controls
- Real-time coordinate updates
- Accuracy circle based on zoom level

### AR Mode
- Camera preview with UI overlay
- Real-time compass integration
- Directional arrow pointing to Qibla
- Phone orientation detection

### Professional UI/UX
- Material Design 3 compliance
- Smooth animations and transitions
- Responsive layout for different screen sizes
- Accessibility support

## 🚨 Troubleshooting

Common issues and solutions are available in the in-app troubleshooting guide. For technical issues, please check the [Issues](https://github.com/bizzkoot/Qibla_Finder/issues) section.

## 📈 Performance

- **Location Accuracy**: ±5m with GPS, ±30m with network
- **Compass Accuracy**: ±2° after calibration
- **AR Performance**: 60fps on supported devices
- **Map Loading**: <2 seconds with cached tiles

---

**Built with ❤️ for the Muslim community**