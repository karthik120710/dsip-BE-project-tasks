@REM ----------------------------------------------------------------------------
@REM Maven Wrapper startup script for Windows
@REM ----------------------------------------------------------------------------

@echo off
setlocal

set BASEDIR=%~dp0
set MAVEN_WRAPPER_JAR=%BASEDIR%.mvn\wrapper\maven-wrapper.jar

@REM Download wrapper if not present
if not exist "%MAVEN_WRAPPER_JAR%" (
    echo Downloading Maven Wrapper...
    powershell -Command "& { (New-Object Net.WebClient).DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar', '%MAVEN_WRAPPER_JAR%') }"
)

@REM Find Java
if defined JAVA_HOME goto useJavaHome
set JAVA_CMD=java
goto runMaven

:useJavaHome
set JAVA_CMD=%JAVA_HOME%\bin\java

:runMaven
if not defined MAVEN_OPTS set MAVEN_OPTS=
"%JAVA_CMD%" %MAVEN_OPTS% -Dmaven.multiModuleProjectDirectory="%BASEDIR%" -cp "%MAVEN_WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*

endlocal
