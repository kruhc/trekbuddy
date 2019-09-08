# TrekBuddy   ![logo](http://www.trekbuddy.net/icon.svg "Logo")
TrekBuddy is an old, originally J2ME, application for GPS tracking etc. It was intended to be simple easy-to-use application that would run fast even on lowend devices (remember Siemens S65, Nokia 6230i... ?).

Homepage: [TrekBuddy](http://www.trekbuddy.net/forum/) (forum)

## State of the project
You can tell by looking at the tools, naming convetion etc that it is oa old, poorly maintained project. While during the course of time TrekBuddy was made available for various platforms (Java and Symbian phones, Blackberries, Windows Phone), it is now used on Android. This repository may not (YET) hold everything needed to build TrekBuddy for various platforms (most of them obsolete), but in the least it should have all needed for building APK. 

WORK IN PROGRESS: sources and other were migrated from CVS and SVN, it was not 100% successful migration so there may be obsolete dangling files and/or version that do not match those used to build current APK in PlayStore.

## Dependencies
- microemulator - J2ME implementation (and more) for Android by Bartek Teodorczyk. I believe it is now available in Google archive only. _I have been using mix of CVS and SVN for TrekBuddy, and because I wasn't able to convert SVN to GIT I lost commit history for microemu changes. I will try to at least provide diff for externals/microemu sources against original soon._
- HECL - scripting language by David Welton [(github)](https://github.com/davidw/hecl). _The same with diff of externals/hecl sources against original applies._
- KXML - low footprint XML parser by Stefan Haustein

There may be remains of others, but that is most likely obsolete and should be removed.

## Building
The following tools are needed to build TrekBuddy:
- Java (1.8 works ok for Android target, for other targets 1.7 may work better)
- Ant (1.8 works ok)
- Sun/Oracle WTK toolkit (2.5.2_01 works ok) 
- Android SDK (versions 14 and 26), Android ANT+ SDK

Building on Linux was never attempted (there are very likely Windows paths and tools references left in build files).

__The sources contains Antenna preprocessor directives__. Used Antenna 1.0.2 (you need `antenna-bin-1.0.2.jar` in `lib` folder), other version may NOT work.

Build helper tools:
`ant -f build-tools.xml

### Android
Building APK:

1. build `microemulator` core modules - go to `externals/microemulator`and execute `build-ant.bat`  
This is only needed once, or of course when you make a change in either `cldc`, `midp`, `microemu-javase` or `microemu-extensions\microemu-jsr-75` module.

2. build language resources you want to include in the package by using either res-ascii or res-utf8 script, eg. `res-ascii.bat en en` or `res-utf8 fr fr` (first param can be full locale but it must match file suffix in `res-lang` folder). English res file should always be included in the package.

3. build 'android' target by running `build.bat public android`  (check that ANT_HOME and JAVA_HOME in `build.bat` match your system)
You should find `trekbuddy.apk` in `dist\public\android` folder. The APK will be unsigned unless you provide signing key (see 'sign' target in `externals/microemulator/microemu-android/build-trekbuddy.xml`). If the build fails, check `dist/public/android/build.log`.

