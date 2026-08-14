# Proposed Folder Structure for Qiblah Finder

## Current Issues
- Too many documentation files in root directory
- Mixed file types (docs, binaries, configs, assets)
- Inconsistent naming conventions
- Large files (APK, images) in root
- No clear separation of concerns

## Recommended Structure

```
Qiblah_Finder/
├── 📁 docs/                          # All documentation
│   ├── 📁 guides/                    # How-to guides
│   │   ├── IN_APP_UPDATE_IMPLEMENTATION.md
│   │   ├── GIT_PUSH_GUIDE.md
│   │   ├── RELEASE_GUIDE.md
│   │   ├── GitHub_Steps.md
│   │   └── GitHubWorkflow.md
│   ├── 📁 technical/                 # Technical documentation
│   │   ├── Architecture.md
│   │   ├── Technical.md
│   │   ├── UX.md
│   │   ├── Troubleshooting.md
│   │   ├── SunCalibration.md
│   │   ├── AR.md
│   │   ├── COMPASS_CORE_REFACTOR_V2.md
│   │   ├── Map_Performance_Optimization.md
│   │   ├── ZoomMap.md
│   │   ├── Satellite_Map.md
│   │   └── Fallback.Webp_Lossy.md
│   ├── 📁 security/                  # Security-related docs
│   │   ├── SECURITY_SUMMARY.md
│   │   ├── SIGNED_APK_SUMMARY_Secure.md
│   │   └── GitHub_Secrets_Setup_Secure.md
│   ├── 📁 development/               # Development guidelines
│   │   ├── COMMIT_CONVENTIONS.md
│   │   └── Progress.md
│   └── README.md                     # Main project README
├── 📁 assets/                        # Static assets
│   ├── 📁 images/                    # Images and icons
│   │   ├── 📁 icons/                 # App icons
│   │   │   └── [Icon folder contents]
│   │   ├── 📁 screenshots/           # App screenshots
│   │   │   └── [Screenshot folder contents]
│   │   └── master_icon.png
│   └── 📁 releases/                  # Release artifacts
│       └── qiblafinder-release.apk
├── 📁 scripts/                       # Build and utility scripts
│   └── test-release-system.sh
├── 📁 .github/                       # GitHub workflows and configs
│   ├── 📁 workflows/
├── 📁 app/                           # Main application code
│   ├── 📁 src/
│   ├── build.gradle
│   └── qiblafinder-release-key.jks
├── 📁 gradle/                        # Gradle wrapper
├── 📁 build/                         # Build outputs
├── 📁 .gradle/                       # Gradle cache
├── 📁 .kilocode/                     # IDE-specific files
├── 📁 .git/                          # Git repository
├── .gitignore                        # Git ignore rules
├── .cursorrules                      # Cursor IDE rules
├── build.gradle                      # Root build file
├── settings.gradle                   # Gradle settings
├── gradle.properties                  # Gradle properties
├── gradlew                           # Gradle wrapper script
├── gradlew.bat                       # Gradle wrapper script (Windows)
├── gradlew.bak                       # Backup gradle wrapper
├── .DS_Store                         # macOS system file
└── .log                              # Build logs
```

## Benefits of This Structure

### 1. **Improved Navigation**
- Clear separation of documentation by purpose
- Logical grouping of related files
- Easier to find specific information

### 2. **Better Maintainability**
- Reduced root directory clutter
- Consistent naming conventions
- Organized asset management

### 3. **Enhanced Collaboration**
- Clear documentation structure
- Separated concerns
- Easier onboarding for new developers

### 4. **Professional Appearance**
- Clean root directory
- Industry-standard organization
- Better project presentation

## Implementation Steps

### Phase 1: Create New Structure
1. Create new directories: `docs/`, `assets/`, `assets/images/`, `assets/releases/`
2. Move documentation files to appropriate subdirectories
3. Move assets to `assets/` directory
4. Move APK to `assets/releases/`

### Phase 2: Update References
1. Update any hardcoded paths in documentation
2. Update CI/CD workflows if needed
3. Update README.md with new structure

### Phase 3: Clean Up
1. Remove unnecessary files from root
2. Update .gitignore if needed
3. Test build process

## File Naming Conventions

### Documentation
- Use kebab-case for file names: `in-app-update-implementation.md`
- Use descriptive names that indicate content
- Group related files in appropriate subdirectories

### Assets
- Use descriptive names for images
- Maintain consistent naming patterns
- Organize by type and purpose

## Migration Checklist

- [ ] Create new directory structure
- [ ] Move documentation files
- [ ] Move asset files
- [ ] Move release artifacts
- [ ] Update any hardcoded references
- [ ] Test build process
- [ ] Update README.md
- [ ] Commit changes with descriptive message
- [ ] Update team documentation 