@echo off
echo Starting Order Management System Services...
echo.

echo Starting Customer Service...
start "Customer Service" cmd /k "cd /d %~dp0backend\customer-service && sbt run"
timeout /t 5 /nobreak >nul

echo Starting Order Service...
start "Order Service" cmd /k "cd /d %~dp0backend\order-service && sbt run"
timeout /t 5 /nobreak >nul

echo Starting Payment Service...
start "Payment Service" cmd /k "cd /d %~dp0backend\payment-service && sbt run"
timeout /t 5 /nobreak >nul

echo Starting Product Service...
start "Product Service" cmd /k "cd /d %~dp0backend\product-service && sbt run"
timeout /t 5 /nobreak >nul

echo Starting Report Service...
start "Report Service" cmd /k "cd /d %~dp0backend\report-service && sbt run"
timeout /t 5 /nobreak >nul

echo Starting User Service...
start "User Service" cmd /k "cd /d %~dp0backend\user-service && sbt run"
timeout /t 10 /nobreak >nul

echo Starting API Gateway...
start "API Gateway" cmd /k "cd /d %~dp0backend\api-gateway && sbt run"
timeout /t 15 /nobreak >nul

echo Starting Frontend (Angular)...
start "Frontend" cmd /k "cd /d %~dp0frontend && npm start"

echo.
echo All services started!
echo Frontend will be available at http://localhost:4200
echo API Gateway is available at http://localhost:8080
echo.
pause
