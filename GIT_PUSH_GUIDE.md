# Git Push Guide for Qibla Finder

## 🎯 Overview

This guide provides **step-by-step instructions** for pushing changes to Git and triggering the automated release system in the Qibla Finder project.

## 📋 Prerequisites

- ✅ Git installed and configured
- ✅ Access to [Qibla_Finder repository](https://github.com/bizzkoot/Qibla_Finder)
- ✅ Local repository cloned and up-to-date
- ✅ Changes ready to commit

## 🔄 Current Workflow Overview

### **Automated Release System**
Our project uses **GitHub Actions** with **Conventional Commits** to automatically:
1. **Analyze commit messages** for version bump keywords
2. **Update version** in `app/build.gradle`
3. **Create git tags** (e.g., `v1.4.0`)
4. **Build signed APK** with release signing
5. **Create GitHub release** with APK attached

### **Version Bumping Rules**
| Commit Type | Keywords | Version Bump | Example |
|-------------|----------|--------------|---------|
| **Major** | `breaking`, `major` | `1.3.0` → `2.0.0` | `breaking(api): change data format` |
| **Minor** | `feat`, `feature`, `enhancement` | `1.3.0` → `1.4.0` | `feat(maps): add digital zoom` |
| **Patch** | `fix`, `bug`, `patch` | `1.3.0` → `1.3.1` | `fix(compass): resolve bearing error` |

## 🚀 Step-by-Step Git Push Process

### **Step 1: Check Current Status**
```bash
# Check current branch and status
git status

# Check current version
grep "versionName\|versionCode" app/build.gradle

# Check recent commits
git log --oneline -5
```

### **Step 2: Pull Latest Changes**
```bash
# Fetch latest changes from remote
git fetch origin

# Pull latest changes (if any)
git pull origin main

# Verify you're up to date
git status
```

### **Step 3: Stage Your Changes**
```bash
# Stage all changes
git add .

# Or stage specific files
git add app/src/main/java/com/bizzkoot/qiblafinder/
git add ZoomMap.md
git add app/src/main/java/com/bizzkoot/qiblafinder/utils/

# Check what's staged
git status
```

### **Step 4: Write Proper Commit Message**
Follow the **Conventional Commits** format:
```bash
# Format: <type>(<scope>): <description>
git commit -m "feat(maps): implement digital zoom feature with device-specific optimizations

- Add digital zoom capability beyond tile server limits (up to 4x zoom)
- Implement device capabilities detection for performance optimization
- Add smooth drag handling with zoom-aware sensitivity
- Include visual indicator for digital zoom mode
- Optimize tile loading during digital zoom to reduce bandwidth
- Add comprehensive error handling and memory pressure monitoring
- Support device-specific zoom limits (high-end: 4x, mid-range: 2.5x, low-end: 2x)
- Enhance accuracy calculations for digital zoom levels
- Add performance safeguards and automatic fallbacks"
```

### **Step 5: Push to Main**
```bash
# Push to main branch (triggers automated workflow)
git push origin main
```

## 📊 Commit Message Examples

### **New Features (Minor Bump)**
```bash
# Digital zoom feature
git commit -m "feat(maps): implement digital zoom feature with device-specific optimizations"

# AR camera overlay
git commit -m "feat(ar): add AR camera overlay for real-time guidance"

# Dark mode support
git commit -m "enhancement(ui): add dark mode support for better user experience"
```

### **Bug Fixes (Patch Bump)**
```bash
# Compass bearing fix
git commit -m "fix(compass): resolve bearing calculation error in magnetic declination"

# Camera permission fix
git commit -m "bug(camera): fix permission denied error on Huawei devices"

# GPS accuracy fix
git commit -m "patch(location): fix GPS accuracy issues in urban areas"
```

### **Breaking Changes (Major Bump)**
```bash
# API changes
git commit -m "breaking(api): change sensor data format for better performance"

# UI redesign
git commit -m "major(ui): redesign navigation structure for improved UX"
```

### **Documentation & Maintenance (Patch Bump)**
```bash
# Documentation updates
git commit -m "docs(readme): update installation guide with new requirements"

# Code refactoring
git commit -m "refactor(utils): simplify calculation methods for better maintainability"

# Performance improvements
git commit -m "perf(compass): optimize bearing calculation for faster response"

# Dependencies update
git commit -m "chore(deps): update gradle version to 8.0"
```

## 🔍 Monitoring the Process

### **Step 1: Check GitHub Actions**
1. Go to: [GitHub Actions](https://github.com/bizzkoot/Qibla_Finder/actions)
2. Look for **"Release Drafter"** workflow
3. Click to view detailed logs
4. Monitor for any errors

### **Step 2: Verify Version Bump**
1. Check `app/build.gradle` for updated version
2. Look for new commit: `chore: bump version to X.X.X [skip ci]`
3. Verify new git tag: `vX.X.X`

### **Step 3: Check Release**
1. Go to: [Releases](https://github.com/bizzkoot/Qibla_Finder/releases)
2. Confirm new release exists
3. Verify APK is attached
4. Download and test APK

## 🚨 Troubleshooting

### **Common Issues & Solutions**

#### **Issue 1: Version Not Bumping**
**Symptoms:**
- Version remains unchanged after push
- No new tag created

**Causes:**
- Commit message doesn't match keywords
- Workflow failed to execute

**Solutions:**
```bash
# Check commit message format
git log --oneline -1

# Use proper keywords
git commit --amend -m "feat(maps): implement digital zoom feature"
git push --force-with-lease origin main
```

#### **Issue 2: Workflow Fails**
**Symptoms:**
- GitHub Actions shows red X
- Error messages in logs

**Causes:**
- Missing GitHub secrets
- Build errors
- Permission issues

**Solutions:**
1. Check [GitHub Secrets Setup](GitHub_Secrets_Setup_Secure.md)
2. Verify all secrets are configured
3. Review build logs for specific errors

#### **Issue 3: Tag Already Exists**
**Symptoms:**
- Workflow fails with tag conflict
- Version already released

**Solutions:**
```bash
# Check existing tags
git tag -l | grep v

# Use different version keywords
git commit --amend -m "feat(maps): implement digital zoom feature (v1.4.1)"
git push --force-with-lease origin main
```

#### **Issue 4: Push Rejected**
**Symptoms:**
- `git push` fails with rejection

**Solutions:**
```bash
# Pull latest changes first
git pull origin main

# Resolve conflicts if any
git status
git add .
git commit -m "merge: resolve conflicts"

# Push again
git push origin main
```

## 📋 Pre-Push Checklist

### **Before Pushing:**
- ✅ [ ] All changes are staged (`git add .`)
- ✅ [ ] Commit message follows conventional format
- ✅ [ ] Local tests pass (`./gradlew test`)
- ✅ [ ] Build succeeds (`./gradlew assembleDebug`)
- ✅ [ ] Code is reviewed and approved
- ✅ [ ] Documentation is updated
- ✅ [ ] No sensitive data in commits

### **After Pushing:**
- ✅ [ ] GitHub Actions workflow started
- ✅ [ ] Version bump detected
- ✅ [ ] New tag created
- ✅ [ ] Release created with APK
- ✅ [ ] APK downloaded and tested

## 🔧 Advanced Usage

### **Force Push (Use with Caution)**
```bash
# Only use when absolutely necessary
git push --force-with-lease origin main
```

### **Amend Last Commit**
```bash
# Modify last commit message
git commit --amend -m "feat(maps): implement digital zoom feature"

# Add files to last commit
git add .
git commit --amend --no-edit
```

### **Create Feature Branch**
```bash
# Create and switch to feature branch
git checkout -b feature/digital-zoom

# Make changes and commit
git add .
git commit -m "feat(maps): implement digital zoom feature"

# Push feature branch
git push origin feature/digital-zoom

# Create pull request on GitHub
# Merge to main when approved
```

## 📚 Related Documentation

- [Commit Conventions](COMMIT_CONVENTIONS.md) - Detailed commit message guidelines
- [Release Guide](RELEASE_GUIDE.md) - Complete release system explanation
- [GitHub Steps](GitHub_Steps.md) - GitHub setup and configuration
- [GitHub Secrets Setup](GitHub_Secrets_Setup_Secure.md) - Security configuration

## 🆘 Support

### **Getting Help:**
1. **Check this guide** first
2. **Review related documentation** above
3. **Check GitHub Actions logs** for specific errors
4. **Create GitHub issue** with detailed information

### **Emergency Contacts:**
- **Development Team**: Review documentation and logs
- **GitHub Issues**: Create issue with workflow logs
- **Documentation**: All `.md` files in repository

---

**Last Updated**: December 2024  
**Maintainer**: Development Team  
**Version**: 1.0 