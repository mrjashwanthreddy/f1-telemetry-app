@echo off
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ==============================================
echo F1 Telemetry — Desktop EXE Builder
echo Backend: http://f1-telemetry-app.duckdns.org:8080
echo ==============================================

echo.
echo [1/5] Cleaning old build output...
if exist "target\custom-runtime" rd /s /q "target\custom-runtime"
if exist "target\dist_new"       rd /s /q "target\dist_new"
if exist "target\input"          rd /s /q "target\input"

echo.
echo [2/5] Building Spring Boot fat JAR (Maven)...
call mvn clean package -DskipTests -q
if %ERRORLEVEL% neq 0 (
    echo ERROR: Maven build failed!
    exit /b %ERRORLEVEL%
)
echo JAR built successfully.

echo.
echo [3/5] Generating minimal custom JRE (jlink)...
jlink ^
  --no-header-files --no-man-pages --strip-debug ^
  --add-modules java.base,java.logging,java.xml,java.naming,java.sql,java.transaction.xa,java.compiler,java.net.http,java.scripting,java.security.jgss,java.security.sasl,java.instrument,java.desktop,java.management,jdk.unsupported,jdk.management,java.rmi,jdk.crypto.ec,jdk.crypto.cryptoki,jdk.jsobject ^
  --output target\custom-runtime
if %ERRORLEVEL% neq 0 (
    echo ERROR: jlink failed!
    exit /b %ERRORLEVEL%
)

echo.
echo [4/5] Staging packaging input...
mkdir target\input
copy target\f1-telemetry-0.0.1-SNAPSHOT.jar target\input\f1-telemetry-0.0.1-SNAPSHOT.jar

echo.
echo [5/5] Packaging into standalone EXE (jpackage)...
jpackage ^
  --type app-image ^
  --dest target\dist_new ^
  --name F1Telemetry ^
  --input target\input ^
  --main-jar f1-telemetry-0.0.1-SNAPSHOT.jar ^
  --main-class org.springframework.boot.loader.launch.JarLauncher ^
  --runtime-image target\custom-runtime ^
  --java-options "-Djava.awt.headless=false" ^
  --java-options "--add-exports java.base/java.lang=ALL-UNNAMED" ^
  --java-options "--add-exports java.desktop/sun.awt=ALL-UNNAMED" ^
  --java-options "--add-exports java.desktop/sun.java2d=ALL-UNNAMED" ^
  --icon logo.ico
if %ERRORLEVEL% neq 0 (
    echo ERROR: jpackage failed!
    exit /b %ERRORLEVEL%
)

echo.
echo ==============================================
echo SUCCESS! Standalone app at: target\dist_new\F1Telemetry\
echo.
echo The app connects to the cloud backend at:
echo   http://f1-telemetry-app.duckdns.org:8080
echo.
echo NOTE: First launch downloads JCEF Chromium (~250MB)
echo       to %%USERPROFILE%%\.f1telemetry\jcef
echo ==============================================
