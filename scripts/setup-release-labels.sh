#!/bin/bash

# Setup script for GitHub labels needed by release-drafter
# Run this script to create all necessary labels in your GitHub repository

set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

print_status() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if gh CLI is installed
if ! command -v gh &> /dev/null; then
    print_error "GitHub CLI (gh) is not installed. Please install it first:"
    print_error "https://cli.github.com/"
    exit 1
fi

# Check if user is authenticated
if ! gh auth status &> /dev/null; then
    print_error "Please authenticate with GitHub CLI first:"
    print_error "gh auth login"
    exit 1
fi

# Function to create label if it doesn't exist
create_label_if_not_exists() {
    local name="$1"
    local color="$2"
    local description="$3"
    
    # Check if label exists
    if gh api repos/:owner/:repo/labels/"$name" &> /dev/null 2>&1; then
        print_warning "Label '$name' already exists, skipping..."
    else
        print_status "Creating label: $name"
        gh api repos/:owner/:repo/labels -f name="$name" -f color="$color" -f description="$description" &> /dev/null
        print_status "✅ Created label: $name"
    fi
}

print_status "Setting up GitHub labels for release-drafter..."

# Feature labels (Minor version bump)
print_status "Creating feature labels..."
create_label_if_not_exists "feature" "0e8a16" "New functionality"
create_label_if_not_exists "enhancement" "1d76db" "Improvements to existing features"
create_label_if_not_exists "new feature" "0e8a16" "Brand new features"

# Bug fix labels (Patch version bump)
print_status "Creating bug fix labels..."
create_label_if_not_exists "fix" "d73a4a" "Bug fixes"
create_label_if_not_exists "bugfix" "d73a4a" "Alternative bug fix label"
create_label_if_not_exists "bug" "d73a4a" "General bug-related changes"

# Improvement labels (No version bump)
print_status "Creating improvement labels..."
create_label_if_not_exists "improvement" "1d76db" "General improvements"
create_label_if_not_exists "refactor" "1d76db" "Code refactoring"
create_label_if_not_exists "optimization" "1d76db" "Performance improvements"

# Documentation labels (No version bump)
print_status "Creating documentation labels..."
create_label_if_not_exists "documentation" "0075ca" "Documentation changes"
create_label_if_not_exists "docs" "0075ca" "Alternative docs label"

# Testing labels (No version bump)
print_status "Creating testing labels..."
create_label_if_not_exists "test" "fef2c0" "Test-related changes"
create_label_if_not_exists "testing" "fef2c0" "Alternative test label"

# Maintenance labels (No version bump)
print_status "Creating maintenance labels..."
create_label_if_not_exists "chore" "5319e7" "Maintenance tasks"
create_label_if_not_exists "maintenance" "5319e7" "General maintenance"
create_label_if_not_exists "dependencies" "5319e7" "Dependency updates"

# Breaking change labels (Major version bump)
print_status "Creating breaking change labels..."
create_label_if_not_exists "breaking" "b60205" "Breaking changes"
create_label_if_not_exists "breaking-change" "b60205" "Alternative breaking label"

print_status "✅ Label setup completed!"
print_status ""
print_status "🎯 Next steps:"
print_status "1. Create a feature branch: git checkout -b feature/your-feature"
print_status "2. Make changes and commit: git commit -m 'feat(scope): description'"
print_status "3. Create PR with appropriate labels"
print_status "4. Merge to main → Automatic release draft created"
print_status ""
print_status "📖 See COMMIT_CONVENTIONS.md for detailed guidelines"
print_status "📖 See RELEASE_GUIDE.md for release workflow"
print_status ""
print_status "🔍 To verify labels, visit: https://github.com/bizzkoot/Qibla_Finder/labels" 