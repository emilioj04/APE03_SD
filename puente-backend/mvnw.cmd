@echo off
setlocal

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

if not "%JAVA_HOME%" == "" goto haveJavaHome
set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init
goto noJava

:haveJavaHome
set JAVA_EXE="%JAVA_HOME%\bin\java.exe"
if exist %JAVA_EXE% goto init

:noJava
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
goto end

:init
set MAVEN_PROJECTBASEDIR=%APP_HOME%
set WRAPPER_JAR="%APP_HOME%\.mvn\wrapper\maven-wrapper.jar"

if exist %WRAPPER_JAR% goto runWrapper
powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object System.Net.WebClient).DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar', '%WRAPPER_JAR%')}"

:runWrapper
%JAVA_EXE% -jar %WRAPPER_JAR% %*
if ERRORLEVEL 1 goto error
goto end

:error
exit /b 1

:end
exit /b 0
