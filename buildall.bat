@echo off

if "%1"=="" goto help
if not exist properties\build-%1.properties goto vendor

echo Build version 'standard' ...
cmd /c build %1 standard en_US
echo Build version 'standard lite' ...
cmd /c build %1 standard-lite en_US
echo Build version 'basic' ...
cmd /c build %1 basic en_US
echo Build version 'j9' ...
cmd /c build %1 j9 en_US
rem cmd /c build %1 rim41 en_US
echo Build version 'rim42' ...
cmd /c build %1 rim42 en_US
echo Build version 'android' ...
cmd /c build %1 android en_US
goto end

:vendor
echo No such vendor - %1
goto end

:help
echo Usage: %0 vendor
echo Example:
echo        %0 public
goto end

:end
