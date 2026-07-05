# Standalone Packaging & Custom JRE Bundling

To support local execution on a driver's gaming PC without requiring a globally pre-installed Java Runtime Environment (JRE) or Maven setup, the application supports packaging as a standalone Windows executable (`F1Telemetry.exe`).

---

## 1. Automated Packaging Script

The execution lifecycle is automated in the batch script:
- Path: [package-app.bat](file:///c:/jashwanth-sde/f1-telemetry/f1-telemetry-app/package-app.bat)

### Packaging Process Steps:
1. **Clean**: Deletes any pre-existing build directories in `target\custom-runtime`, `target\dist`, and `target\input`.
2. **JLink JRE Customization**: Compiles a highly stripped JRE containing only modules required by the Spring Boot & Netty core frameworks, minimizing output sizing.
3. **Stage Resources**: Creates `target\input` and copies the compiled spring Boot fat JAR (`f1-telemetry-0.0.1-SNAPSHOT.jar`) into it.
4. **JPackage Assembly**: Wraps the fat JAR and custom JRE using the `jpackage` tool to build a standalone directory containing `F1Telemetry.exe`.

---

## 2. JLink Minimal Modules List

To compile the runtime image, the following modules are added:
- `java.base`: Core standard runtime objects.
- `java.logging`: Logging frameworks support.
- `java.xml`: XML payload serializations.
- `java.naming`: JNDI configurations.
- `java.sql`: JDBC database connection layers.
- `java.transaction.xa`: Multi-threaded JDBC database transactions.
- `java.compiler`: Java compiler interface.
- `java.net.http`: HTTP requests.
- `java.scripting`: Scripting engines.
- `java.security.jgss` / `java.security.sasl`: Secure LDAP/Kerberos.
- `java.instrument`: Spring proxy generation.
- `java.desktop`: Screen resources.
- `java.management` / `jdk.management`: JVM monitoring.
- `jdk.unsupported`: Internal low-level access APIs required by Netty (e.g., Unsafe).
- `java.rmi`: Remote Method Invocation.

---

## 3. JPackage Configuration Parameters

The jpackage command runs with these parameters:
- `--type app-image`: Builds a direct directory structure (useful for standalone execution).
- `--dest target\dist`: Output destination folder.
- `--name F1Telemetry`: Name of the compiled executable file.
- `--main-jar f1-telemetry-0.0.1-SNAPSHOT.jar`: The executable fat archive.
- `--main-class org.springframework.boot.loader.launch.JarLauncher`: Delegates class loading to Spring's archive launcher to locate nested dependencies.
- `--runtime-image target\custom-runtime`: Links the minimal custom JRE.
- `--win-console`: Wraps the execution inside a standard cmd console window, allowing developers to view logs and telemetry counts in real-time.

---

## 4. Database Setup & Standalone Run

### Standalone Executable Run
When running from the packaged output (`target\dist\F1Telemetry\F1Telemetry.exe`), it expects a local PostgreSQL instance running. The configuration properties fallback automatically to default local parameters:
- **Host**: `localhost:5432`
- **DB Name**: `f1telemetry`
- **User/Pass**: `f1user` / `f1pass`

### Local Development Run
In development, running the application inside Maven (`mvn spring-boot:run` or IDE execution) uses Spring Boot's automatic Docker Compose support. If a `docker-compose.yml` is found, the system boots the Postgres container instance automatically and updates local configurations dynamically.
