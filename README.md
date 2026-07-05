# F1 Telemetry Processing System
![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-green?style=flat-square&logo=spring)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)

This Spring Boot application is a high-performance, real-time telemetry processing dashboard for F1 Codemasters games (F1 23/24/25). It ingests high-frequency UDP telemetry streams, processes them with ultra-low latency, and broadcasts updates to a unified web dashboard for live monitoring and post-race analytics.

---

## 👤 Author

**Jashwanth Reddy**

* **GitHub**: [@mrjashwanthreddy](https://github.com/mrjashwanthreddy)
* **LinkedIn**: [@jashwanth-java-developer](https://www.linkedin.com/in/jashwanth-java-developer/)
* **Instagram**: [@mrjashwanthreddy](https://www.instagram.com/mr.jashwanthreddy/)

---

## 🚀 Features

* **High-Speed UDP Ingestion**: Utilizes Netty asynchronous event loops to listen on UDP port `20777` and unpack incoming binary structs in real-time.
* **Low Allocation / Zero GC Pause**: Features efficient little-endian binary decoding to prevent GC-induced latency spikes in high-frequency streams (60Hz).
* **Live WebSockets Streaming**: Decouples UDP throughput by broadcasting live stats to a Web client at 30Hz using SockJS / STOMP.
* **Rule-Based Live Alerts**: Automatically evaluates metric thresholds (e.g. tire overheating, front wing damage) and pushes warning events to the driver.
* **Asynchronous Persistence & Downsampling**: Buffers telemetry metrics in a queue and downsamples them to 10Hz before batch-inserting into PostgreSQL to save disk space.
* **Single Page Application (SPA)**: Clean tabbed dashboard (Live Dashboard, Completed Lap History, Interactive Telemetry Analytics, and Raw Packet Debugger).
* **Interactive Chart.js Analytics**: Renders post-race graphs mapping Speed, RPM, Throttle, and Brake traces sequence over a completed lap.
* **GraalVM & Custom JRE Executables**: Bundled script using `jlink` and `jpackage` to package the Spring Boot app into a standalone Windows `F1Telemetry.exe`.

---

## 🛠️ Prerequisites

* **Java 21**
* **Maven** (configured via wrapper)
* **Docker Desktop**
* **Codemasters F1 Game** (or use the mock UDP python sender script)

---

## ⚙️ Installation & Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/mrjashwanthreddy/f1-telemetry.git
   cd f1-telemetry
   ```

2. **Start the database container**
   Boot up the local PostgreSQL database instance using Docker:
   ```bash
   cd f1-telemetry-app
   docker-compose up -d
   ```

3. **Verify Configuration**
   Check settings in `f1-telemetry-app/src/main/resources/application.properties`. Ensure JDBC datasource connections match your Postgres compose credentials:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/f1telemetry
   spring.datasource.username=f1user
   spring.datasource.password=f1pass
   ```

---

## 🏃‍♂️ Running the Application

### Launching in Development Mode
Execute the Spring Boot runtime using the Maven wrapper:
```bash
./mvnw spring-boot:run
```
The web dashboard will be available at `http://localhost:8080`.

### Simulating Game Telemetry
If you do not have the F1 game running, you can run the mock sender script to broadcast test telemetry packets:
```bash
python test_udp_sender.py realistic
```

### Packaging a Standalone Executable
Build and bundle a standalone Windows executable (`F1Telemetry.exe`) using the automated packaging pipeline:
```bash
package-app.bat
```
The standalone distribution will be generated in `target\dist\F1Telemetry`.
