# Indoor backend + frontend

## Modules
| Module | Role |
|---|---|
| `contract` | KMP lib shared by both sides: domain model (`Mall/Floor/Store`, `Path2D`) and REST DTOs (`api/ApiDtos.kt`, `api/DtoMapper.kt`). |
| `backend` | JVM Ktor server (CIO). Owns mall data (`backend/src/data`) and route calculation (`routing/IndoorRoutingService`). |
| `shared` | Compose UI. Gets all indoor data over HTTP (`RemoteIndoorRoutingRepository`), draws Bézier curves itself. |
| `jvm-app`, `android-app`, `ios-app`, `wasm-app` | Frontends. |

## Database (PostgreSQL)
Galleries live in PostgreSQL; schema is managed by Flyway (`backend/resources/db/migration/V1__init.sql`), applied on startup.
Tables: `malls`, `mall_entry_points`, `floors`, `stores`, `store_entry_points`, `elevators`, `escalators`.
Floor/store shapes are stored as SVG path text (`M x y L x y Q cx cy x y C c1x c1y c2x c2y x y Z`, local mall coordinates, curves preserved). Entrance names live in `mall_entry_points.name` (V4).
On first start, if `malls` is empty, the two Kotlin-defined galleries (`MockGaleria*`) are inserted (`SEED_DB=false` disables). Data is read into memory once at startup, so restart the backend after editing the DB.

Env: `DB_URL` (default `jdbc:postgresql://localhost:5433/malls`), `DB_USER`, `DB_PASSWORD` (default `malls`).

## Run
0. Database: `docker compose up -d --wait` (Postgres 16 on host port 5433, to avoid clashing with a local 5432).
1. Backend (port 8080, override with `PORT`/`HOST`): `.\kotlin.bat run -m backend`
2. Frontend: `.\kotlin.bat run -m jvm-app` (default `BackendConfig.baseUrl = http://localhost:8080`).
   - Android emulator: set `BackendConfig.baseUrl = "http://10.0.2.2:8080"` before `Screen()`.
   - Real device: use the LAN IP of the backend machine.
   - Wasm: the backend sends CORS headers, but `PlatformHttp` for wasmJs is still a stub (returns null) — it must be implemented with `fetch` for the web app to get data.
3. Tests: `.\kotlin.bat test -m contract -m backend -m shared -p jvm` (backend tests start PostgreSQL via Testcontainers; Docker must be running).

## Flow
1. On start `MapState` calls `GET /malls`, then `GET /malls/{id}` for every mall (floor plans, stores) and `GET /malls/{id}/locations`.
2. User picks start/end -> `GET /malls/{id}/route?from=..&to=..`.
3. Backend answers with plain **waypoints per floor**; the client builds the smooth curve with `CatmullRomBezierSpline` and draws it.
4. Shapes (floor outline, stores) are sent as **polygons (point lists)**; curved facades are sampled into points on the server. Frontend closes them and may smooth them itself.

## REST API v1 (JSON, all GET)
Coordinates are in the mall's local system: x in `0..sizeX`, y in `0..sizeY`; `upperLeft`/`downRight` map it to lat/lon.

| Endpoint | Response |
|---|---|
| `/health` | `{"status":"ok"}` |
| `/api/v1/malls` | `MallSummaryDto[]` (id, name, size, geo corners, minFloor, floorNumbers) |
| `/api/v1/malls/{id}` | `MallDto`: `entryPoints[]`, `floors[]` each with `outline[]`, `stores[]` (`instanceId, shopId, name, category, description, outline[], entryPoints[]`), `elevators[]`, `escalators[]` (`direction` UP/DOWN) |
| `/api/v1/malls/{id}/locations` | `NavLocationDto[]` (`id, name, type STORE/EXIT, floorNumber, position, category`) |
| `/api/v1/malls/{id}/route?from=<locId>&to=<locId>` | `RouteDto`: `levels[]` = `{floorNumber, waypoints[], instructions[], distanceMeters}`, `totalDistanceMeters`, `estimatedTimeSeconds` |

Points: `{"x":1.0,"y":2.0}`. Errors: `{"error":"mall_not_found|location_not_found|bad_request|route_failed|internal_error","message":"..."}` with HTTP 404 / 400 / 422 / 500.

Example:
```
GET /api/v1/malls/1/route?from=exit_1_0&to=store_3000
-> {"mallId":1,"levels":[{"floorNumber":0,"waypoints":[{"x":..,"y":..},...],"instructions":[...],"distanceMeters":123.4}],...}
```

## Authorization (JWT)
Reading maps, locations and routes stays **public**. Only gallery management requires an `ADMIN` token.
Users are stored in the `users` table (migration V3); passwords are hashed with PBKDF2-HMAC-SHA256 + random salt.

Env: `JWT_SECRET` (>= 32 chars; if unset a random one is generated, so tokens die on restart), `JWT_TTL_SECONDS` (default 3600),
`ADMIN_USERNAME` + `ADMIN_PASSWORD` (creates the first ADMIN on startup if missing; password >= 8 chars).

| Endpoint | Auth | Body -> Response |
|---|---|---|
| `POST /api/v1/auth/register` | - | `{username,password}` -> 201 `TokenResponse` (role USER); 409 `username_taken` |
| `POST /api/v1/auth/login` | - | `{username,password}` -> `{token,expiresInSeconds,user}`; 401 `invalid_credentials` |
| `GET /api/v1/auth/me` | Bearer | -> `{id,username,role}` |
| `PUT /api/v1/admin/malls/{id}` | Bearer, ADMIN | `MallDto` (same shape as GET) -> 200/201; replaces the whole mall |
| `DELETE /api/v1/admin/malls/{id}` | Bearer, ADMIN | -> 204; 404 if missing |

Send `Authorization: Bearer <token>`. Missing/invalid token -> 401 `unauthorized`; wrong role -> 403 `forbidden`.
Usernames: 3-32 chars `[A-Za-z0-9_.-]`, case-insensitive. Admin changes take effect immediately (catalog is reloaded).
`MallDto` floors and stores carry both `outline` (polygon points) and `outlineSvg` (exact path with curves). GET returns both; on PUT `outlineSvg` wins when present, otherwise the polygon is stored (curves are lost only in that case). Invalid `outlineSvg` -> 400.

Example (PowerShell):
```
$env:ADMIN_USERNAME="admin"; $env:ADMIN_PASSWORD="change-me-123"; $env:JWT_SECRET="<32+ random chars>"
.\kotlin.bat run -m backend
$tok = (Invoke-RestMethod -Method Post http://localhost:8080/api/v1/auth/login -ContentType application/json -Body '{"username":"admin","password":"change-me-123"}').token
Invoke-RestMethod -Method Delete http://localhost:8080/api/v1/admin/malls/2 -Headers @{Authorization="Bearer $tok"}
```
## Контур ТЦ и админ-панель

**Контур ТЦ (`outline`).** У `MallDto` есть `outline` (точки) и `outlineSvg` (точный путь с кривыми; главнее точек).
Это общий силуэт здания: показывается, когда ТЦ не в фокусе или карта отдалена (детали этажей скрыты).
Если `outline` не задан, клиент рисует форму первого этажа (миграция V5 заполняет его из этажа с минимальным номером).

**Создание ТЦ.** `POST /api/v1/admin/malls` (роль ADMIN) — тело как у `MallDto`; `id` назначает сервер,
магазины с `instanceId <= 0` получают id тоже от сервера. Ответ `201 {"id": <новый id>}`.
`PUT /api/v1/admin/malls/{id}` обновляет, `DELETE` удаляет.

**Админ-панель (клиент).** Кнопка «🛠 Admin» слева. Шаги:
1. Войти админским аккаунтом (`ADMIN_USERNAME` / `ADMIN_PASSWORD` бэкенда).
2. «Новый ТЦ»: ввести название → «Draw outline on the map» → кликами обвести здание → Finish.
   Создаётся черновик с системой координат 1000×H по габаритам контура и одним этажом 0.
3. Инструменты: контур ТЦ, форма этажа, магазин (название/категория + полигон), вход, лифт, эскалаторы,
   удаление; добавление/удаление этажей. Изменения видны на карте сразу.
4. «Create»/«Save» отправляет POST/PUT; «Discard» перезагружает данные с сервера.

Ограничения: маршрутизация у новых ТЦ идёт по упрощённой вертикальной оси (центр здания), поэтому для сложных
форм маршруты будут грубыми; формы рисуются ломаными (кривые Безье в UI не редактируются, но сохраняются при PUT
существующих ТЦ, если не менять форму); лимита попыток входа нет.
