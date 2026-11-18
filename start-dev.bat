@echo off
REM =====================================================================
REM  Bloop - arranque del entorno de desarrollo en Windows
REM
REM  1. Crea .env a partir de .env.example si no existe.
REM  2. Base de datos:
REM       - Con Docker:  docker compose up -d (PostgreSQL 5432 + Mailpit)
REM       - Sin Docker:  PostgreSQL embebido (puerto 5433, datos en backend\.local-db)
REM  3. Abre una ventana con Spring Boot (http://localhost:8080)
REM  4. Abre una ventana con Angular   (http://localhost:4200)
REM  5. Abre el navegador cuando todo responde.
REM
REM  Opcion:  start-dev.bat --no-docker   fuerza el modo sin Docker.
REM  Para detener todo: stop-dev.bat
REM =====================================================================
setlocal EnableDelayedExpansion
cd /d "%~dp0"
title Bloop - arranque

echo.
echo  ==== Bloop: entorno de desarrollo ====
echo.

where java >nul 2>&1 || (echo [ERROR] No se encuentra Java. Instala JDK 21 o superior. & goto :fail)
where node >nul 2>&1 || (echo [ERROR] No se encuentra Node.js. Instala Node 22 LTS. & goto :fail)

if not exist ".env" (
  copy ".env.example" ".env" >nul
  echo [OK] Creado .env a partir de .env.example
)

REM ---------- Base de datos ----------
set "USE_DOCKER=0"
if /I not "%~1"=="--no-docker" (
  where docker >nul 2>&1 && (
    docker info >nul 2>&1 && set "USE_DOCKER=1"
  )
)

if "%USE_DOCKER%"=="1" (
  echo [..] Arrancando PostgreSQL y Mailpit con Docker Compose...
  docker compose up -d postgres mailpit || goto :fail
  set "BACKEND_CMD=mvnw.cmd spring-boot:run"
  echo [OK] PostgreSQL en localhost:5432 ^| Correos de prueba en http://localhost:8025
) else (
  echo [i ] Docker no disponible: se usara PostgreSQL embebido ^(puerto 5433^).
  set "BACKEND_CMD=mvnw.cmd spring-boot:test-run"
)

REM PostgreSQL embebido necesita el Visual C++ Redistributable (vcruntime140.dll).
REM Sin el, initdb.exe falla sin mostrar ningun mensaje.
if "%USE_DOCKER%"=="0" if not exist "%SystemRoot%\System32\vcruntime140.dll" (
  echo [ERROR] Falta Microsoft Visual C++ Redistributable ^(x64^), necesario para PostgreSQL embebido.
  echo         Instalalo con:  winget install Microsoft.VCRedist.2015+.x64
  echo         o descargalo de https://aka.ms/vs/17/release/vc_redist.x64.exe y vuelve a ejecutar start-dev.bat
  goto :fail
)

REM ---------- Backend ----------
echo [..] Abriendo ventana del backend (Spring Boot)...
start "Bloop backend" /D "%~dp0backend" cmd /k "!BACKEND_CMD!"

REM ---------- Frontend ----------
if not exist "frontend\node_modules" (
  echo [..] Instalando dependencias de Angular ^(solo la primera vez^)...
  pushd frontend
  call npm install || (popd & goto :fail)
  popd
)
echo [..] Abriendo ventana del frontend (Angular)...
start "Bloop frontend" /D "%~dp0frontend" cmd /k "npm start"

REM ---------- Esperar y abrir navegador ----------
echo [..] Esperando a que el backend responda en http://localhost:8080 ...
set /a TRIES=0
:wait_backend
set /a TRIES+=1
powershell -NoProfile -Command "try { (Invoke-WebRequest -UseBasicParsing http://localhost:8080/actuator/health -TimeoutSec 2).StatusCode } catch { exit 1 }" >nul 2>&1 && goto :wait_frontend
if !TRIES! GEQ 90 (echo [!] El backend tarda demasiado. Revisa su ventana. & goto :summary)
ping -n 3 127.0.0.1 >nul
goto :wait_backend

:wait_frontend
echo [OK] Backend listo.
set /a TRIES=0
:wait_front_loop
set /a TRIES+=1
powershell -NoProfile -Command "try { (Invoke-WebRequest -UseBasicParsing http://localhost:4200 -TimeoutSec 2).StatusCode } catch { exit 1 }" >nul 2>&1 && goto :open
if !TRIES! GEQ 60 (echo [!] Angular tarda demasiado. Revisa su ventana. & goto :summary)
ping -n 3 127.0.0.1 >nul
goto :wait_front_loop

:open
echo [OK] Frontend listo.
start "" http://localhost:4200

:summary
REM Sin expansion retardada para poder mostrar el caracter "!".
setlocal DisableDelayedExpansion
echo.
echo  ------------------------------------------------------------------
echo   Web publica y panel:  http://localhost:4200   (panel en /admin)
echo   API + Swagger:        http://localhost:8080/swagger-ui.html
if "%USE_DOCKER%"=="1" echo   Correos de prueba:    http://localhost:8025
echo   Usuarios demo (contrasena: DEMO_PASSWORD de .env, por defecto Demo1234!):
echo     admin@noctambula.test  ^| puerta@noctambula.test  ^| cliente@bloop.test
echo     superadmin@bloop.test
echo   Para detener todo:    stop-dev.bat
echo  ------------------------------------------------------------------
echo.
endlocal
endlocal
exit /b 0

:fail
echo.
echo [ERROR] No se pudo arrancar el entorno. Revisa los mensajes anteriores.
endlocal
exit /b 1
