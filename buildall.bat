@echo off

if "%1"=="" goto help
if not exist properties\build-%1.properties goto vendor

cmd /c build %1 generic
cmd /c build %1 std
cmd /c build %1 j9
cmd /c build %1 rim41
cmd /c build %1 rim42
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
