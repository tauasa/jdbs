@echo off
REM ════════════════════════════════════════════════════════════════════════════
REM  start-jdbs.bat  –  JDBS Server launcher for Windows
REM
REM  Usage:
REM    start-jdbs.bat                      GUI mode, defaults
REM    start-jdbs.bat --cli                Headless CLI mode
REM    start-jdbs.bat --port=7000          Custom port
REM    start-jdbs.bat --max-clients=25     Override max clients
REM    start-jdbs.bat --cli --port=7000    CLI + custom port
REM ════════════════════════════════════════════════════════════════════════════
setlocal EnableDelayedExpansion

REM ── Resolve script directory ─────────────────────────────────────────────────
set "SCRIPT_DIR=%~dp0"

REM ── Locate the fat jar (newest version in jdbs-server\target\) ──────────────
set "FAT_JAR="
for /f "delims=" %%F in ('dir /b /s /o-n "%SCRIPT_DIR%jdbs-server\target\jdbs-server-*-fat.jar" 2^>nul') do (
    if not defined FAT_JAR set "FAT_JAR=%%F"
)

if not defined FAT_JAR (
    echo ERROR: jdbs-server fat jar not found.
    echo        Run 'mvn package' from the project root first.
    echo        Expected: jdbs-server\target\jdbs-server-^<version^>-fat.jar
    exit /b 1
)

echo JDBS Server
echo   Jar:  %FAT_JAR%
echo   Args: %*
echo.

REM ── Java binary ──────────────────────────────────────────────────────────────
if defined JAVA_HOME (
    set "JAVA_BIN=%JAVA_HOME%\bin\java.exe"
) else (
    set "JAVA_BIN=java"
)

REM ── Java version check (17+) ─────────────────────────────────────────────────
for /f "tokens=3" %%V in ('"%JAVA_BIN%" -version 2^>^&1 ^| findstr /i "version"') do (
    set "JAVA_VER=%%V"
    goto :ver_found
)
:ver_found
REM Strip quotes and split on dot/underscore
set "JAVA_VER=%JAVA_VER:"=%"
for /f "tokens=1 delims=._" %%M in ("%JAVA_VER%") do set "JAVA_MAJOR=%%M"

if %JAVA_MAJOR% LSS 17 (
    echo ERROR: Java 17 or later is required ^(found Java %JAVA_MAJOR%^).
    exit /b 1
)

REM ── JVM flags ────────────────────────────────────────────────────────────────
set JVM_OPTS=-Xms64m -Xmx512m

REM Detect CLI mode → enable headless (no graphics subsystem needed)
echo %* | findstr /i "\-\-cli" >nul 2>&1
if not errorlevel 1 (
    set "JVM_OPTS=%JVM_OPTS% -Djava.awt.headless=true"
)

REM ── Launch ────────────────────────────────────────────────────────────────────
"%JAVA_BIN%" %JVM_OPTS% -jar "%FAT_JAR%" %*

endlocal
