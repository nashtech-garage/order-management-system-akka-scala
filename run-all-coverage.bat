@echo off

echo Skipping Common (already done)...
echo Skipping User Service (already done)...
echo Skipping Customer Service (already done)...
echo Skipping Product Service (already done)...

echo Running coverage for Order Service...
cd backend\order-service
call sbt clean coverage test
call sbt coverage coverageReport
rem Proceed even if failed

echo Running coverage for Payment Service...
cd ..\payment-service
call sbt clean coverage test
call sbt coverage coverageReport
rem Proceed even if failed

echo Running coverage for Report Service...
cd ..\report-service
call sbt clean coverage test
call sbt coverage coverageReport
rem Proceed even if failed

echo Running coverage for API Gateway...
cd ..\api-gateway
call sbt clean coverage test
call sbt coverage coverageReport
rem Proceed even if failed

echo All coverage runs completed.
