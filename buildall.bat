@echo off

if "%1"=="" goto help
if not exist properties\build-%1.properties goto vendor
if "%ANT_HOME%"=="" set ANT_HOME=z:\apps\apache-ant-1.8.2

set RESOURCE_LANG=en
echo Using locale '%RESOURCE_LANG%'

java -cp tools/classes;%ANT_HOME%/lib/ant.jar BuildVersion public

echo Build version 'standard' ...
call :build %1 standard %RESOURCE_LANG%

echo Build version 'standard lite' ...
call :build %1 standard-lite %RESOURCE_LANG%

echo Build version 'symbian' ...
call :build %1 symbian %RESOURCE_LANG%

echo Build version 'basic' ...
call :build %1 basic %RESOURCE_LANG%

echo Build version 'j9' ...
call :build %1 j9 %RESOURCE_LANG%

REM echo Build version 'rim41' ...
REM call :build %1 rim41 %RESOURCE_LANG%

echo Build version 'rim42' ...
call :build %1 rim42 %RESOURCE_LANG%

echo Build version 'rim50' ...
call :build %1 rim50 %RESOURCE_LANG%

echo Build version 'android' ...
call :build %1 android %RESOURCE_LANG%

echo Build version 'android-backport' ...
call :build %1 android-backport %RESOURCE_LANG%

echo Build version 'playbook' ...
echo COMMENTED OUT: call :build %1 playbook.signed %RESOURCE_LANG%

goto end

:build
cmd /c %ANT_HOME%\bin\ant -Dinclude.properties=properties\build-%1.properties -Dlocale=%RESOURCE_LANG% -l dist\%1\standard\build.log -f build.xml %2
echo.
exit /b

:vendor
echo No such vendor - %1
goto end

:help
echo Usage: %0 vendor
echo Example:
echo        %0 public
goto end

:end
