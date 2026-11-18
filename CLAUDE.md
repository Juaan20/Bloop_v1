# CLAUDE.md - normas del proyecto Bloop (BLOOP_V2)

> **BLOOP_V2 es la copia publica e independiente de VENUEFLOW** (`C:\dev\VenueFlow`, proyecto principal).
> Sincronizacion SOLO manual y unidireccional VENUEFLOW -> BLOOP_V2, cuando el propietario lo pida
> expresamente ("Pasa los cambios de VENUEFLOW a BLOOP_V2"). Procedimiento completo en `README.md`,
> seccion "Sincronizacion con VENUEFLOW"; el ultimo commit sincronizado esta en `.sync/venueflow-sync.txt`.
> Nunca tocar VENUEFLOW desde aqui, nunca pasar cambios de BLOOP_V2 a VENUEFLOW y no hacer commit/push sin orden.

Guia para futuras sesiones de Claude Code. Leer antes de modificar el proyecto.

## Contexto

- SaaS multi-tenant para nightlife. Monolito modular Spring Boot 4.1 (Java 21) + Angular 21 + PostgreSQL 17.
- El propietario es un desarrollador junior (DAM) que quiere entender y mantener el codigo: priorizar claridad sobre ingenio. Sin microservicios, colas, CQRS ni abstracciones sin motivo.
- Nombre: la plataforma se llama **Bloop** (antes VenueFlow). Paquete Java `com.bloop`, propiedades `bloop.*`, BD local `bloop`. Se mantienen a proposito: la carpeta `C:\dev\VenueFlow`, el repositorio de GitHub antiguo, el prefijo `vf-` de componentes/utilidades CSS y las migraciones V1-V16 (V17 corrige el dato de V2).
- Idioma: codigo en ingles; comentarios, mensajes de error, textos de UI y documentacion en espanol.
- Estado: fases 1 a 6 implementadas (fase 6: correo SMTP configurable con plantillas HTML, imagenes en Cloudinary, carteles por dia, filtros de pedidos, salas con plano propio, TPV por pestanas con recetas en ml). Pantallas de las fases 2 y 3 pendientes de revisar a fondo en el navegador; Stripe sin probar con una cuenta real.

## Entorno de desarrollo (maquina del propietario)

- Windows 11, JDK 24, Node 22.14 (por eso Angular 21: Angular 22 exige Node >= 22.22.3), sin Docker ni WSL.
- Maven solo via `mvnw.cmd` (no hay Maven global). Las dependencias estan en la cache de `~/.m2` (`-o` funciona).
- Base de datos sin Docker: `mvnw spring-boot:test-run` arranca `LocalDevApplication` (src/test) con PostgreSQL embebido zonky en el puerto 5433, datos en `backend/.local-db`. Para detenerlo limpiamente usar `stop-dev.bat` (hace `pg_ctl stop`); matar solo el proceso java deja procesos postgres vivos.

## Reglas de arquitectura backend (obligatorias)

1. Cada modulo `com.bloop.<modulo>` tiene `controller/`, `dto/request/`, `dto/response/`, `entity/`, `repository/`, `service/`, `mapper/` (solo las capas necesarias, sin carpetas vacias).
2. Controladores: solo HTTP, `@Valid` y `@PreAuthorize("hasAuthority('PERMISO')")`. Nada de repositorios ni reglas.
3. Nunca devolver entidades JPA. DTOs como `record`. Mappers manuales (`@Component`), no MapStruct.
4. **Multi-tenant:** el tenant SIEMPRE viene de `CurrentUserProvider.requireTenantId()`. Toda consulta de negocio filtra por `tenantId` (`findByIdAndTenantId`). Recurso de otra empresa => 404.
5. Errores con `NotFoundException`, `BadRequestException`, `ConflictException`, `ForbiddenException`, `UnauthorizedException`; `GlobalExceptionHandler` los traduce a `ApiError`.
6. Esquema solo con Flyway (`ddl-auto: validate`). No editar migraciones ya aplicadas: crear `V<n+1>__descripcion.sql`.
7. Dinero `BigDecimal` / `NUMERIC(10,2)`. Fechas `Instant` / `TIMESTAMPTZ` en UTC. Ids UUID.
8. Concurrencia: stock con `UPDATE` condicional (`TicketTypeRepository.reserveStock`), QR con `markUsedIfValid`, pagos con `findByIdForUpdate`. Mantener estos patrones.
9. Auditoria con `AuditService.record(...)`; si el actor no pertenece a la empresa afectada (cliente pagando), `recordInTenant(...)`.
10. Permisos nuevos: migracion + `shared/security/Permissions.java` + `frontend/src/app/core/auth/permissions.ts`.
11. Reservas sin solapamientos con `EXCLUDE USING gist` (extension `btree_gist`, V7); usos de codigos promocionales con `PromoCodeRepository.reserveUse`; ficha CRM con `insertIfMissing` + `SELECT ... FOR UPDATE`.
12. TPV: existencias con `StockService`: las VENTAS descuentan sin bloquear (el stock teorico puede quedar en negativo hasta el recuento, como en hosteleria); salidas manuales y traspasos si exigen stock (UPDATE condicional); caja abierta bloqueada con `findOpenForUpdate`; ventas con Idempotency-Key por terminal. Quien trabaja en que local: `StaffVenueAccess`.
13. Reembolsos solo con `OrderRefundService` (bloquea el pedido, idempotente). Cancelar evento/sesion reembolsa via `CancellationRefundListener` (AFTER_COMMIT, una transaccion por pedido).
14. Stripe: el resultado del pago solo llega por webhook firmado (`StripeWebhookService`); nunca marcar pagos como confirmados desde la vuelta del navegador. Tests con `FakeStripeServer`, nunca contra Stripe real.
15. Intentos de login en memoria (`LoginAttemptService`); los tests de integracion los reinician en `AbstractIntegrationTest`.
16. VIP (modulo `vipservice`): pedidos y cobros de mesa bloquean la reserva (`findByIdForUpdate`) y son idempotentes por clave; el reparto paquete/adicional esta en `VipAccountCalculator`. `SEATED` exige pago previo y `COMPLETED` solo via cierre de cuenta.
17. Asistentes: del documento solo se guarda tipo + 4 ultimos (`AttendeeRules`); la edad minima se valida en `CheckoutService`. Nunca loguear ni devolver el documento.
18. TPV: `CategoryKind` decide que vende cada terminal (`allowedKinds`) y que sale en la carta VIP. Prevision de asistencia: logica pura en `AttendanceForecaster`, SQL en `ForecastRepository`.
19. Cada sala (`vip_zones`) tiene su forma en `vip_zones.layout` (rejilla pintada + elementos, ver `floor-layout.ts`); nada de planos fijos. Las reservas VIP se gestionan desde el plano (no hay pagina de reservas aparte) y son SIEMPRE por fiesta (`sessionId` obligatorio, nunca por horas); solo se reservan mesas colocadas en el plano (`VipTable.isOnPlan`). "Liberar mesa" = cerrar la cuenta (COMPLETED): la mesa queda libre en esa fiesta y el historial se conserva. Mesas en taquilla con `POST /api/reservations/walk-in` (reserva + cobro + SEATED en una transaccion). Terminal `VIP_SERVICE`: carga botellas a una mesa via `PosVipService`.

20. Correo: siempre `NotificationService.sendEmail(..., EmailMessage)`; el HTML lo pinta `EmailTemplateRenderer` (escapa todo). Variables `MAIL_*` (ver `README.md`). Modo pruebas = `MAIL_ENABLED=false` (estado LOGGED, nunca fingir SENT).
21. Imagenes: solo via `MediaService` (valida por contenido con `ImageValidator`) e `ImageStorage` (Cloudinary o local). La BD guarda la URL. Toda URL de imagen nueva pasa por `MediaUrlValidator.requireOwnUrl`.
22. Inventario: `stock_unit` UNIT o ML; lo que descuenta una venta lo decide `StockConsumption` (receta o la propia variante). Anular devuelve los movimientos SALE de la venta. Refresco de combinado = `products.mixer_surcharge` (no su precio suelto).
24. Separacion clientes/personal: los endpoints de cliente (`/api/me/tickets|orders|reservations|privacy`, `/api/checkout`) exigen `hasRole('CUSTOMER')` (los roles del JWT llegan como `ROLE_*`); en Angular `customerAreaGuard` saca al personal de la web publica.
25. TPV de barra: 4 categorias. Combinados = un producto por tipo de alcohol (Ron, Ginebra...; Cubata 9 / Con Red Bull 11) con receta de 50 ml de la botella de su tipo (categoria `SUPPLY`, en ml). Chupitos sin inventario. Inventario tipico de discoteca en `InventoryControlService`: proveedores, entradas con albaran y coste, traspasos almacen->barra, recuento de cierre, control de merma (`InventoryControlCalculator`), pedido sugerido y envases retornables (`container_returns`).
23. Modales: `<vf-modal>` (centrado, scroll interno, `.vf-modal-actions` fija abajo). Formularios dentro de la pagina: directiva `vfReveal`. Nada de `transform` en ancestros de elementos `fixed` (la animacion `fade-in` es solo opacidad).

## Datos de demostracion

- `DEMO_DATASET` elige el juego (por defecto `kembo`; el antiguo es `noctambula`).
- Kembo (Zaragoza): `KemboDemoSeeder` (orden 1: empresa, local, cuentas `*@kembo.test`, carta, almacen + barras Sala L/U, proveedores, Sala L y Sala U con 10 reservados cada una, 2 "Premium DJ" por sala) y `KemboHistoryGenerator` (orden 2: 3 meses pasados jue/vie/sab + tardeos y el mes siguiente con preventa; albaranes, traspasos, recuentos con merma y envases). Una sola vez, en una transaccion.
  Los nombres "producto|variante" de la carta deben ser unicos (el generador falla si se repiten).
- Noctambula (`DEMO_DATASET=noctambula`):
- `DemoDataSeeder` (orden 1): cuentas, empresas, locales, eventos, catalogo TPV. Solo perfil dev.
- `VipServiceDemoSeeder` (orden 3) y `BarCatalogDemoSeeder` (orden 4): carta VIP y carta de barra por tipo de bebida (botellas en ml, copas con receta, refrescos con suplemento).
- `HistoricalDemoDataGenerator` (orden 2): con `DEMO_HISTORY_DAYS>0` genera N dias de actividad con SQL directo y fechas pasadas.
  Si se anaden columnas NOT NULL a tablas que rellena (pedidos, entradas, TPV, reservas...), hay que actualizar sus INSERT.
  Verificar con una BD temporal (`-Dbloop.localdb.dir=... -Dbloop.localdb.port=5434`) antes de tocar la del propietario.


## Trampas conocidas (Spring Boot 4 / Hibernate 7 / Jackson 3)

- Sobre una entidad ya gestionada NO usar `save()` / `saveAndFlush()` para persistir hijos nuevos en cascada: `merge` crea copias y el objeto original queda sin id. Usar `repository.flush()`.
- Jackson 3 falla con primitivos ausentes; se desactivo `fail-on-null-for-primitives` en `application.yml`.
- Anotaciones de test MVC en `org.springframework.boot.webmvc.test.autoconfigure` (dependencia `spring-boot-starter-webmvc-test`).
- `@PreAuthorize` se evalua despues de deserializar el cuerpo: un body invalido da 400 antes que 403.
- En consultas JPQL con filtros opcionales de texto usar `:search = ''` (el servicio convierte null en "").

## Frontend

- Standalone components, signals, Reactive Forms, `loadComponent` por pagina, control flow `@if/@for`.
- Los componentes no usan `HttpClient`: cada modulo tiene su `*.service.ts`.
- Estilos con Tailwind 4 y utilidades propias `vf-*` definidas con `@utility` en `src/styles.css`.
- Sistema de diseno (oscuro premium): en `@theme` la escala `zinc` esta redefinida con neutros azul-negro, `brand` = violeta-indigo, `accent` = azul frio. Fuentes Inter (texto) y Geist (titulos) via Google Fonts en `index.html`.
  Utilidades: `vf-btn-primary|secondary|ghost|danger`, `vf-btn-sm`, `vf-btn-icon`, `vf-card`, `vf-panel`, `vf-input`, `vf-input-search`, `vf-badge`, `vf-skeleton`, `vf-kbd`, `.vf-table`.
- Iconos: `<vf-icon name="..." class="size-4" />` (`shared/ui/icon.ts`, SVG propios; nada de emojis). Logo: `<vf-logo />`. Paginacion: `<vf-pagination [page]="page" (pageChange)="..." />`. Carga: `<vf-spinner>` pinta esqueletos.
- Eventos sin imagen: cartel generativo `eventArt(nombre)` (`public/events/event-card.ts`), nunca fotos inventadas. Respetar `prefers-reduced-motion` (ya global en `styles.css`).
- Errores: `errorMessage(err)` de `core/services/api-error.ts`. Estados de carga, vacio y error en cada lista.
- Nada de botones decorativos ni datos inventados: todo conectado al backend.

## Verificacion antes de dar algo por terminado

```bat
cd backend && mvnw.cmd -o test          :: 93 tests (PostgreSQL embebido; la prueba de carga va aparte: -Dgroups=performance -Dsurefire.excludedGroups=ninguno)
cd frontend && npx ng build && npm test  :: build + 37 tests Vitest
```

No afirmar que algo funciona sin haberlo ejecutado; si algo no se puede probar (p. ej. Docker en esta maquina), decirlo.

## Seguridad

- No usar secretos reales; `.env` no se versiona; `.env.example` sin secretos.
- No guardar datos de tarjeta. El pago simulado solo en dev (`bloop.payments.simulated-enabled`).
