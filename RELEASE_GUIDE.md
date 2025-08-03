# 🚀 Release Guide for Qibla Finder

This guide explains how the automated release system works and how to create releases properly.

## 🎯 How It Works

The release system uses **release-drafter** to automatically:
1. **Track changes** from PRs and commits
2. **Generate release notes** based on PR labels
3. **Create release drafts** when PRs are merged
4. **Publish releases** when tags are pushed

## 📋 Release Workflow

### **Step 1: Development**
- Create feature branches: `git checkout -b feature/compass-calibration`
- Follow commit conventions: `git commit -m "feat(compass): add calibration screen"`
- Create PR with appropriate labels (see below)

### **Step 2: PR Review & Merge**
- Add labels to PR: `feature`, `fix`, `improvement`, etc.
- Merge to main branch
- **Automatic**: Release draft is created/updated

### **Step 3: Release Publication**
- Review the draft release on GitHub
- Edit release notes if needed
- Publish the release
- **Automatic**: APK is built and attached

## 🏷️ PR Labels Guide

### **Feature Labels** (Minor version bump)
- `feature` - New functionality
- `enhancement` - Improvements to existing features
- `new feature` - Brand new features

**Example**: Adding compass calibration screen

### **Bug Fix Labels** (Patch version bump)
- `fix` - Bug fixes
- `bugfix` - Alternative bug fix label
- `bug` - General bug-related changes

**Example**: Fixing GPS accuracy in urban areas

### **Improvement Labels** (No version bump)
- `improvement` - General improvements
- `refactor` - Code refactoring
- `optimization` - Performance improvements

**Example**: Optimizing map tile loading

### **Documentation Labels** (No version bump)
- `documentation` - Documentation changes
- `docs` - Alternative docs label

**Example**: Updating README installation guide

### **Breaking Change Labels** (Major version bump)
- `breaking` - Breaking changes
- `breaking-change` - Alternative breaking label

**Example**: Changing API interface

## 📝 Commit Message Rules

### ✅ **Good Examples**
```
feat(compass): add interactive calibration screen
fix(gps): resolve location accuracy in urban areas
docs(readme): update installation instructions
refactor(ar): improve camera performance
perf(map): optimize tile loading for faster rendering
test(compass): add unit tests for calibration logic
chore(deps): update androidx compose to 1.5.1
breaking(api): change location service interface
```

### ❌ **Bad Examples**
```
Added compass calibration
Fixed GPS bug
Updated readme
Refactored AR code
Optimized map
Added tests
Updated dependencies
Changed API
```

## 🔄 Version Bumping Rules

| Commit Type | Version Bump | Example |
|-------------|--------------|---------|
| `feat` | Minor (1.0.0 → 1.1.0) | New features |
| `fix` | Patch (1.0.0 → 1.0.1) | Bug fixes |
| `perf` | Patch (1.0.0 → 1.0.1) | Performance improvements |
| `breaking` | Major (1.0.0 → 2.0.0) | Breaking changes |
| Others | None | No version bump |

## 🚀 Creating a Release

### **Method 1: Automatic (Recommended)**
1. **Merge PRs** with proper labels
2. **Review draft** release on GitHub
3. **Edit release notes** if needed
4. **Publish release** from draft

### **Method 2: Manual Tag**
1. **Create tag**: `git tag v1.0.1`
2. **Push tag**: `git push origin v1.0.1`
3. **Automatic**: Release is created with APK

## 📋 Release Checklist

Before publishing a release:

- [ ] **All tests pass** in CI/CD
- [ ] **Release notes** are complete and accurate
- [ ] **APK is attached** to the release
- [ ] **Version numbers** are correct
- [ ] **Breaking changes** are documented
- [ ] **Known issues** are listed (if any)

## 🎯 Best Practices

### **For Features**
1. Use `feat(scope): description` commit messages
2. Add `feature` label to PR
3. Write clear PR description
4. Test thoroughly before merge

### **For Bug Fixes**
1. Use `fix(scope): description` commit messages
2. Add `fix` label to PR
3. Include steps to reproduce
4. Test the fix thoroughly

### **For Breaking Changes**
1. Use `breaking(scope): description` commit messages
2. Add `breaking` label to PR
3. Document migration guide
4. Announce in PR description

## 🔧 Troubleshooting

### **Release Not Created**
- Check if PR has proper labels
- Verify commit messages follow conventions
- Check GitHub Actions for errors

### **Wrong Version Bump**
- Review commit types in PR
- Check PR labels
- Verify release-drafter configuration

### **APK Not Attached**
- Check if tag was pushed correctly
- Verify signing secrets are set
- Check build logs for errors

## 📊 Monitoring Releases

### **GitHub Actions**
- Check Actions tab for build status
- Monitor release workflow execution
- Review build artifacts

### **Release Page**
- Visit: `https://github.com/bizzkoot/Qibla_Finder/releases`
- Review draft releases
- Check APK downloads

### **Analytics**
- Monitor download counts
- Track user feedback
- Review crash reports

## 🎉 Success Metrics

A successful release should have:
- ✅ **All tests passing**
- ✅ **Clean release notes**
- ✅ **APK attached and signed**
- ✅ **Proper version bump**
- ✅ **No critical bugs reported**

---

**Remember**: Following these guidelines ensures professional, automated releases! 🚀 