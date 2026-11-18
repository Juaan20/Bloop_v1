@echo off
REM =====================================================================
REM  Bloop - detiene de forma segura los servicios de desarrollo:
REM   1. PostgreSQL embebido (si se uso el modo sin Docker), con pg_ctl.
REM   2. Procesos que escuchan en 8080 (Spring Boot) y 4200 (Angular).
REM   3. Contenedores de Docker Compose (docker compose stop: NO borra datos).
REM =====================================================================
setlocal EnableDelayedExpansion
cd /d "%~dp0"
echo.
echo  ==== Bloop: deteniendo servicios ====

REM ---------- 1. PostgreSQL embebido ----------
set "PGDATA=%~dp0backend\.local-db\pgdata"
if exist "%PGDATA%\postmaster.pid" (
  set "PGCTL="
  for /d %%D in ("%TEMP%\embedded-pg\PG-*") do (
    if exist "%%D\bin\pg_ctl.exe" set "PGCTL=%%D\bin\pg_ctl.exe"
  )
  if defined PGCTL (
    echo [..] Deteniendo PostgreSQL embebido...
    "!PGCTL!" -D "%PGDATA%" stop -m fast
  ) else (
    echo [!] No se encontro pg_ctl.exe; PostgreSQL se cerrara junto con el backend.
  )
)

REM ---------- 2. Backend (8080) y frontend (4200) ----------
for %%P in (8080 4200) do (
  for /f "tokens=5" %%I in ('netstat -ano ^| findstr /R /C:":%%P .*LISTENING"') do (
    if not "%%I"=="0" (
      echo [..] Deteniendo proceso %%I en el puerto %%P...
      taskkill /PID %%I /T /F >nul 2>&1
    )
  )
)

REM ---------- 3. Docker ----------
where docker >nul 2>&1 && (
  docker info >nul 2>&1 && (
    echo [..] Deteniendo contenedores ^(los datos se conservan^)...
    docker compose stop
  )
)

echo [OK] Servicios detenidos. Puedes cerrar las ventanas "Bloop backend" y "Bloop frontend".
endlocal
