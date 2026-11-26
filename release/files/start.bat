@echo off
rem TechnoSoft Bonus 1.0 - server start (Windows 8 and later).
rem Web interface and REST API: http://localhost:8080 (port: BONUS_PORT). Stop: Ctrl+C.
rem Settings may be set as environment variables before start or edited below.
rem Every setting is described in config\application.yml (see also README.txt).
setlocal EnableExtensions
cd /d "%~dp0"

rem ---- Database connection (defaults) -------------------------------------------------------
if not defined BONUS_DB_HOST set "BONUS_DB_HOST=localhost"
if not defined BONUS_DB_PORT set "BONUS_DB_PORT=5432"
if not defined BONUS_DB_NAME set "BONUS_DB_NAME=technosoft_bonus"
if not defined BONUS_DB_USER set "BONUS_DB_USER=postgres"
rem set "BONUS_DB_PASSWORD=your-password"
if not defined BONUS_PORT set "BONUS_PORT=8080"

rem ---- Secrets (required) ---------------------------------------------------------------------
rem Generate a value in PowerShell (N random bytes in base64; N=48 for BONUS_JWT_SECRET, N=32 for BONUS_SECRET_KEY):
rem   $b = New-Object byte[] 32; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)
rem then uncomment the lines below and paste the values (or set them as environment variables):
rem set "BONUS_JWT_SECRET=..."
rem set "BONUS_SECRET_KEY=..."

rem ---- Java memory: heap limit 512 MB -------------------------------------------------------
if not defined JAVA_OPTS set "JAVA_OPTS=-Xms128m -Xmx512m"

if not defined BONUS_JWT_SECRET goto :no_secrets
if not defined BONUS_SECRET_KEY goto :no_secrets

set "JAVA_EXE=java"
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
"%JAVA_EXE%" -version >nul 2>&1
if errorlevel 1 goto :no_java

rem First line of "java -version": openjdk version "21.0.2" ... -> 21; Java 8: "1.8.0_x" -> 1.
set "JVER="
set "JMAJOR="
"%JAVA_EXE%" -version 2> "%TEMP%\technosoft_bonus_java.txt"
for /f "usebackq tokens=3" %%V in ("%TEMP%\technosoft_bonus_java.txt") do if not defined JVER set "JVER=%%~V"
del "%TEMP%\technosoft_bonus_java.txt" >nul 2>&1
if defined JVER for /f "delims=.-+" %%M in ("%JVER%") do set "JMAJOR=%%M"
if not defined JMAJOR goto :run
if %JMAJOR% LSS 21 goto :old_java

:run
if not exist logs mkdir logs
rem UTF-8 console: server messages are in Russian (this file itself contains only ASCII).
chcp 65001 >nul
echo TechnoSoft Bonus: http://localhost:%BONUS_PORT%  (DB %BONUS_DB_HOST%:%BONUS_DB_PORT%/%BONUS_DB_NAME%, user %BONUS_DB_USER%)
"%JAVA_EXE%" %JAVA_OPTS% -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -jar "app\technosoft-bonus.jar" --spring.config.additional-location=file:./config/application.yml %*
exit /b %errorlevel%

:no_secrets
echo ERROR: secrets BONUS_JWT_SECRET and/or BONUS_SECRET_KEY are not set.
echo Generate them (README.txt, step 4) and set them as environment variables
echo or in the "Secrets" block at the top of start.bat.
exit /b 1

:no_java
echo ERROR: Java not found. Install Java 21 (JDK or JRE), add it to PATH or set JAVA_HOME.
exit /b 1

:old_java
echo ERROR: Java 21 or later is required, found version %JMAJOR% (%JAVA_EXE%).
exit /b 1
