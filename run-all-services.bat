@echo off
echo Starting Order Management System Services...
echo.

echo Starting Customer Service...
start "Customer Service" cmd /k "cd /d %~dp0customer-service && sbt run"
timeout /t 5 /nobreak >nul

echo Starting Order Service...
start "Order Service" cmd /k "cd /d %~dp0order-service && sbt run"
timeout /t 5 /nobreak >nul

echo Starting Payment Service...
start "Payment Service" cmd /k "cd /d %~dp0payment-service && sbt run"
timeout /t 5 /nobreak >nul

echo Starting Product Service...
start "Product Service" cmd /k "cd /d %~dp0product-service && sbt run"
timeout /t 5 /nobreak >nul

echo Starting Report Service...
start "Report Service" cmd /k "cd /d %~dp0report-service && sbt run"
timeout /t 5 /nobreak >nul

echo Starting User Service...
start "User Service" cmd /k "cd /d %~dp0user-service && sbt run"
timeout /t 10 /nobreak >nul

echo Starting API Gateway (last)...
start "API Gateway" cmd /k "cd /d %~dp0api-gateway && sbt run"

echo.
echo All services started!
echo.
pause
