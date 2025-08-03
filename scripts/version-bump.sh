#!/bin/bash

# Version management script for Qiblah Finder
# Usage: ./scripts/version-bump.sh [major|minor|patch]

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to print colored output
print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if we're in the right directory
if [ ! -f "app/build.gradle" ]; then
    print_error "This script must be run from the project root directory"
    exit 1
fi

# Get current version from build.gradle
get_current_version() {
    grep "versionName" app/build.gradle | sed 's/.*versionName "\(.*\)"/\1/'
}

get_current_version_code() {
    grep "versionCode" app/build.gradle | sed 's/.*versionCode \([0-9]*\)/\1/'
}

# Update version in build.gradle
update_version() {
    local new_version=$1
    local new_version_code=$2
    
    # Update versionName
    sed -i.bak "s/versionName \".*\"/versionName \"$new_version\"/" app/build.gradle
    
    # Update versionCode
    sed -i.bak "s/versionCode [0-9]*/versionCode $new_version_code/" app/build.gradle
    
    # Remove backup files
    rm -f app/build.gradle.bak
}

# Bump version based on type
bump_version() {
    local bump_type=$1
    local current_version=$(get_current_version)
    local current_version_code=$(get_current_version_code)
    
    # Parse current version
    IFS='.' read -ra VERSION_PARTS <<< "$current_version"
    local major=${VERSION_PARTS[0]}
    local minor=${VERSION_PARTS[1]}
    local patch=${VERSION_PARTS[2]}
    
    local new_version
    local new_version_code=$((current_version_code + 1))
    
    case $bump_type in
        "major")
            new_version="$((major + 1)).0.0"
            ;;
        "minor")
            new_version="$major.$((minor + 1)).0"
            ;;
        "patch")
            new_version="$major.$minor.$((patch + 1))"
            ;;
        *)
            print_error "Invalid bump type. Use: major, minor, or patch"
            exit 1
            ;;
    esac
    
    print_status "Current version: $current_version (code: $current_version_code)"
    print_status "New version: $new_version (code: $new_version_code)"
    
    # Update build.gradle
    update_version "$new_version" "$new_version_code"
    
    # Create git tag
    local tag="v$new_version"
    git add app/build.gradle
    git commit -m "Bump version to $new_version"
    git tag -a "$tag" -m "Release $new_version"
    
    print_status "Version bumped to $new_version"
    print_status "Tag created: $tag"
    print_warning "Don't forget to push the tag: git push origin $tag"
}

# Main script
if [ $# -eq 0 ]; then
    print_error "Usage: $0 [major|minor|patch]"
    print_status "Examples:"
    print_status "  $0 patch  # 1.0.0 -> 1.0.1"
    print_status "  $0 minor  # 1.0.0 -> 1.1.0"
    print_status "  $0 major  # 1.0.0 -> 2.0.0"
    exit 1
fi

bump_type=$1
bump_version "$bump_type" 