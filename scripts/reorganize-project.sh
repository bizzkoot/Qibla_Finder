#!/bin/bash

# Qiblah Finder Project Reorganization Script
# This script helps reorganize the project structure for better maintainability

set -e  # Exit on any error

echo "🚀 Starting Qiblah Finder project reorganization..."

# Create new directory structure
echo "📁 Creating new directory structure..."

# Create docs directories
mkdir -p docs/guides
mkdir -p docs/technical
mkdir -p docs/security
mkdir -p docs/development

# Create assets directories
mkdir -p assets/images/icons
mkdir -p assets/images/screenshots
mkdir -p assets/releases

echo "✅ Directory structure created"

# Move documentation files
echo "📄 Moving documentation files..."

# Guides
echo "  Moving guides..."
mv IN_APP_UPDATE_IMPLEMENTATION.md docs/guides/ 2>/dev/null || echo "  ⚠️  IN_APP_UPDATE_IMPLEMENTATION.md not found or already moved"
mv GIT_PUSH_GUIDE.md docs/guides/ 2>/dev/null || echo "  ⚠️  GIT_PUSH_GUIDE.md not found or already moved"
mv RELEASE_GUIDE.md docs/guides/ 2>/dev/null || echo "  ⚠️  RELEASE_GUIDE.md not found or already moved"
mv GitHub_Steps.md docs/guides/ 2>/dev/null || echo "  ⚠️  GitHub_Steps.md not found or already moved"
mv GitHubWorkflow.md docs/guides/ 2>/dev/null || echo "  ⚠️  GitHubWorkflow.md not found or already moved"

# Technical documentation
echo "  Moving technical docs..."
mv Architecture.md docs/technical/ 2>/dev/null || echo "  ⚠️  Architecture.md not found or already moved"
mv Technical.md docs/technical/ 2>/dev/null || echo "  ⚠️  Technical.md not found or already moved"
mv UX.md docs/technical/ 2>/dev/null || echo "  ⚠️  UX.md not found or already moved"
mv Troubleshooting.md docs/technical/ 2>/dev/null || echo "  ⚠️  Troubleshooting.md not found or already moved"
mv SunCalibration.md docs/technical/ 2>/dev/null || echo "  ⚠️  SunCalibration.md not found or already moved"
mv AR.md docs/technical/ 2>/dev/null || echo "  ⚠️  AR.md not found or already moved"
mv COMPASS_CORE_REFACTOR_V2.md docs/technical/ 2>/dev/null || echo "  ⚠️  COMPASS_CORE_REFACTOR_V2.md not found or already moved"
mv Map_Performance_Optimization.md docs/technical/ 2>/dev/null || echo "  ⚠️  Map_Performance_Optimization.md not found or already moved"
mv ZoomMap.md docs/technical/ 2>/dev/null || echo "  ⚠️  ZoomMap.md not found or already moved"
mv Satellite_Map.md docs/technical/ 2>/dev/null || echo "  ⚠️  Satellite_Map.md not found or already moved"
mv Fallback.Webp_Lossy.md docs/technical/ 2>/dev/null || echo "  ⚠️  Fallback.Webp_Lossy.md not found or already moved"

# Security documentation
echo "  Moving security docs..."
mv SECURITY_SUMMARY.md docs/security/ 2>/dev/null || echo "  ⚠️  SECURITY_SUMMARY.md not found or already moved"
mv SIGNED_APK_SUMMARY_Secure.md docs/security/ 2>/dev/null || echo "  ⚠️  SIGNED_APK_SUMMARY_Secure.md not found or already moved"
mv GitHub_Secrets_Setup_Secure.md docs/security/ 2>/dev/null || echo "  ⚠️  GitHub_Secrets_Setup_Secure.md not found or already moved"

# Development documentation
echo "  Moving development docs..."
mv COMMIT_CONVENTIONS.md docs/development/ 2>/dev/null || echo "  ⚠️  COMMIT_CONVENTIONS.md not found or already moved"
mv Progress.md docs/development/ 2>/dev/null || echo "  ⚠️  Progress.md not found or already moved"

echo "✅ Documentation files moved"

# Move assets
echo "🖼️  Moving assets..."

# Move icons
echo "  Moving icons..."
if [ -d "Icon" ]; then
    mv Icon/* assets/images/icons/ 2>/dev/null || echo "  ⚠️  Some icon files could not be moved"
    rmdir Icon 2>/dev/null || echo "  ⚠️  Icon directory could not be removed"
else
    echo "  ℹ️  Icon directory not found"
fi

# Move screenshots
echo "  Moving screenshots..."
if [ -d "Screenshot" ]; then
    mv Screenshot/* assets/images/screenshots/ 2>/dev/null || echo "  ⚠️  Some screenshot files could not be moved"
    rmdir Screenshot 2>/dev/null || echo "  ⚠️  Screenshot directory could not be removed"
else
    echo "  ℹ️  Screenshot directory not found"
fi

# Move other images
echo "  Moving other images..."
mv master_icon.png assets/images/ 2>/dev/null || echo "  ⚠️  master_icon.png not found or already moved"

# Move release artifacts
echo "  Moving release artifacts..."
mv qiblafinder-release.apk assets/releases/ 2>/dev/null || echo "  ⚠️  qiblafinder-release.apk not found or already moved"

echo "✅ Assets moved"

# Create a new README in docs
echo "📝 Creating documentation README..."
cat > docs/README.md << 'EOF'
# Qiblah Finder Documentation

This directory contains all documentation for the Qiblah Finder project.

## Structure

- **guides/** - How-to guides and tutorials
- **technical/** - Technical documentation and specifications
- **security/** - Security-related documentation
- **development/** - Development guidelines and progress tracking

## Quick Links

### Guides
- [In-App Update Implementation](guides/IN_APP_UPDATE_IMPLEMENTATION.md)
- [Git Push Guide](guides/GIT_PUSH_GUIDE.md)
- [Release Guide](guides/RELEASE_GUIDE.md)
- [GitHub Steps](guides/GitHub_Steps.md)
- [GitHub Workflow](guides/GitHubWorkflow.md)

### Technical Documentation
- [Architecture](technical/Architecture.md)
- [Technical Specifications](technical/Technical.md)
- [User Experience](technical/UX.md)
- [Troubleshooting](technical/Troubleshooting.md)
- [Sun Calibration](technical/SunCalibration.md)
- [Augmented Reality](technical/AR.md)
- [Compass Core Refactor](technical/COMPASS_CORE_REFACTOR_V2.md)
- [Map Performance Optimization](technical/Map_Performance_Optimization.md)
- [Zoom Map](technical/ZoomMap.md)
- [Satellite Map](technical/Satellite_Map.md)
- [Fallback WebP Lossy](technical/Fallback.Webp_Lossy.md)

### Security
- [Security Summary](security/SECURITY_SUMMARY.md)
- [Signed APK Summary](security/SIGNED_APK_SUMMARY_Secure.md)
- [GitHub Secrets Setup](security/GitHub_Secrets_Setup_Secure.md)

### Development
- [Commit Conventions](development/COMMIT_CONVENTIONS.md)
- [Progress Tracking](development/Progress.md)
EOF

echo "✅ Documentation README created"

# Update main README to reference new structure
echo "📝 Updating main README..."
if [ -f "README.md" ]; then
    # Create a backup
    cp README.md README.md.backup
    echo "  📋 Main README backed up as README.md.backup"
fi

echo "✅ Project reorganization completed!"

echo ""
echo "🎉 Reorganization Summary:"
echo "  ✅ Created new directory structure"
echo "  ✅ Moved documentation to docs/"
echo "  ✅ Moved assets to assets/"
echo "  ✅ Created documentation README"
echo "  ✅ Backed up main README"
echo ""
echo "📋 Next Steps:"
echo "  1. Review the new structure"
echo "  2. Update any hardcoded paths in your code"
echo "  3. Test the build process"
echo "  4. Update the main README.md with new structure"
echo "  5. Commit the changes"
echo ""
echo "🔍 To review the new structure, run:"
echo "  tree -I 'node_modules|.git|.gradle|build'"
echo ""
echo "⚠️  Note: Some files may not have been moved if they didn't exist or were already moved."
echo "   Please check the output above for any warnings." 