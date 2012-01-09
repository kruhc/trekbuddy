@echo off

set RESOURCE_LANG=en
echo Using locale '%RESOURCE_LANG%'

if "%1"=="" goto help
if not exist properties\build-%1.properties goto vendor

echo Build version 'standard' ...
cmd /c build %1 standard %RESOURCE_LANG%

echo Build version 'standard lite' ...
cmd /c build %1 standard-lite %RESOURCE_LANG%

echo Build version 'symbian' ...
cmd /c build %1 symbian %RESOURCE_LANG%

echo Build version 'basic' ...
cmd /c build %1 basic %RESOURCE_LANG%

echo Build version 'j9' ...
cmd /c build %1 j9 %RESOURCE_LANG%

rem echo Build version 'rim41' ...
rem cmd /c build %1 rim41 %RESOURCE_LANG%

echo Build version 'rim42' ...
cmd /c build %1 rim42 %RESOURCE_LANG%

echo Build version 'rim50' ...
cmd /c build %1 rim50 %RESOURCE_LANG%

echo Build version 'android' ...
cmd /c build %1 android %RESOURCE_LANG%

echo Build version 'android-backport' ...
cmd /c build %1 android-backport %RESOURCE_LANG%

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
