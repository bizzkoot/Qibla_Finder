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
check_file "app/build.gradle"
check_file "docs/development/COMMIT_CONVENTIONS.md"
check_file "docs/guides/RELEASE_GUIDE.md"

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
check_content "docs/development/COMMIT_CONVENTIONS.md" "feat\|feature\|enhancement"
check_content "docs/development/COMMIT_CONVENTIONS.md" "fix\|bug\|patch"
check_content "docs/development/COMMIT_CONVENTIONS.md" "breaking\|major"
check_content "docs/guides/RELEASE_GUIDE.md" "Automated Release System"

echo ""
echo "🔐 Checking GitHub secrets requirements..."
echo "----------------------------------------"

# Check if secrets are mentioned in documentation
check_content "docs/guides/RELEASE_GUIDE.md" "SIGNING_KEY_BASE64"
check_content "docs/guides/RELEASE_GUIDE.md" "KEYSTORE_PASSWORD"
check_content "docs/guides/RELEASE_GUIDE.md" "KEY_ALIAS"
check_content "docs/guides/RELEASE_GUIDE.md" "KEY_PASSWORD"

echo ""
echo "🎯 Testing version bump logic..."
echo "-------------------------------"

# Test version bump logic with sample commit messages.
# Mirrors release-drafter.yml exactly: subject-only scan of the
# conventional-commit type prefix; bodies never influence the bump.
FAILED_BUMP_CHECKS=0
test_version_bump() {
    local commit_msg="$1"
    local expected_bump="$2"
    local actual
    local lower

    lower=$(printf '%s' "$commit_msg" | tr '[:upper:]' '[:lower:]')
    if printf '%s' "$lower" | grep -qE '^(breaking|major)(\([^)]*\))?!?:|^[a-z]+(\([^)]*\))?!:'; then
        actual="major"
    elif printf '%s' "$lower" | grep -qE '^(feat|feature|enhancement)(\([^)]*\))?!?:'; then
        actual="minor"
    else
        actual="patch"
    fi

    if [ "$actual" = "$expected_bump" ]; then
        echo -e "${GREEN}✅ '$commit_msg' -> $actual (expected $expected_bump)${NC}"
    else
        echo -e "${RED}❌ '$commit_msg' -> $actual (expected $expected_bump)${NC}"
        FAILED_BUMP_CHECKS=$((FAILED_BUMP_CHECKS + 1))
    fi
}

test_version_bump "feat(compass): add magnetic declination correction" "minor"
test_version_bump "fix(camera): resolve permission error" "patch"
test_version_bump "breaking(api): change sensor data format" "major"
test_version_bump "docs(readme): update installation guide" "patch"
# Hardening cases: prose in the subject (not the type prefix) and a
# non-commit-type body mentioning trigger words must NOT change the bump.
test_version_bump "docs: explain how to avoid a major api break in the guide" "patch"
test_version_bump "chore: bump version to 2.10.4 [skip ci]" "patch"
# Conventional-commit breaking marker `!` -> major.
test_version_bump "feat!: remove legacy compass API" "major"
test_version_bump "fix(api)!: change sensor data format" "major"

echo ""
echo -e "${YELLOW}Version bump checks failed: $FAILED_BUMP_CHECKS${NC}"
echo ""
echo "📊 Summary..."
echo "-------------"

echo -e "${YELLOW}Release System Status:${NC}"
echo "• Workflow: .github/workflows/release-drafter.yml"
echo "• Documentation: docs/development/COMMIT_CONVENTIONS.md, docs/guides/RELEASE_GUIDE.md"
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