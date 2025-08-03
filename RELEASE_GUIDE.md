# Automated Release System Guide

## 🎯 Overview

This guide explains the complete automated release system for Qibla Finder. The system automatically creates releases with signed APKs based on commit messages.

## 🔄 Complete Workflow

### 1. **Developer Makes Changes**
```bash
# Make your changes
git add .
git commit -m "feat(compass): add magnetic declination correction"
git push origin main
```

### 2. **Automated Process Triggers**
- **Trigger**: Push to `main` branch
- **Workflow**: `.github/workflows/release-drafter.yml`
- **Analysis**: Reads commit message for keywords
- **Version Bump**: Determines version change (major/minor/patch)

### 3. **Version Management**
- **Reads**: Current version from `app/build.gradle`
- **Calculates**: New version based on commit keywords
- **Updates**: `versionName` and `versionCode` in `app/build.gradle`
- **Commits**: Version change to repository

### 4. **Tag Creation**
- **Creates**: Git tag (e.g., `v1.1.0`)
- **Pushes**: Tag to repository
- **Ensures**: Tag exists before release creation

### 5. **APK Building**
- **Sets up**: JDK 17 environment
- **Creates**: Keystore from GitHub secrets
- **Builds**: Signed release APK
- **Outputs**: `app/build/outputs/apk/release/app-release.apk`

### 6. **Release Creation**
- **Creates**: GitHub release with tag
- **Attaches**: Signed APK to release
- **Generates**: Release notes automatically
- **Publishes**: Release (not draft)

## 📋 Step-by-Step Instructions

### For Developers:

#### **Step 1: Write Proper Commit Message**
Follow the format: `<type>(<scope>): <description>`

**Examples:**
```bash
# New feature (minor version bump)
git commit -m "feat(ar): add AR camera overlay"

# Bug fix (patch version bump)
git commit -m "fix(compass): resolve bearing calculation error"

# Breaking change (major version bump)
git commit -m "breaking(api): change sensor data format"
```

#### **Step 2: Push to Main**
```bash
git push origin main
```

#### **Step 3: Monitor Progress**
1. Go to: `https://github.com/bizzkoot/Qibla_Finder/actions`
2. Find the "Release Drafter" workflow
3. Click to view detailed logs
4. Check for any errors

#### **Step 4: Verify Release**
1. Go to: `https://github.com/bizzkoot/Qibla_Finder/releases`
2. Confirm new release exists
3. Verify APK is attached
4. Download and test APK

### For Project Managers:

#### **Step 1: Review Commit Messages**
- Ensure all commits follow conventional format
- Verify version bumps are appropriate
- Check for breaking changes

#### **Step 2: Monitor Releases**
- Verify APK is attached to releases
- Test APK functionality
- Check release notes accuracy

#### **Step 3: Troubleshoot Issues**
- Review workflow logs for errors
- Verify GitHub secrets configuration
- Check for tag conflicts

## 🔧 Configuration Files

### 1. **Workflow File**: `.github/workflows/release-drafter.yml`
- **Purpose**: Main automation workflow
- **Triggers**: Push to main, PR events
- **Actions**: Version bumping, tag creation, APK building, release creation

### 2. **Release Drafter Config**: `.github/release-drafter.yml`
- **Purpose**: Release notes configuration
- **Features**: Categories, templates, formatting

### 3. **Build Configuration**: `app/build.gradle`
- **Purpose**: Version source of truth
- **Fields**: `versionName`, `versionCode`
- **Auto-updated**: By workflow

## 🚨 Troubleshooting Guide

### Common Issues & Solutions:

#### **Issue 1: Version Not Bumping**
**Symptoms:**
- Version remains unchanged after push
- No new tag created

**Causes:**
- Commit message doesn't match keywords
- Workflow failed to execute

**Solutions:**
1. Check commit message format
2. Use proper keywords (feat, fix, breaking, etc.)
3. Review workflow logs

#### **Issue 2: Workflow Fails**
**Symptoms:**
- GitHub Actions shows red X
- Error messages in logs

**Causes:**
- Missing GitHub secrets
- Build errors
- Permission issues

**Solutions:**
1. Check `GitHub_Secrets_Setup_Secure.md`
2. Verify all secrets are configured
3. Review build logs for specific errors

#### **Issue 3: No APK in Release**
**Symptoms:**
- Release created but no APK attached
- Missing APK file

**Causes:**
- Build failure
- Signing issues
- File path problems

**Solutions:**
1. Check build logs for errors
2. Verify keystore configuration
3. Ensure APK path is correct

#### **Issue 4: Tag Already Exists**
**Symptoms:**
- Workflow fails with tag conflict
- Version already released

**Causes:**
- Version already exists
- Duplicate version bump

**Solutions:**
1. Use different version keywords
2. Manually delete tag if needed
3. Use different commit message

## 📊 Version Bumping Rules

### **Major Version (X.0.0)**
**Keywords:** `breaking`, `major`
**Examples:**
- `breaking(api): change sensor data format`
- `major(ui): redesign navigation structure`

### **Minor Version (0.X.0)**
**Keywords:** `feat`, `feature`, `enhancement`
**Examples:**
- `feat(compass): add magnetic declination`
- `feature(ar): implement AR overlay`
- `enhancement(ui): add dark mode`

### **Patch Version (0.0.X)**
**Keywords:** `fix`, `bug`, `patch`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`
**Examples:**
- `fix(camera): resolve permission error`
- `bug(compass): fix bearing calculation`
- `docs(readme): update installation guide`
- `style(code): format according to guidelines`
- `refactor(utils): simplify calculations`
- `perf(compass): optimize calculations`
- `test(ar): add unit tests`
- `chore(deps): update gradle version`

## 🔐 Security Considerations

### **GitHub Secrets Required:**
- `SIGNING_KEY_BASE64`: Base64-encoded keystore
- `KEYSTORE_PASSWORD`: Keystore password
- `KEY_ALIAS`: Key alias
- `KEY_PASSWORD`: Key password
- `GITHUB_TOKEN`: Automatically provided

### **Security Best Practices:**
- Never commit secrets to repository
- Use GitHub secrets for sensitive data
- Regularly rotate signing keys
- Monitor for unauthorized access

## 📈 Monitoring & Maintenance

### **Regular Checks:**
1. **Weekly**: Review recent releases
2. **Monthly**: Verify workflow performance
3. **Quarterly**: Update documentation
4. **As Needed**: Troubleshoot issues

### **Performance Metrics:**
- Release frequency
- Build success rate
- APK attachment rate
- Error frequency

## 🆘 Support & Resources

### **Documentation:**
- `COMMIT_CONVENTIONS.md`: Commit message guidelines
- `GitHub_Secrets_Setup_Secure.md`: Security setup
- This guide: Complete workflow explanation

### **Tools:**
- GitHub Actions: Workflow automation
- Release Drafter: Release notes generation
- Softprops Action: Release creation

### **Contacts:**
- **Issues**: Create GitHub issue with logs
- **Questions**: Review documentation first
- **Emergencies**: Contact development team

---

**Last Updated**: December 2024  
**Maintainer**: Development Team  
**Version**: 1.0 