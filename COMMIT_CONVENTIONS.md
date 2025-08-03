# 📝 Commit Message Conventions for Qibla Finder

This document defines the commit message conventions that must be followed to ensure proper automated releases with release-drafter.

## 🎯 Why This Matters

Following these conventions enables:
- ✅ **Automatic release notes** generation
- ✅ **Proper version bumping** based on commit types
- ✅ **Categorized changelog** entries
- ✅ **Professional release** documentation

## 📋 Commit Message Format

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### **Type** (Required)
Must be one of the following:

| Type | Description | Version Bump | Example |
|------|-------------|--------------|---------|
| `feat` | New feature | Minor | `feat(compass): add calibration screen` |
| `fix` | Bug fix | Patch | `fix(gps): resolve location accuracy issue` |
| `docs` | Documentation changes | None | `docs(readme): update installation guide` |
| `style` | Code style changes (formatting, etc.) | None | `style(ui): format code with ktlint` |
| `refactor` | Code refactoring | None | `refactor(ar): improve camera performance` |
| `perf` | Performance improvements | Patch | `perf(map): optimize tile loading` |
| `test` | Adding or updating tests | None | `test(compass): add unit tests` |
| `chore` | Maintenance tasks | None | `chore(deps): update gradle version` |
| `breaking` | Breaking changes | Major | `breaking(api): change location service interface` |

### **Scope** (Optional)
The scope should be the name of the component affected:

- `compass` - Compass-related features
- `gps` - GPS and location services
- `ar` - Augmented Reality features
- `ui` - User interface changes
- `map` - Map-related functionality
- `permissions` - Permission handling
- `deps` - Dependencies and libraries
- `build` - Build system changes
- `docs` - Documentation

### **Description** (Required)
- Use imperative mood ("add" not "added")
- Don't capitalize first letter
- No period at the end
- Keep it under 72 characters

## 🏷️ PR Labels

When creating Pull Requests, use these labels to categorize changes:

### **Feature Labels**
- `feature` - New functionality
- `enhancement` - Improvements to existing features
- `new feature` - Brand new features

### **Bug Fix Labels**
- `fix` - Bug fixes
- `bugfix` - Alternative bug fix label
- `bug` - General bug-related changes

### **Improvement Labels**
- `improvement` - General improvements
- `refactor` - Code refactoring
- `optimization` - Performance improvements

### **Documentation Labels**
- `documentation` - Documentation changes
- `docs` - Alternative docs label

### **Testing Labels**
- `test` - Test-related changes
- `testing` - Alternative test label

### **Maintenance Labels**
- `chore` - Maintenance tasks
- `maintenance` - General maintenance
- `dependencies` - Dependency updates

### **Breaking Change Labels**
- `breaking` - Breaking changes
- `breaking-change` - Alternative breaking label

## 📝 Examples

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

## 🚀 Workflow

### **For New Features**
1. Create feature branch: `git checkout -b feature/compass-calibration`
2. Make changes and commit: `git commit -m "feat(compass): add interactive calibration screen"`
3. Push and create PR with label: `feature`
4. Merge to main → Automatic release draft created

### **For Bug Fixes**
1. Create fix branch: `git checkout -b fix/gps-accuracy`
2. Fix the bug and commit: `git commit -m "fix(gps): resolve location accuracy in urban areas"`
3. Push and create PR with label: `fix`
4. Merge to main → Automatic release draft created

### **For Breaking Changes**
1. Create breaking branch: `git checkout -b breaking/api-changes`
2. Make breaking changes and commit: `git commit -m "breaking(api): change location service interface"`
3. Push and create PR with label: `breaking`
4. Merge to main → Major version bump

## 📋 Checklist

Before committing, ensure:
- [ ] Commit message follows the format: `type(scope): description`
- [ ] Type is one of the allowed types
- [ ] Description is in imperative mood
- [ ] Description is under 72 characters
- [ ] PR has appropriate labels (if applicable)

## 🎯 Quick Reference

### **Common Commands**
```bash
# Feature
git commit -m "feat(compass): add calibration screen"

# Bug fix
git commit -m "fix(gps): resolve accuracy issue"

# Documentation
git commit -m "docs(readme): update installation guide"

# Refactoring
git commit -m "refactor(ar): improve camera performance"

# Breaking change
git commit -m "breaking(api): change location interface"
```

### **PR Labels to Use**
- New features: `feature`
- Bug fixes: `fix`
- Improvements: `improvement`
- Documentation: `documentation`
- Breaking changes: `breaking`

---

**Remember**: Following these conventions ensures automatic, professional releases! 🚀 