@echo off

setlocal

if "%1"=="" goto help
if "%2"=="" goto help
if "%3"=="" goto help
if not exist properties\build-%1.properties goto vendor

if "%ANT_HOME%"=="" set ANT_HOME=z:\apps\apache-ant-1.8.2

cmd /c %ANT_HOME%\bin\ant -Dinclude.properties=properties\build-%1.properties -Dlocale=%3 -l dist\%1\%2\build.log -f build.xml %2
goto end

:vendor
echo No such vendor - %1
goto end

:help
echo Usage: %0 vendor device locale
echo Example:
echo        %0 public generic en_US
goto end

:end
