@echo off
setlocal
set PATH=%PATH%;c:\Windows\Microsoft.NET\Framework\v4.0.30319
@echo on
msbuild TrackingApp.csproj /property:Configuration=Release /t:Build /verbosity:minimal