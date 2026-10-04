# Kraków Map & Indoor Navigation

An interactive multiplatform map application for Kraków featuring vector/raster map rendering, multi-floor indoor routing for shopping malls (Galeria Krakowska, Galeria Kazimierz), outdoor pedestrian routing to mall entrances, step-free accessible navigation, and an interactive admin geometry editor.

Built with **Kotlin Multiplatform (KMP)** and **Compose Multiplatform**.

---

## Project Structure

| Module | Description |
|---|---|
| [`contract`](contract/) | Shared domain models (`Mall`, `Floor`, `Store`, `Path2D`) and REST API DTOs. |
| [`backend`](backend/) | JVM Ktor server (CIO) with PostgreSQL storage, indoor pathfinding, and OpenStreetMap pedestrian routing. |
| [`shared`](shared/) | Compose Multiplatform UI, gesture engine, layer rendering pipeline, and map state. |
| [`android-app`](android-app/) | Android mobile application with edge-to-edge support and GPS integration. |
| [`jvm-app`](jvm-app/) | Desktop Compose application (Linux, macOS, Windows). |
| [`wasm-app`](wasm-app/) | WebAssembly browser application. |
| [`ios-app`](ios-app/) | iOS application (Xcode / Compose Multiplatform). |

---

## Prerequisites

- **Java JDK**: JDK 17 or higher (e.g. Eclipse Adoptium OpenJDK 17).
- **Docker**: Docker & Docker Compose (for PostgreSQL 16).
- **Android SDK** *(optional, for Android)*: SDK 36, Android command-line tools / platform-tools (`adb`).
- **Xcode** *(optional, for iOS)*: macOS with Xcode installed.

---

## How to Run

### 1. Start the Database (PostgreSQL)

The backend uses PostgreSQL on port `5433` (to avoid conflicts with standard local Postgres installations).

```bash
docker compose up -d --wait
```

Flyway database migrations run automatically when the backend starts, seeding Galeria Krakowska and Galeria Kazimierz.

---

### 2. Start the Backend API Server

Run the Ktor server on `http://localhost:8080`:

**Linux / macOS:**
```bash
./gradlew :backend:run
```

**Windows:**
```powershell
.\gradlew.bat :backend:run
```

#### Optional Backend Environment Variables:
- `PORT`: Server port (default: `8080`).
- `HOST`: Server host (default: `0.0.0.0`).
- `DB_URL`: Postgres JDBC URL (default: `jdbc:postgresql://localhost:5433/malls`).
- `DB_USER` / `DB_PASSWORD`: Database credentials (default: `malls` / `malls`).
- `ADMIN_USERNAME` / `ADMIN_PASSWORD`: Default admin credentials for the mall editor (e.g. `admin` / `change-me-123`).
- `JWT_SECRET`: Secret key for JWT auth (>= 32 chars).

---

### 3. Run the Frontends

#### Desktop App (JVM)
Runs the desktop Compose UI window:

```bash
./gradlew :jvm-app:run
```

To specify a custom backend URL on launch:
```bash
BACKEND_URL="http://localhost:8080" ./gradlew :jvm-app:run
```

---

#### Android App

1. **Build debug APK:**
   ```bash
   ./gradlew :android-app:assembleDebug
   ```

2. **Install and run on a connected device / emulator:**
   ```bash
   ./gradlew :android-app:installDebug
   ```
   Or launch directly from Android Studio.

3. **Connecting Android to the Backend:**
   - **Android Emulator**: In the app, tap the **`⚙️ Host`** button on the bottom offline banner (or the **`🥞`** Layer Manager menu) and choose **`Emulator 10.0.2.2`** (`http://10.0.2.2:8080`).
   - **Physical Device**:
     - *Option A (USB Reverse Proxy)*: Run `adb reverse tcp:8080 tcp:8080`, and the phone can reach the backend at `http://localhost:8080`.
     - *Option B (Wi-Fi LAN)*: Ensure your phone and computer are on the same Wi-Fi, tap **`⚙️ Host`** in the app, and enter your computer's local IP (e.g. `http://192.168.1.100:8080`).

---

#### Web App (Wasm)
Runs the WebAssembly frontend in your default browser:

```bash
./gradlew :wasm-app:wasmJsBrowserDevelopmentRun
```

---

#### iOS App (macOS only)
Open `ios-app/module.xcodeproj` in Xcode, select a simulator or device target, and build/run.

---

## Outdoor Routing Graph (OpenStreetMap)

The backend provides pedestrian routing from Kraków streets directly to mall entrances using an offline OpenStreetMap walk graph (`backend/data/krakow-walk.graph.gz`).

To regenerate or build a fresh walk graph from the Małopolska OSM extract:

1. Download the OSM extract (~200 MB):
   ```bash
   mkdir -p build/osm
   curl -L -o build/osm/malopolskie-latest.osm.pbf https://download.geofabrik.de/europe/poland/malopolskie-latest.osm.pbf
   ```
2. Build the walking network graph:
   ```bash
   ./gradlew :backend:buildWalkGraph
   ```
   Output: `backend/data/krakow-walk.graph.gz`.

---

## Running Tests

Run unit and integration tests across all modules:

```bash
./gradlew test
```

Or run module-specific test suites:
```bash
./gradlew :backend:test
./gradlew :shared:testReleaseUnitTest
./gradlew :contract:testReleaseUnitTest
```
*(Note: Backend tests use Testcontainers and require Docker to be running).*

---

## Admin Panel & Geometry Editor

- Click the **`🛠 Admin`** button (left sidebar on desktop, or `🛠️` in the right-side control toolbar on mobile).
- Log in with the admin credentials configured in the backend (`ADMIN_USERNAME` / `ADMIN_PASSWORD`).
- Create and edit mall floor plans, store boundaries, entrances, escalators, and elevators directly on the live map.
