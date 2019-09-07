@echo off

set SYSPROPS=%SYSPROPS% -Djavac.debug=true "-Djavac.debuglevel=source,lines"

echo Building microemu-cldc ...
cd microemu-cldc
call ant %SYSPROPS%
cd ..
echo Done
echo Building microemu-midp ...
cd microemu-midp
call ant %SYSPROPS%
cd ..
echo Done
echo Building microemu-javase ...
cd microemu-javase
call ant %SYSPROPS%
cd ..
cd microemu-extensions\microemu-jsr-75
call ant %SYSPROPS%
cd ..\..
echo Done
echo Finished
