@echo off
cd /d %~dp0
rem Called by the program when a build/package is needed (auto update, rebuild).
rem Compiles src\*.java into out\production\ScrollSched, then packages sc.jar.
setlocal
set "JAVAC="
set "JAR="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\javac.exe" (
    set "JAVAC=%JAVA_HOME%\bin\javac.exe"
    set "JAR=%JAVA_HOME%\bin\jar.exe"
)
if not defined JAVAC for /d %%D in ("%ProgramFiles%\Java\jdk*") do (
    if exist "%%D\bin\javac.exe" (
        set "JAVAC=%%D\bin\javac.exe"
        set "JAR=%%D\bin\jar.exe"
    )
)
if not defined JAVAC for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk*") do (
    if exist "%%D\bin\javac.exe" (
        set "JAVAC=%%D\bin\javac.exe"
        set "JAR=%%D\bin\jar.exe"
    )
)
if not defined JAVAC (
    javac -version >nul 2>&1
    if not errorlevel 1 (
        set "JAVAC=javac"
        set "JAR=jar"
    )
)
if not defined JAVAC (
    echo [ERROR] javac not found. Install a JDK or set JAVA_HOME.
    exit /b 2
)
if not exist "src" (
    echo [ERROR] src directory not found.
    exit /b 2
)
if not exist "out\production\ScrollSched" mkdir "out\production\ScrollSched"
"%JAVAC%" -encoding UTF-8 -d "out\production\ScrollSched" src\*.java
if errorlevel 1 (
    echo [ERROR] javac compilation failed.
    exit /b 1
)
"%JAR%" cfe sc.jar Main -C "out\production\ScrollSched" .
if errorlevel 1 (
    echo [ERROR] jar packaging failed.
    exit /b 1
)
echo [OK] Compiled and packaged sc.jar
exit /b 0
