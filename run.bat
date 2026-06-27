@echo off
CLS
echo ===================================================
echo [1/2] Compiling Java source files...
echo ===================================================

:: Create the out directory if it doesn't exist
if not exist out mkdir out

:: Compile all files inside the package directory
javac -d out src/com/miniredis/*.java

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

:: Run the compiled main class
java -cp out com.miniredis.RedisServer