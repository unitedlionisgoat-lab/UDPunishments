@echo off
setlocal
set MAVEN=C:\Users\Zaid\Documents\Codex\2026-06-30\a\work\tools\apache-maven-3.9.9\bin\mvn.cmd
if exist "%MAVEN%" (
  call "%MAVEN%" clean package
) else (
  mvn clean package
)
