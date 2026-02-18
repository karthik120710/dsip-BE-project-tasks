@echo off
setlocal

cd /d "%~dp0"

java -Dmaven.multiModuleProjectDirectory="%CD%" -cp ".mvn\wrapper\maven-wrapper.jar" org.apache.maven.wrapper.MavenWrapperMain %*

endlocal
