@echo off
REM Bloop - ejecuta todas las pruebas (backend con PostgreSQL embebido + frontend con Vitest).
setlocal
cd /d "%~dp0"
echo ==== Tests backend (Maven) ====
pushd backend
call mvnw.cmd test || (popd & echo [ERROR] Fallan los tests del backend & exit /b 1)
popd
echo ==== Tests frontend (Angular + Vitest) ====
pushd frontend
if not exist node_modules call npm install
call npm test || (popd & echo [ERROR] Fallan los tests del frontend & exit /b 1)
popd
echo [OK] Todas las pruebas han pasado.
endlocal
