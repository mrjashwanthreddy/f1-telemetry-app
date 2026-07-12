@echo off
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo ==============================================
echo 1. Cleaning old build distributions...
echo ==============================================
if exist "target\custom-runtime" rd /s /q "target\custom-runtime"
if exist "target\dist" rd /s /q "target\dist"
if exist "target\input" rd /s /q "target\input"

echo ==============================================
echo 2. Generating minimal custom JRE (jlink)...
echo ==============================================
jlink --no-header-files --no-man-pages --strip-debug --add-modules java.base,java.logging,java.xml,java.naming,java.sql,java.transaction.xa,java.compiler,java.net.http,java.scripting,java.security.jgss,java.security.sasl,java.instrument,java.desktop,java.management,jdk.unsupported,jdk.management,java.rmi,jdk.crypto.ec,jdk.crypto.cryptoki --output target\custom-runtime
if %ERRORLEVEL% neq 0 (
    echo ERROR: jlink failed!
    exit /b %ERRORLEVEL%
)

echo ==============================================
echo 3. Preparing clean input directory...
echo ==============================================
mkdir target\input
copy target\f1-telemetry-0.0.1-SNAPSHOT.jar target\input\f1-telemetry-0.0.1-SNAPSHOT.jar

echo ==============================================
echo 4. Packaging into standalone EXE (jpackage)...
echo ==============================================
jpackage --type app-image --dest target\dist --name F1Telemetry --input target\input --main-jar f1-telemetry-0.0.1-SNAPSHOT.jar --main-class org.springframework.boot.loader.launch.JarLauncher --runtime-image target\custom-runtime --win-console
if %ERRORLEVEL% neq 0 (
    echo ERROR: jpackage failed!
    exit /b %ERRORLEVEL%
)

echo ==============================================
echo SUCCESS! standalone app created in target\dist\F1Telemetry
echo ==============================================
