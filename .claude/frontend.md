# Frontend Single Page Application (SPA) Web Client

The front-end client operates as a dynamic, high-density Single Page Application (SPA) utilizing CSS variables for styling, SockJS/STOMP for WebSocket notifications, and Chart.js for data visualization.

---

## 1. Directory Structure

All frontend resources are located in the static classpath of the Spring Boot app:
- Path: [static/](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/resources/static)
  - `index.html`: The HTML layout defining tabs, gauges, and historical data tables.
  - [css/styles.css](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/resources/static/css/styles.css): Core stylesheet utilizing CSS variables for dark mode and structural animations.
  - [js/auth.js](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/resources/static/js/auth.js): Handles JWT credentials (saved in `localStorage`), registration redirects, and authenticating sessions.
  - [js/app.js](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/resources/static/js/app.js): Connects to the STOMP message channel and updates live dashboard components.
  - [js/history.js](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/resources/static/js/history.js): Manages fetching historical lap timings and displaying results in tabular lists.
  - [js/analytics.js](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/resources/static/js/analytics.js): Controls Chart.js initialization and streams telemetry traces.

---

## 2. Navigation Tabs (DOM Sections)

The SPA manages view changes by toggling custom hidden CSS classes (`.active-tab` / `.hidden`) on the following containers:
1. **Login/Register Tab**: Visible when no valid JWT token is saved in local storage.
2. **Dashboard Tab**: Displays gauges (Speed, RPM, Gear), progress indicators (Throttle/Brake), and tyre status alerts.
3. **Session History Tab**: Lists completed sessions and sector-by-sector lap records.
4. **Analytics Tab**: Displays the Chart.js grid mapping telemetry parameters (Speed, Throttle, Brake, RPM) sequentially over a complete lap.
5. **Raw Inspector Tab**: An automated scrolling console terminal showing raw JSON packets in real-time.

---

## 3. WebSocket Channel Subscriptions

WebSockets connect to the `/telemetry-websocket` handler fallback SockJS path and subscribe to:

### 1. `/topic/live-telemetry`
- **Data Payload**: [LiveSessionState](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/state/LiveSessionState.java) JSON.
- **Frequency**: ~30Hz.
- **Handling**: Updates RPM indicator, speed digits, gear text, throttle/brake progress fills, and tyre surface wear configurations.

### 2. `/topic/live-alerts`
- **Data Payload**: [AlertEvent](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/src/main/java/com/f1telemetry/engine/AlertEvent.java) JSON.
- **Frequency**: Event-driven.
- **Handling**: Triggers screen flash animations, displaying warning banners (e.g. Critical Tyre Wear or Damage notifications).

### 3. `/topic/raw-packets`
- **Data Payload**: Raw unpacked binary packet serialization string.
- **Frequency**: ~60Hz.
- **Handling**: Pushes lines directly to the Raw Inspector log container, maintaining clean text scrolling.

---

## 4. REST API Endpoint Catalog

All API endpoints are prefixed with `/api` and require a JWT header: `Authorization: Bearer <Token>` (excluding open security endpoints):

| Method | Endpoint | Description |
|---|---|---|
| **POST** | `/api/auth/register` | Create a new driver profile (No Auth header required). |
| **POST** | `/api/auth/login` | Authenticate driver and obtain JWT (No Auth header required). |
| **POST** | `/api/session/start` | Establishes active driver session context in the backend. |
| **GET** | `/api/history/sessions` | Fetch list of completed sessions linked to the active driver. |
| **GET** | `/api/history/sessions/{sessionId}/laps` | Fetch lap timing tables (S1, S2, S3, total time) for a session. |
| **GET** | `/api/history/sessions/{sessionId}/laps/{lapNumber}/telemetry` | Fetch telemetry records list to render the analytics charts. |
