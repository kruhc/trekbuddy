@echo off

setlocal

if "%1"=="" goto help
if "%2"=="" goto help
if not exist properties\build-%1.properties goto vendor

if "%ANT_HOME%"=="" set ANT_HOME=z:\apps\apache-ant-1.8.2

rem override
rem set JAVA_HOME=c:\Program Files (x86)\Java\jdk1.7.0_71
set JAVA_HOME=c:\Program Files (x86)\Java\jdk1.8.0_144

java -cp tools/classes;%ANT_HOME%/lib/ant.jar BuildVersion public

mkdir dist\%1\%2 2>NUL 

cmd /c %ANT_HOME%\bin\ant -Dinclude.properties=properties\build-%1.properties -l dist\%1\%2\build.log -f build.xml %2

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
if "%ERRORLEVEL%"=="0" (
    echo Build successful [%ERRORLEVEL%]
) else (
    echo Build failed! [%ERRORLEVEL%]
)
exit /B %ERRORLEVEL%