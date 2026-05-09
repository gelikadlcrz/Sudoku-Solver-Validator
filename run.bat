@echo off
echo Batch Sudoku Validator and Solver
echo PLT-Parallel Bridge Matrix - Java
echo.
java -jar sudoku-solver.jar %*
if errorlevel 1 (
    echo.
    echo ERROR: Make sure Java 17+ is installed. Download from https://adoptium.net
    pause
)
