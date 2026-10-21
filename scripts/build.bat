@echo off
rem Builds the launcher jar on Windows.
rem Requirements: JDK 21 (javac, jar, jpackage) available on PATH.
setlocal enabledelayedexpansion

cd /d "%~dp0.."
set ROOT=%CD%
set BUILD=%ROOT%\build
set CLASSES=%BUILD%\classes
set TEST_CLASSES=%BUILD%\test-classes
set DIST=%BUILD%\dist

if exist "%BUILD%" rmdir /s /q "%BUILD%"
mkdir "%CLASSES%" "%TEST_CLASSES%" "%DIST%" 2>nul

echo ==^> Compiling main sources
dir /s /b src\main\java\*.java > "%BUILD%\sources.txt"
javac -encoding UTF-8 -d "%CLASSES%" "@%BUILD%\sources.txt"
if errorlevel 1 goto :failed

echo ==^> Compiling tests
dir /s /b src\test\java\*.java > "%BUILD%\test-sources.txt"
javac -encoding UTF-8 -cp "%CLASSES%" -d "%TEST_CLASSES%" "@%BUILD%\test-sources.txt"
if errorlevel 1 goto :failed

echo ==^> Running tests
for %%f in (src\test\java\dev\cookielauncher\test\*Test.java) do (
    set "NAME=%%~nf"
    echo dev.cookielauncher.test.!NAME!>> "%TEST_CLASSES%\tests.list"
)
java -cp "%CLASSES%;%TEST_CLASSES%" dev.cookielauncher.test.TestRunner
if errorlevel 1 goto :testsfailed

echo ==^> Building jar
> "%BUILD%\MANIFEST.MF" echo Manifest-Version: 1.0
>> "%BUILD%\MANIFEST.MF" echo Main-Class: dev.cookielauncher.Main
>> "%BUILD%\MANIFEST.MF" echo Implementation-Title: Cookie Launcher
>> "%BUILD%\MANIFEST.MF" echo Implementation-Version: 1.0.0
jar --create --file "%DIST%\cookie-launcher.jar" --manifest "%BUILD%\MANIFEST.MF" -C "%CLASSES%" .
echo     %DIST%\cookie-launcher.jar

if /i "%~1"=="--package" (
    echo ==^> Building native app image
    jpackage --type app-image --name CookieLauncher --input "%DIST%" ^
        --main-jar cookie-launcher.jar --main-class dev.cookielauncher.Main ^
        --dest "%BUILD%\app" --java-options "-Dfile.encoding=UTF-8" ^
        --java-options "-Dsun.java2d.uiScale.enabled=true" ^
        --app-version 1.0.0 --vendor "Cookie Launcher contributors" ^
        --description "Open source Minecraft: Java Edition launcher"
    echo     %BUILD%\app\CookieLauncher\CookieLauncher.exe
)

echo ==^> Done
exit /b 0

:failed
echo !! Compilation failed
exit /b 1

:testsfailed
echo !! Tests failed; refusing to package
exit /b 1
