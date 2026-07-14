@echo off
cls
echo ===================================================
echo Cleaning mini-redis temporary and persistence files...
echo ===================================================

:: -------------------------------------------------
:: 1️⃣  Stop any running Redis server (java process)
:: -------------------------------------------------
echo.
echo Attempting to stop any running mini-redis server...
for /f "tokens=2 delims=," %%P in ('tasklist /FI "IMAGENAME eq java.exe" /FO CSV /NH') do (
    echo Stopping Java process PID %%P
    taskkill /F /PID %%P >nul 2>&1
)

:: Give the OS a moment to release file handles
timeout /t 2 /nobreak >nul

:: -------------------------------------------------
:: 2️⃣  Delete persistence files (ignore errors)
:: -------------------------------------------------
set "FILES=appendonly.aof dump.rdb dump.rdb.tmp"
for %%F in (%FILES%) do (
    if exist "%%F" (
        del /f /q "%%F"
        if not exist "%%F" (
            echo Deleted %%F
        ) else (
            echo *** Failed to delete %%F (still in use)
        )
    )
)

:: -------------------------------------------------
:: 3️⃣  Delete compiled classes & the out folder
:: -------------------------------------------------
if exist out (
    rmdir /s /q out
    if not exist out (
        echo Deleted out directory and compiled .class files
    ) else (
        echo *** Failed to delete out directory
    )
)

:: -------------------------------------------------
:: 4️⃣  Clean stray .class files that might be left in src
:: -------------------------------------------------
del /s /q src\*.class >nul 2>&1

:: -------------------------------------------------
:: 5️⃣  Delete .zip files in the project root
:: -------------------------------------------------
for %%F in (*.zip) do (
    if exist "%%F" (
        del /f /q "%%F"
        if not exist "%%F" (
            echo Deleted %%F
        ) else (
            echo *** Failed to delete %%F
        )
    )
)

echo.
echo Clean complete!
exit /b 0