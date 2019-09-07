@echo off

setlocal

if "%1"=="" goto help
if "%2"=="" goto help

if "%ANT_HOME%"=="" set ANT_HOME=z:\apps\apache-ant-1.8.2

call "%ANT_HOME%"\bin\ant -Dlocale=%1 -Dshort=%2 -f res-utf8.xml -v
goto end

:help
echo Usage: %0 full_locale short_locale
echo Example:
echo        %0 en_US en
goto end

:end
