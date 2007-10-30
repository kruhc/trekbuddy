@echo off

set JAVA_HOME=C:\Program Files\Java\jdk1.5.0_12
set PATH=%JAVA_HOME%\bin;%PATH%

cmd /c ant generic %*
cmd /c ant std %*
cmd /c ant j9 %*
cmd /c ant rim41 %*
cmd /c ant rim42 %*