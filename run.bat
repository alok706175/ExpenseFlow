@echo off
setlocal
cd /d "%~dp0"

echo =======================================================
echo Starting ExpenseFlow Web Application...
echo =======================================================

:: Check Java
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Java 17+ is not installed or not in PATH!
    pause
    exit /b 1
)

:: Check if global mvn exists
where mvn >nul 2>&1
if %ERRORLEVEL% EQU 0 (
    echo [INFO] Found installed Maven. Starting application...
    mvn spring-boot:run
    goto end
)

:: Check if local maven already extracted
if exist ".mvn\apache-maven-3.9.6\bin\mvn.cmd" (
    echo [INFO] Using local Maven...
    call .mvn\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
    goto end
)

:: Download Maven automatically
echo [INFO] Maven not found. Downloading portable Maven automatically (one-time setup)...
powershell -Command "New-Item -ItemType Directory -Force -Path '.mvn' | Out-Null; Write-Host 'Downloading Maven 3.9.6...'; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/3.9.6/binaries/apache-maven-3.9.6-bin.zip' -OutFile '.mvn\maven.zip'; Write-Host 'Extracting...'; Expand-Archive -Path '.mvn\maven.zip' -DestinationPath '.mvn' -Force; Remove-Item '.mvn\maven.zip' -Force; Write-Host 'Maven setup complete!'"

if exist ".mvn\apache-maven-3.9.6\bin\mvn.cmd" (
    echo [INFO] Starting Spring Boot application with H2 database...
    call .mvn\apache-maven-3.9.6\bin\mvn.cmd spring-boot:run
) else (
    echo [ERROR] Failed to setup Maven. Please install Maven manually or open in IntelliJ IDEA / VS Code.
    pause
)

:end
pause

