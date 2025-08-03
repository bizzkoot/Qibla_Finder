#!/bin/bash

# Test script for the release-drafter system
# This script helps verify that the configuration is working

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

print_status "Testing release-drafter configuration..."

# Check if required files exist
print_status "Checking required files..."

if [ -f ".github/release-drafter.yml" ]; then
    print_status "✅ .github/release-drafter.yml exists"
else
    print_error "❌ .github/release-drafter.yml missing"
    exit 1
fi

if [ -f ".github/workflows/release-drafter.yml" ]; then
    print_status "✅ .github/workflows/release-drafter.yml exists"
else
    print_error "❌ .github/workflows/release-drafter.yml missing"
    exit 1
fi

# Check YAML syntax (basic check)
print_status "Checking YAML syntax..."

# Check release-drafter.yml
if grep -q "name-template:" .github/release-drafter.yml; then
    print_status "✅ release-drafter.yml has required fields"
else
    print_error "❌ release-drafter.yml missing required fields"
    exit 1
fi

# Check workflow.yml
if grep -q "release-drafter/release-drafter@v5" .github/workflows/release-drafter.yml; then
    print_status "✅ workflow.yml has correct action"
else
    print_error "❌ workflow.yml missing correct action"
    exit 1
fi

# Check if labels exist
print_status "Checking GitHub labels..."

# Test a few key labels
labels_to_check=("feature" "fix" "enhancement" "breaking")

for label in "${labels_to_check[@]}"; do
    if gh api repos/:owner/:repo/labels/"$label" &> /dev/null 2>&1; then
        print_status "✅ Label '$label' exists"
    else
        print_warning "⚠️  Label '$label' not found (this is okay if not created yet)"
    fi
done

print_status ""
print_status "🎯 Configuration looks good!"
print_status ""
print_status "📋 Next steps to test:"
print_status "1. Create a test branch: git checkout -b test/release-system"
print_status "2. Make a change and commit: git commit -m 'feat(ui): test release system'"
print_status "3. Push and create PR: git push origin test/release-system"
print_status "4. Add 'feature' label to PR"
print_status "5. Merge to main"
print_status "6. Check releases page for draft"
print_status ""
print_status "🔍 Monitor at: https://github.com/bizzkoot/Qibla_Finder/actions"
print_status "📦 Releases at: https://github.com/bizzkoot/Qibla_Finder/releases" 