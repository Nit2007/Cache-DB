@echo off
setlocal enabledelayedexpansion

:: ============================================================
:: compress.bat
:: Compresses the entire project (folder this script sits in)
:: into a zip file placed in the same root folder.
:: Excludes common build/junk folders to keep the zip lean.
:: ============================================================

set "ROOT=%~dp0"
cd /d "%ROOT%"

:: Project folder name (used as zip base name)
for %%I in ("%ROOT%.") do set "PROJECT_NAME=%%~nxI"

:: Timestamp for the zip file (yyyyMMdd_HHmmss)
for /f "tokens=1-3 delims=/ " %%a in ('date /t') do set "DATE=%%a"
for /f "tokens=1-2 delims=: " %%a in ('time /t') do set "TIME=%%a-%%b"
set "STAMP=%date:~-4%%date:~4,2%%date:~7,2%_%time:~0,2%%time:~3,2%"
set "STAMP=%STAMP: =0%"

set "ZIPNAME=%PROJECT_NAME%_%STAMP%.zip"
set "ZIPPATH=%ROOT%%ZIPNAME%"

echo Compressing project "%PROJECT_NAME%" ...
echo Output: %ZIPPATH%
echo.

:: Use PowerShell Compress-Archive, excluding common junk/build folders
powershell -NoProfile -ExecutionPolicy Bypass -Command ^
    "$root = '%ROOT%';" ^
    "$zip  = '%ZIPPATH%';" ^
    "$exclude = @('.git','.idea','.vscode','target','build','out','bin','node_modules','*.zip');" ^
    "if (Test-Path $zip) { Remove-Item $zip -Force };" ^
    "$items = Get-ChildItem -Path $root -Force | Where-Object { $_.Name -notin $exclude -and ($_.Extension -ne '.zip') };" ^
    "Compress-Archive -Path $items.FullName -DestinationPath $zip -CompressionLevel Optimal;"

if exist "%ZIPPATH%" (
    echo.
    echo Done. Created: %ZIPNAME%
) else (
    echo.
    echo ERROR: Zip creation failed.
)

pause