<p align="center">
  <img src="frontend/public/brand/bloop-logo.png" alt="Bloop" height="72">
</p>

<h1 align="center">Bloop</h1>

<p align="center">
  Plataforma SaaS para el ocio nocturno: entradas, control de acceso, reservados VIP, TPV de barra e inventario en un solo panel.
</p>

<p align="center">
  Spring Boot 4.1 · Java 21 · Angular 21 · PostgreSQL 17 · Tailwind CSS 4
</p>

---

## Que problema resuelve

Una discoteca suele trabajar con herramientas separadas: una web de venta de entradas, una app para
leer QR en la puerta, una libreta o un Excel para los reservados, un TPV para la barra y otro Excel
para el inventario. Los datos no se cruzan y nadie sabe al final de la noche cuanto se ha vendido,
quien ha entrado o cuanta bebida se ha perdido.

**Bloop** reune todo eso en una unica aplicacion web multiempresa: cada empresa (y cada local)
ve solo sus datos, y el personal tiene permisos segun su rol (administrador, encargado, caja,
puerta, camarero VIP, relaciones publicas).

## Funcionalidades principales

**Web publica para clientes**
- Catalogo de eventos y locales, ficha de evento con sus dias (sesiones) y tipos de entrada.
- Compra nominal: datos de cada asistente, control de edad minima y codigos promocionales.
- Pago con Stripe en modo test o pago simulado en desarrollo (el resultado del pago solo se confirma por webhook firmado).
- Area de cliente: mis entradas con QR, transferir una entrada a otra cuenta, mis pedidos, mis reservas y privacidad (RGPD).
- Carta de la mesa VIP por QR: los clientes del reservado piden desde el movil.

**Backoffice del personal**
- Empresas, locales, usuarios, roles y permisos.
- Eventos: sesiones por dia con su propio cartel, tipos de entrada, cupos, pausa de ventas y cancelacion con reembolso automatico.
- Pedidos con filtros, reembolsos y exportacion CSV.
- Control de acceso: validacion de QR en la puerta (un QR solo vale una vez).
- Reservados VIP: cada sala con su plano dibujado (rejilla + elementos), reservas por fiesta, paquetes de botellas, venta en taquilla, pedidos de mesa, cobro y cierre de cuenta ("Liberar mesa").
- TPV de barra y taquilla: cajas con fondo y arqueo, combinados con receta en ml, anulaciones y descuentos autorizados.
- Inventario de discoteca: proveedores, entradas con albaran y coste, traspasos almacen -> barra, recuento de cierre, control de merma, pedido sugerido y envases retornables.
- Promotores (RR. PP.) con comision y codigos promocionales.
- CRM de clientes, consentimientos y auditoria de acciones.
- Panel de indicadores, analitica, prevision de asistencia y alertas.
- Correo transaccional con plantillas HTML (entradas, reservas, recuperacion de contrasena...) e imagenes en Cloudinary.

**Seguridad**
- JWT de acceso + refresh token en cookie HttpOnly (SameSite=Strict), bloqueo temporal tras intentos fallidos y rotacion de claves.
- Aislamiento multiempresa en todas las consultas (un recurso de otra empresa devuelve 404).
- No se guardan datos de tarjeta; del documento de identidad solo se guarda el tipo y los 4 ultimos caracteres.

## Tecnologias

| Capa | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 4.1 (Web MVC, Security, OAuth2 Resource Server/JWT, Data JPA, Validation, Mail, Actuator), Flyway, springdoc-openapi, Lombok |
| Base de datos | PostgreSQL 17 (en local, PostgreSQL embebido: no hace falta instalarlo) |
| Frontend | Angular 21 (componentes standalone, signals), Tailwind CSS 4, TypeScript 5.9 |
| Pruebas | JUnit 5 + PostgreSQL embebido + GreenMail (backend), Vitest (frontend) |
| Servicios externos | Stripe (modo test), Cloudinary (imagenes), cualquier servidor SMTP (Gmail, Brevo...) |
| Despliegue | Docker (backend), Vercel (frontend), Render + Neon (backend y base de datos) |

## Estructura

```
BLOOP_V2/
├── backend/                 API REST Spring Boot (monolito modular)
│   ├── src/main/java/com/bloop/
│   │   ├── authentication/  login, JWT, registro, recuperacion de contrasena
│   │   ├── tenants/ venues/ users/
│   │   ├── events/ orders/ payments/ ticketing/ accesscontrol/
│   │   ├── reservations/ vipservice/        reservados VIP y servicio de mesa
│   │   ├── pos/ inventory/                  TPV e inventario
│   │   ├── promoters/ customers/ analytics/ dashboard/ audit/ notifications/ media/
│   │   ├── demo/                            datos de demostracion (solo perfil dev)
│   │   └── shared/                          configuracion, seguridad, errores
│   ├── src/main/resources/db/migration/    esquema con Flyway (V1...V17)
│   └── Dockerfile
├── frontend/                Angular 21
│   ├── src/app/public/      web publica para clientes
│   ├── src/app/features/    backoffice del personal
│   └── vercel.json          despliegue en Vercel
├── render.yaml              despliegue del backend en Render
├── docker-compose.yml       PostgreSQL + Mailpit en local (opcional)
├── start-dev.bat / stop-dev.bat / run-tests.bat   atajos para Windows
└── .env.example             plantilla de variables de entorno (sin secretos)
```

Cada modulo del backend sigue la misma estructura: `controller/`, `dto/`, `entity/`, `repository/`, `service/` y `mapper/`.

## Requisitos

- **JDK 21** o superior (Maven no hace falta: se usa el wrapper `mvnw`).
- **Node.js 22** (probado con 22.14) y npm.
- **Docker** solo si quieres PostgreSQL y Mailpit en contenedores; no es obligatorio.

## Instalacion

```bash
git clone https://github.com/<tu-usuario>/BLOOP_V2.git
cd BLOOP_V2
cp .env.example .env          # en Windows: copy .env.example .env
cd frontend && npm install
```

El `.env` de ejemplo ya funciona en local: correo en modo pruebas y datos de demostracion activados.

## Ejecucion en local

**Opcion rapida en Windows:** `start-dev.bat` abre el backend y el frontend en dos ventanas y `stop-dev.bat` lo para todo.

**Backend** (con PostgreSQL embebido, sin Docker):

```bash
cd backend
./mvnw spring-boot:test-run      # Windows: mvnw.cmd spring-boot:test-run
```

- API en `http://localhost:8080` y documentacion Swagger en `http://localhost:8080/swagger-ui.html`.
- La base de datos se guarda en `backend/.local-db` (puerto 5433). La primera vez se crean los datos de demostracion.
- Para pararlo de forma limpia en Windows usa `stop-dev.bat` (detiene tambien PostgreSQL).

**Backend con Docker** (alternativa): `docker compose up -d` levanta PostgreSQL en el puerto 5432 y Mailpit
(los correos se ven en `http://localhost:8025`); despues `./mvnw spring-boot:run` desde `backend/`.

**Frontend:**

```bash
cd frontend
npm start                         # http://localhost:4200 (redirige /api al backend)
```

**Datos de demostracion (solo en local):** la discoteca *Kembo* (Zaragoza) "se que no es así pero por si acaso no pongo el mismo nombre jajaja" con dos salas, 20 reservados,
tres meses de actividad y el mes siguiente con preventa. Todas las cuentas usan la contrasena de `DEMO_PASSWORD`
(por defecto `Demo1234!`):

| Cuenta | Rol |
|---|---|
| `admin@kembo.test` | Administrador de la empresa |
| `encargado@kembo.test` | Encargado |
| `caja@kembo.test` | TPV de barra |
| `puerta@kembo.test` | Control de acceso |
| `servicio@kembo.test` | Camarero VIP |
| `rrpp@kembo.test` | Relaciones publicas |
| `cliente@kembo.test` | Cliente de la web |
| `superadmin@bloop.test` | Administrador de la plataforma |

**Pruebas:**

```bash
cd backend && ./mvnw test                     # 93 tests con PostgreSQL embebido
cd frontend && npx ng build && npm test       # build de produccion + 37 tests
```

## Variables de entorno

En local se leen del fichero `.env` (plantilla en `.env.example`). En produccion se configuran en el panel
del proveedor (Render/Vercel); **nunca se suben al repositorio**.

| Variable | Para que sirve | Local | Produccion |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Perfil de Spring (`dev` o `prod`) | `dev` (por defecto) | `prod` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexion a PostgreSQL | no hace falta con el embebido | obligatorias |
| `JWT_SECRET`, `QR_SECRET` | Firma de sesiones y QR (minimo 32 caracteres) | las de `.env.example` | obligatorias, aleatorias |
| `PUBLIC_URL` | URL del frontend (enlaces de los correos, vuelta de Stripe) | `http://localhost:4200` | URL de Vercel |
| `CORS_ALLOWED_ORIGINS` | Origenes permitidos | `http://localhost:4200` | URL de Vercel |
| `MAIL_ENABLED` | `false` = modo pruebas (los correos se guardan y se escriben en el log) | `false` | `true` |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_STARTTLS_ENABLE`, `MAIL_FROM`, `MAIL_FROM_NAME` | Servidor SMTP. Con Gmail: `smtp.gmail.com:587` y una *contrasena de aplicacion* | opcional | obligatorias |
| `MEDIA_PROVIDER` | `local` (disco) o `cloudinary` | `local` | `cloudinary` |
| `MEDIA_CLOUDINARY_CLOUD_NAME`, `MEDIA_CLOUDINARY_API_KEY`, `MEDIA_CLOUDINARY_API_SECRET` | Cuenta de Cloudinary | opcional | obligatorias |
| `STRIPE_ENABLED`, `STRIPE_SECRET_KEY`, `STRIPE_WEBHOOK_SECRET` | Pago con Stripe en modo test (solo claves `sk_test_`) | opcional | opcional |
| `DEMO_DATA`, `DEMO_PASSWORD`, `DEMO_DATASET` | Datos de demostracion (solo perfil `dev`) | activados | no se usan |

## Despliegue

Vercel sirve muy bien el frontend Angular (ficheros estaticos), pero **no ejecuta un servidor Java permanente**,
asi que el backend va en un servicio compatible con Docker. La arquitectura propuesta, con planes gratuitos:

```
Navegador ──> Vercel (Angular)
                 │  /api/*  (reenvio en vercel.json: mismo origen, la cookie de sesion funciona)
                 ▼
              Render (Spring Boot en Docker) ──> Neon (PostgreSQL)
                 ├──> Cloudinary (imagenes)
                 ├──> SMTP (correo)
                 └──> Stripe (pagos en modo test, opcional)
```

**1. Base de datos en Neon** (neon.tech, plan gratuito; probado con PostgreSQL 18)
- El proyecto ya esta enlazado en `.neon` y `neon.ts` (CLI de Neon: `npm i -g neon`). Con `neon login` y
  `neon env pull` se descargan las variables a `.env.local` (ignorado por Git): `DATABASE_URL` (pooler)
  y `DATABASE_URL_UNPOOLED` (directa).
- Usa la conexion **directa** (`DATABASE_URL_UNPOOLED`, host sin `-pooler`): el backend ya tiene su propio
  pool (HikariCP) y Flyway migra al arrancar, algo que el pooler de Neon (PgBouncer) no admite bien.
- Conviertela a las tres variables del backend: de `postgresql://USUARIO:CLAVE@HOST/neondb?...` salen
  `DB_URL=jdbc:postgresql://HOST/neondb?sslmode=require`, `DB_USERNAME=USUARIO` y `DB_PASSWORD=CLAVE`.
- Flyway crea todas las tablas al primer arranque (incluida la extension `btree_gist`).
- El backend de Render va en la misma region que la base de datos (`ohio` en `render.yaml`, porque Neon esta en `us-east-2`).

**2. Backend en Render** (render.com, plan gratuito)
- *New > Blueprint* y elige este repositorio: `render.yaml` crea el servicio `bloop-backend` con el `Dockerfile`.
- Rellena en el panel las variables marcadas como `sync: false` (base de datos, correo, Cloudinary, URLs).
  `JWT_SECRET` y `QR_SECRET` las genera Render automaticamente.
- Comprueba que responde en `https://bloop-backend.onrender.com/actuator/health` (si Render te asigna otro nombre, usalo en el paso 3).

**3. Frontend en Vercel** (vercel.com, plan Hobby gratuito)
- *Add New > Project*, importa el repositorio y pon **Root Directory = `frontend`**. El resto lo toma de `frontend/vercel.json`.
- Si la URL de tu backend no es `https://bloop-backend.onrender.com`, cambiala en `frontend/vercel.json`.
- Al terminar, copia la URL de Vercel en `PUBLIC_URL` y `CORS_ALLOWED_ORIGINS` del backend y vuelve a desplegarlo.

**Servicios externos necesarios**

| Servicio | Para que | Obligatorio |
|---|---|---|
| Neon (o cualquier PostgreSQL 17 o superior) | Base de datos | Si |
| Cloudinary | Carteles e imagenes (el disco de Render se borra en cada despliegue) | Si, para subir imagenes |
| SMTP (Gmail con contrasena de aplicacion, Brevo, Resend...) | Correos de entradas, reservas y contrasenas | Si, para enviar correo |
| Stripe (modo test) | Pago online de entradas | Opcional |

**A tener en cuenta en los planes gratuitos**
- El backend de Render "se duerme" tras unos minutos sin uso: la primera peticion puede tardar alrededor de un minuto.
- Algunos planes gratuitos bloquean la salida por los puertos SMTP. Si el correo falla en produccion
  (tiempo de espera agotado), usa un proveedor que admita otro puerto (por ejemplo Brevo en el 2525).
- En produccion (`prod`) **no se crean datos de demostracion** y el pago simulado esta desactivado:
  la plataforma arranca vacia y el pago online requiere claves de Stripe en modo test.
- El webhook de Stripe debe apuntar directamente al backend: `https://<backend>/api/payments/stripe/webhook`.

## Estado del despliegue

Registro de lo que ya esta montado (noviembre de 2026). Las claves y contrasenas **no estan en este repositorio**:
viven en el panel de cada servicio y, en local, en `.env` / `.env.local` (ignorados por Git).

| Pieza | Servicio | Estado | Detalles |
|---|---|---|---|
| Base de datos | **Neon** (plan gratuito) | Hecho y probado | Proyecto enlazado con `neon init` (`neon.ts`), rama `production`, region `us-east-2` (Ohio), PostgreSQL 18. Flyway ya ha aplicado las 17 migraciones. Probado en modo `prod`: arranque, lectura, registro, login y borrado de cuenta. |
| Imagenes | **Cloudinary** (plan gratuito) | Hecho y probado | Carpeta `bloop`. Probado desde el backend: subida, descarga desde `res.cloudinary.com` y borrado. |
| Backend | **Render** (plan Free) | En despliegue | Blueprint `Bloop` creado desde `render.yaml` con el servicio `bloop-backend` (Docker, region Ohio). URL prevista: `https://bloop-backend.onrender.com`. |
| Frontend | **Vercel** (plan Hobby) | Pendiente | Se importa el repositorio con *Root Directory* = `frontend`. Despues se actualizan `PUBLIC_URL` y `CORS_ALLOWED_ORIGINS` en Render con la URL definitiva. |
| Correo | **Gmail** (SMTP con contrasena de aplicacion) | Configurado | `smtp.gmail.com:587` con STARTTLS. Pendiente de comprobar que Render no bloquea el puerto SMTP. |
| Pagos | Stripe (modo test) | Desactivado | Sin claves: la web funciona, pero sin pago online. |

**Variables rellenadas en Render** (solo los nombres; los valores estan en el panel de Render):
`DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (conexion directa de Neon), `PUBLIC_URL`, `CORS_ALLOWED_ORIGINS`,
`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`,
`MEDIA_CLOUDINARY_CLOUD_NAME`, `MEDIA_CLOUDINARY_API_KEY`, `MEDIA_CLOUDINARY_API_SECRET`.
El resto (`SPRING_PROFILES_ACTIVE=prod`, puerto, memoria de la JVM, pool de conexiones, `JWT_SECRET`, `QR_SECRET`...)
lo pone `render.yaml`; los dos secretos los genera Render.

**Pasos que quedan**
1. Esperar a que Render termine el primer despliegue y comprobar `https://bloop-backend.onrender.com/actuator/health` (`UP`).
2. Desplegar el frontend en Vercel. Si la URL del backend es otra, cambiarla en `frontend/vercel.json`.
3. Poner la URL de Vercel en `PUBLIC_URL` y `CORS_ALLOWED_ORIGINS` de Render.
4. Crear el primer administrador de la plataforma (en produccion no hay datos de demostracion) y probar un correo real.

