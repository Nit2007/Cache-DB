@echo off
CLS
echo ===================================================
echo [1/2] Compiling Java source files...
echo ===================================================

:: Create the out directory if it doesn't exist
if not exist out mkdir out

:: Compile all files inside the package directory
javac -d out src/com/miniredis/*.java src/com/cachedb/*.java src/com/cachedb/db/*.java src/com/cachedb/cache/*.java

:: Check if compilation was successful (Error level 0 means success)
if %errorlevel% neq 0 (
    echo.
    echo [ERROR] Compilation failed! Please check your syntax errors above.
    pause
    exit /b %errorlevel%
)

echo.
echo ===================================================
echo [2/2] Launching mini-redis Server...
echo ===================================================
echo Server is running! Press Ctrl+C to stop.
echo.
echo (Tip: You can run 'run.bat --aof everysec' to enable AOF persistence)
echo.

:: Run the compiled main class with any passed arguments
java -cp out com.miniredis.RedisServer %*