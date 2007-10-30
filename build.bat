@echo off

set JAVA_HOME=C:\Program Files\Java\jdk1.5.0_12
set PATH=%JAVA_HOME%\bin;%PATH%

ant -Dinclude.properties=build.properties %*