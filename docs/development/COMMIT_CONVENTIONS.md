# Commit Conventions & Automated Version Bumping Guide

## 🎯 Overview

This document explains how to use commit messages to automatically trigger version bumps and releases in the Qibla Finder project. The system uses **Conventional Commits** format to determine version changes.

## 📝 Commit Message Format

### Required Format
```
<type>(<scope>): <description>
```

### Examples
- `feat(compass): add magnetic declination correction`
- `fix(ar): resolve camera permission crash`
- `docs(readme): update installation instructions`
- `style(ui): improve button spacing`
- `refactor(calibration): simplify sun position calculation`
- `perf(location): optimize GPS accuracy`
- `test(compass): add unit tests for bearing calculation`
- `chore(deps): update gradle version`

## 🏷️ Commit Types & Version Bumping Rules

| Commit Type | Keywords | Version Bump | Description |
|-------------|----------|--------------|-------------|
| **Major** | `breaking`, `major` | `1.0.0` → `2.0.0` | Breaking changes, incompatible API changes |
| **Minor** | `feat`, `feature`, `enhancement` | `1.0.0` → `1.1.0` | New features, backward compatible |
| **Patch** | `fix`, `bug`, `patch` | `1.0.0` → `1.0.1` | Bug fixes, backward compatible |
| **Patch** | `docs`, `style`, `refactor`, `perf`, `test`, `chore` | `1.0.0` → `1.0.1` | Documentation, formatting, refactoring, performance, tests, maintenance |

## 🔄 Automated Process Flow

### What Happens When You Push to Main:

1. **Trigger**: Push to `main` branch
2. **Analysis**: Workflow reads your commit message
3. **Version Detection**: Matches keywords to determine bump type
4. **Version Update**: Automatically updates `app/build.gradle`
5. **Commit**: Commits the version change
6. **Tag Creation**: Creates git tag (e.g., `v1.1.0`)
7. **Tag Push**: Pushes tag to repository
8. **APK Build**: Builds signed release APK
9. **Release Creation**: Creates GitHub release with APK attached

### Example Timeline:
```
Current Version: 1.0.0
Your Commit: "feat(compass): add magnetic declination correction"
Result: Version 1.1.0, Tag v1.1.0, Release with APK
```

## 📋 Step-by-Step Workflow

### For Developers:

#### 1. **Write Proper Commit Message**
```bash
git commit -m "feat(compass): add magnetic declination correction"
```

#### 2. **Push to Main**
```bash
git push origin main
```

#### 3. **Monitor GitHub Actions**
- Go to: `https://github.com/bizzkoot/Qibla_Finder/actions`
- Watch the "Release Drafter" workflow
- Check for any errors

#### 4. **Verify Release**
- Go to: `https://github.com/bizzkoot/Qibla_Finder/releases`
- Confirm new release with APK attached

### For Project Managers:

#### 1. **Review Commit Messages**
- Ensure all commits follow the format
- Check that version bumps are appropriate

#### 2. **Monitor Releases**
- Verify APK is attached to releases
- Test APK functionality

#### 3. **Troubleshooting**
- Check workflow logs for errors
- Verify GitHub secrets are configured

## 🚨 Important Rules

### ✅ **DO:**
- Use conventional commit format
- Include descriptive messages
- Use appropriate keywords for version bumping
- Test locally before pushing

### ❌ **DON'T:**
- Use generic messages like "update" or "fix"
- Skip the commit type prefix
- Use non-standard keywords
- Push directly to main without testing

## 🔧 Troubleshooting

### Common Issues:

#### 1. **Version Not Bumping**
- **Cause**: Commit message doesn't match keywords
- **Solution**: Use proper keywords (feat, fix, breaking, etc.)

#### 2. **Workflow Fails**
- **Cause**: Missing GitHub secrets
- **Solution**: Check `GitHub_Secrets_Setup_Secure.md`

#### 3. **No APK in Release**
- **Cause**: Build or signing issues
- **Solution**: Check workflow logs for build errors

#### 4. **Tag Already Exists**
- **Cause**: Version already released
- **Solution**: Use different version or keywords

## 📚 Examples by Category

### Features (Minor Bump)
```bash
git commit -m "feat(ar): add AR camera overlay"
git commit -m "feature(compass): implement true north calculation"
git commit -m "enhancement(ui): add dark mode support"
```

### Bug Fixes (Patch Bump)
```bash
git commit -m "fix(camera): resolve permission denied error"
git commit -m "bug(compass): fix bearing calculation error"
git commit -m "patch(location): fix GPS accuracy issues"
```

### Breaking Changes (Major Bump)
```bash
git commit -m "breaking(api): change sensor data format"
git commit -m "major(ui): redesign navigation structure"
```

### Documentation (Patch Bump)
```bash
git commit -m "docs(readme): update installation guide"
git commit -m "docs(api): add method documentation"
```

### Maintenance (Patch Bump)
```bash
git commit -m "chore(deps): update gradle version"
git commit -m "style(code): format according to guidelines"
git commit -m "refactor(utils): simplify calculation methods"
git commit -m "perf(compass): optimize bearing calculation"
git commit -m "test(ar): add unit tests for AR functionality"
```

## 🎯 Quick Reference

### Keywords for Version Bumping:
- **Major**: `breaking`, `major`
- **Minor**: `feat`, `feature`, `enhancement`
- **Patch**: `fix`, `bug`, `patch`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`

### Workflow Status:
- **Success**: Version bumped, tag created, release published with APK
- **Failure**: Check GitHub Actions logs for specific errors

### Support:
- **Documentation**: This file and related `.md` files
- **Issues**: Create GitHub issue with workflow logs
- **Questions**: Review this guide and related documentation

---

**Last Updated**: December 2024  
**Maintainer**: Development Team  
**Version**: 1.0 