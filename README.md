# TrekBuddy
TrekBuddy is an old, originally J2ME, application for GPS tracking etc. It was intended to be simple easy-to-use application that would run fast even on lowend devices (remember Siemens S65, Nokia 6230i... ?).

Homepage: [TrekBuddy](http://www.trekbuddy.net/forum/) (forum)

## State of the project
You can tell by looking at the tools, naming convetion etc that it is on old, poorly maintained project. While during the course of time TrekBuddy was made available for various platforms (Java and Symbian phones, Blackberries, Windows Phone), it is now used on Android. This repository may not (YET) hold everything needed to build TrekBuddy for various platforms (most of them obsolete), but in the least it should have all needed for building APK. 

## Dependencies
- microemulator - J2ME implementation (and more) for Android by Bartek Teodorczyk. I believe it is now available in Google archive only. _I have been using mix of CVS and SVN for TrekBuddy, and because I wasn't able to convert SVN to GIT I lost commit history for microemu changes. I will try to at least provide diff for externals/microemu sources against original._
- HECL - scripting language by David Welton [github](https://github.com/davidw/hecl). _The same with diff of externals/hecl sources against original applies._

There may be remains of others, but that is most likely obsolete and should be removed.

## Building
The following tools are needed to build TrekBuddy:
- Java (1.7 works ok)
- Ant (1.8 works ok)
- Sun/Oracle WTK toolkit (2.5.2_01 works ok) 
- Android SDK (26 have been used lately)

The sources are preprocessed using Antenna 1.0.2 (other version may NOT work).

Building on Linux was never tested (there may be Windows paths left in build files).

Building APK:
1. build `microemulator` (only needed first time or when changed): go to `externals/microemulator`and execute `build-ant.bat`
2. build 'android' target by running `build.bat public android`  

You should find `trekbuddy.apk` in `dist\public\android` folder.

### Contact
kruhc@seznam.cz
