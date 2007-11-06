@echo off

set JAVA_HOME=C:\Program Files\Java\jdk1.5.0_12
set PATH=%JAVA_HOME%\bin;%PATH%

if "%1"=="" goto help
if "%2"=="" goto help
if not exist properties\build-%1.properties goto vendor

cmd /c ant -f res.xml
cmd /c ant -Dinclude.properties=properties\build-%1.properties -f build.xml %2
goto end

:vendor
echo No such vendor - %1
goto end

:help
echo Usage: %0 vendor device
echo Example:
echo        %0 public generic
goto end

:end
