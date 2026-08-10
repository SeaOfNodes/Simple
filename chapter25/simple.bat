@echo off
setlocal

set "HERE=%~dp0"
set "RELEASE=%HERE%build\release"
set "JAR=%RELEASE%\Simple.jar"

if not defined SIMPLE_HOME set "SIMPLE_HOME=%RELEASE%"

java -jar "%JAR%" %*
