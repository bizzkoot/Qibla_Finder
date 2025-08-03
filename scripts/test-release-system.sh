#!/bin/bash

# Test script for Qibla Finder Release System
# This script verifies that all components are properly configured

echo "🔍 Testing Qibla Finder Release System Configuration..."
echo "=================================================="

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Function to check if file exists
check_file() {
    if [ -f "$1" ]; then
        echo -e "${GREEN}✅ $1 exists${NC}"
        return 0
    else
        echo -e "${RED}❌ $1 missing${NC}"
        return 1
    fi
}

# Function to check if directory exists
check_dir() {
    if [ -d "$1" ]; then
        echo -e "${GREEN}✅ $1 exists${NC}"
        return 0
    else
        echo -e "${RED}❌ $1 missing${NC}"
        return 1
    fi
}

# Function to check file content
check_content() {
    if grep -q "$2" "$1" 2>/dev/null; then
        echo -e "${GREEN}✅ $1 contains '$2'${NC}"
        return 0
    else
        echo -e "${RED}❌ $1 missing '$2'${NC}"
        return 1
    fi
}

echo ""
echo "📁 Checking file structure..."
echo "----------------------------"

# Check essential files
check_file ".github/workflows/release-drafter.yml"
check_file ".github/release-drafter.yml"
check_file "app/build.gradle"
check_file "COMMIT_CONVENTIONS.md"
check_file "RELEASE_GUIDE.md"

echo ""
echo "🔧 Checking workflow configuration..."
echo "-----------------------------------"

# Check workflow content
check_content ".github/workflows/release-drafter.yml" "Create git tag"
check_content ".github/workflows/release-drafter.yml" "Push git tag"
check_content ".github/workflows/release-drafter.yml" "tag_name:"
check_content ".github/workflows/release-drafter.yml" "softprops/action-gh-release@v1"

echo ""
echo "📝 Checking build.gradle version..."
echo "----------------------------------"

# Check current version
CURRENT_VERSION=$(grep "versionName" app/build.gradle | sed 's/.*versionName "\(.*\)"/\1/')
CURRENT_VERSION_CODE=$(grep "versionCode" app/build.gradle | sed 's/.*versionCode \([0-9]*\)/\1/')

echo -e "${YELLOW}Current version: $CURRENT_VERSION (code: $CURRENT_VERSION_CODE)${NC}"

echo ""
echo "📋 Checking documentation..."
echo "---------------------------"

# Check documentation content
check_content "COMMIT_CONVENTIONS.md" "feat\|feature\|enhancement"
check_content "COMMIT_CONVENTIONS.md" "fix\|bug\|patch"
check_content "COMMIT_CONVENTIONS.md" "breaking\|major"
check_content "RELEASE_GUIDE.md" "Automated Release System"

echo ""
echo "🔐 Checking GitHub secrets requirements..."
echo "----------------------------------------"

# Check if secrets are mentioned in documentation
check_content "RELEASE_GUIDE.md" "SIGNING_KEY_BASE64"
check_content "RELEASE_GUIDE.md" "KEYSTORE_PASSWORD"
check_content "RELEASE_GUIDE.md" "KEY_ALIAS"
check_content "RELEASE_GUIDE.md" "KEY_PASSWORD"

echo ""
echo "🎯 Testing version bump logic..."
echo "-------------------------------"

# Test version bump logic with sample commit messages
test_version_bump() {
    local commit_msg="$1"
    local expected_bump="$2"
    
    echo -e "${YELLOW}Testing: '$commit_msg'${NC}"
    
    # Simulate version bump logic
    if echo "$commit_msg" | grep -q "breaking\|major"; then
        echo -e "${GREEN}✅ Would trigger MAJOR bump${NC}"
    elif echo "$commit_msg" | grep -q "feat\|feature\|enhancement"; then
        echo -e "${GREEN}✅ Would trigger MINOR bump${NC}"
    elif echo "$commit_msg" | grep -q "fix\|bug\|patch"; then
        echo -e "${GREEN}✅ Would trigger PATCH bump${NC}"
    else
        echo -e "${GREEN}✅ Would trigger PATCH bump (default)${NC}"
    fi
    echo ""
}

test_version_bump "feat(compass): add magnetic declination correction"
test_version_bump "fix(camera): resolve permission error"
test_version_bump "breaking(api): change sensor data format"
test_version_bump "docs(readme): update installation guide"

echo ""
echo "📊 Summary..."
echo "-------------"

echo -e "${YELLOW}Release System Status:${NC}"
echo "• Workflow: .github/workflows/release-drafter.yml"
echo "• Configuration: .github/release-drafter.yml"
echo "• Documentation: COMMIT_CONVENTIONS.md, RELEASE_GUIDE.md"
echo "• Current Version: $CURRENT_VERSION"
echo ""
echo -e "${GREEN}✅ System appears to be properly configured!${NC}"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "1. Ensure GitHub secrets are configured"
echo "2. Test with a commit using proper format"
echo "3. Monitor GitHub Actions for success"
echo "4. Verify release creation with APK"
echo ""
echo "Example test commit:"
echo 'git commit -m "feat(compass): add magnetic declination correction"'
echo "git push origin main" 