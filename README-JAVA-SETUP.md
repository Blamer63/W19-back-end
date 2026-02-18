# Java Setup for IDE Terminal

## Problem
Your IDE terminal is using Java 8 from your system PATH instead of Java 25, causing Spring Boot 3.4.1 to fail with:
```
UnsupportedClassVersionError: class file version 61.0, this version only recognizes up to 52.0
```

## Quick Fix (For Each Terminal Session)

Run this command in your IDE terminal **before** running Maven:
```powershell
.\setup-java.ps1
```

Then run your Maven commands:
```powershell
mvn spring-boot:run
```

## Permanent Fix (Recommended)

### Option 1: Update System Environment Variables
1. Open "Edit the system environment variables" in Windows
2. Click "Environment Variables"
3. In "System variables", find `Path`
4. Click "Edit"
5. Move `C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot\bin` to the **top** of the list
6. Click OK on all dialogs
7. **Restart your IDE** for changes to take effect

### Option 2: Configure IDE to Use Java 25
If you're using **VS Code**:
1. Open Settings (Ctrl+,)
2. Search for "java.home"
3. Set it to: `C:\Program Files\Eclipse Adoptium\jdk-25.0.1.8-hotspot`

If you're using **IntelliJ IDEA**:
1. File → Project Structure → Project
2. Set Project SDK to Java 25
3. File → Settings → Build, Execution, Deployment → Build Tools → Maven → Runner
4. Set JRE to Java 25

## Verification
After applying the fix, verify with:
```powershell
java -version
# Should show: openjdk version "25.0.1"

mvn -version
# Should show: Java version: 25.0.1
```
