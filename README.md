# F1 Race Engineer - Real-Time Ingestion & Analytics Dashboard

![Java](https://img.shields.io/badge/Java-21-orange?style=flat-square&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.4.1-green?style=flat-square&logo=spring)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?style=flat-square&logo=postgresql)
![Gemini AI](https://img.shields.io/badge/Gemini-2.0%20Flash-blueviolet?style=flat-square&logo=google)

A high-performance, real-time telemetry processing application and interactive secondary-screen dashboard designed for Codemasters' F1 games (F1 23/24/25). The system listens for high-frequency UDP game streams, decodes C-struct binary buffers with ultra-low latency, evaluates metric thresholds, and streams live telemetry data to a modern Single Page Application (SPA) dashboard. 

Additionally, it integrates Google's **Gemini 2.0 Flash API** acting as a dynamic, voice-enabled **AI Race Engineer** that performs corner-by-corner physics analysis, triggers live TTS alerts, compiles post-session debriefs, and intercepts OS-level hotkeys for fullscreen voice Q&A.

---

## 👤 Author

**Jashwanth Reddy**

* **GitHub**: [@mrjashwanthreddy](https://github.com/mrjashwanthreddy)
* **LinkedIn**: [@jashwanth-java-developer](https://www.linkedin.com/in/jashwanth-java-developer/)
* **Instagram**: [@mrjashwanthreddy](https://www.instagram.com/mr.jashwanthreddy/)

---

## 📦 Quick Start (Standalone Release)

No developer setup is required! You do not need to install Java, Maven, Docker, or PostgreSQL to use the dashboard:

1. Head to the **Releases** page of this GitHub repository.
2. Download the latest **`F1Telemetry-Windows.zip`** release archive.
3. Extract the ZIP folder to a local directory on your PC.
4. Double-click **`F1Telemetry.exe`** to start the application (a console window will display the Spring Boot logs).
5. Open your web browser and go to `http://localhost:8080` to access the Live Race Engineer Dashboard!

*Note: The standalone release is pre-configured with a secure cloud-hosted database fallback, allowing 100% plug-and-play execution.*

---

## 🚀 Key Features

* **High-Speed UDP Ingestion (Netty)**: Utilizes Netty asynchronous event loops to listen on UDP port `20777` and decode binary C-struct packets in real-time at 60Hz.
* **Low Allocation / Zero GC Pause**: Highly optimized decoding paths using direct buffer offsets to eliminate short-lived objects, avoiding GC-induced latency spikes in high-frequency streams.
* **Live Standings & Track Progress Timeline**: Replaces basic waveform graphs with a live racing HUD:
  * **Track Timeline Map:** A horizontal track visualization showing the relative spacing of all 22 cars in real-time. Highlights the player's position with a glowing pulse animation.
  * **Race Standings Table:** Sleek, high-density table displaying position, driver number, lap, speed, visual tyre compound (color-coded badges), average tyre wear, and real-time front left/right wing damage.
* **AI Race Engineer & Corner Analysis**:
  * **Live TTS Radio Alerts:** Real-time verbal notifications (e.g. tire overheating warnings) delivered via Web Speech API in a professional British voice.
  * **Fullscreen Voice Interceptor:** Intercepts global hotkeys (`Scroll Lock` by default) at the OS level during fullscreen gameplay to trigger voice queries and chats with your engineer.
  * **Lap & Session Debriefs:** Generates corner-by-corner apex speed evaluations, consistency ratings, tyre degradation reviews, and lap delta analysis.
* **Interactive Chart.js Post-Race Analytics**: Visualizes historical lap data with multi-axis graphs comparing Speed, RPM, Throttle, and Brake traces.
* **Zoom-Proof & Responsive Layout**: Responsive media queries and auto-shrink height boundaries adapt to any browser zoom settings or secondary screen sizes. If the window height is constrained, columns stack vertically and scroll internally without clipping.
* **Secure Key Injection CI/CD Pipeline:** The GitHub Actions release workflow automatically bakes in your Action secrets (`GEMINI_KEY`) at packaging compile-time, keeping keys hidden from the public repository history.
* **Seamless Auto-Updater ("Check for Updates")**: Integrated an automated desktop updater. It queries the GitHub Releases API on startup, displays interactive release notes, downloads, extracts, and restarts the application automatically in under 0.5s by skipping static JRE runtime files.
* **Configurable & Hot-Swappable UDP Ports**: Users can customize the UDP host address and port directly from the settings panel. Changes are saved to PostgreSQL and applied on-the-fly without requiring a restart.
* **Modern Premium Settings HUD**: Replaced plain settings grids with a high-density panel featuring real-time connection status logs, customizable voice engineer hotkey bindings, and a custom gradient-orange action button matching the race HUD.
* **Clean App-Image Packaging & Icon**: Standalone Windows executables (`F1Telemetry.exe`) are bundled with the custom F1 icon and run in windowed-background mode, hiding the terminal background window.

---

## 🛠️ Prerequisites (For Developers)

* **Java 21 (LTS)**
* **Maven** (configured via wrapper)
* **Docker Desktop** (for local PostgreSQL instance)
* **Codemasters F1 Game** (or use the mock UDP sender script)

---

## ⚙️ Installation & Setup (For Developers)

1. **Clone the repository**
   ```bash
   git clone https://github.com/mrjashwanthreddy/f1-telemetry.git
   cd f1-telemetry
   ```

2. **Start the local database container**
   Boot up the PostgreSQL instance using Docker Compose:
   ```bash
   cd f1-telemetry-app
   docker-compose up -d
   ```

3. **Verify Configuration**
   Check local preferences in `f1-telemetry-app/src/main/resources/application.properties`. Ensure JDBC configurations match your database profile (do not commit production credentials to source control):
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/f1telemetry
   spring.datasource.username=your_username
   spring.datasource.password=your_password
   spring.datasource.driver-class-name=org.postgresql.Driver
   ```

4. **Environment Variables**
   Create a `.env` file in the project directory for local Gemini AI features:
   ```env
   GEMINI_KEY=your_gemini_api_key_here
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
Build and bundle the custom minimal JRE and standalone Windows executable (`F1Telemetry.exe`) using the packaging pipeline:
```bash
package-app.bat
```
The standalone distribution will be generated in `target\dist\F1Telemetry`.
