@echo off

set JAVA_HOME=C:\Program Files\Java\jdk1.5.0_10
set PATH=C:\Program Files\Java\jdk1.5.0_10\bin;%PATH%

cmd /c ant generic
cmd /c ant std
cmd /c ant j9
rem cmd /c ant rim41
cmd /c ant rim42