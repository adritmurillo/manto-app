# Manual de Instalacion - Manto (Backend + Android + PostgreSQL)

Fecha: 20 de mayo de 2026

## 1) Versiones requeridas

- Java/JDK: 21 (LTS)
- Backend: Spring Boot 4.0.5 (Maven Wrapper incluido)
- Base de datos: PostgreSQL 16
- Android Gradle Plugin: 9.0.0
- Gradle Wrapper Android: 9.3.1
- Android SDK:
  - `compileSdk = 36`
  - `targetSdk = 36`
  - `minSdk = 24`

Nota: En Android el proyecto compila con Java source/target 11, pero la ejecucion de Gradle esta configurada con JDK 21 en `gradle.properties`.

---

## 2) Manual Backend (`D:\integrador2\backend\manto-backend`)

### 2.1 Prerrequisitos

1. Instalar JDK 21.
2. Verificar:

```powershell
java --version
```

Debe mostrar `21.x`.

### 2.2 Configuracion de base de datos

El backend usa `application.yml` con:

- URL: `jdbc:postgresql://localhost:5432/guardian_db`
- usuario: `postgres`
- password: `12345678`

Pero `docker-compose.yml` crea:

- DB: `guardian_db`
- usuario: `guardian_user`
- password: `guardian_pass`

Debes alinear uno de estos dos enfoques:

- Opcion A (recomendada): usar Docker y cambiar `application.yml` a `guardian_user/guardian_pass`.
- Opcion B: mantener `application.yml` y crear usuario/password `postgres/12345678` en tu Postgres local.

### 2.3 Levantar PostgreSQL con Docker (si usas Docker)

```powershell
cd D:\integrador2\backend\manto-backend
docker compose up -d
```

Verificar:

```powershell
docker ps
docker logs guardian-postgres --tail 50
```

### 2.4 Ejecutar backend

```powershell
cd D:\integrador2\backend\manto-backend
.\mvnw.cmd spring-boot:run
```

Backend esperado en:

- `http://localhost:8080`

Swagger esperado en:

- `http://localhost:8080/swagger-ui/index.html`

### 2.5 Variables/secretos importantes

Revisar `src/main/resources/application.yml`:

- `firebase.enabled`
- `firebase.service-account-path`
- `google.safe-browsing.api-key`
- `google.drive.enabled`

Si usas archivos de servicio, valida que existan en la carpeta `secrets/` del backend.

---

## 3) Manual PostgreSQL

## 3.1 Instalacion local (sin Docker)

1. Instalar PostgreSQL 16.
2. Crear base y usuario segun tu configuracion backend.

Ejemplo SQL:

```sql
CREATE DATABASE guardian_db;
CREATE USER guardian_user WITH PASSWORD 'guardian_pass';
GRANT ALL PRIVILEGES ON DATABASE guardian_db TO guardian_user;
```

Si usaras `postgres/12345678`, ajusta el `application.yml` a esos valores o crea ese usuario/password.

### 3.2 Verificacion rapida

```powershell
psql -h localhost -p 5432 -U guardian_user -d guardian_db
```

Si conecta, la DB esta lista.

### 3.3 Inicializacion de esquema

El proyecto tiene `src/main/resources/db/schema.sql` y JPA `ddl-auto: update`.

Al iniciar backend por primera vez, se crean/actualizan tablas automaticamente.

---

## 4) Manual Android Studio (`D:\integrador2\MOBILE`)

### 4.1 Prerrequisitos

1. Android Studio instalado (version reciente compatible con AGP 9.0.0).
2. JDK 21 instalado.
3. SDK Android 36 instalado (Platform + Build Tools).
4. Emulador o dispositivo fisico.

### 4.2 Abrir proyecto

1. Abrir Android Studio.
2. `Open` -> `D:\integrador2\MOBILE`.
3. Esperar sincronizacion Gradle.

### 4.3 Configuracion de Java/Gradle

Archivo actual:

- `gradle.properties` -> `org.gradle.java.home=C:/Program Files/Java/jdk-21`

Si aparece el error:

- `Value ... given for org.gradle.java.home ... is invalid`

Corregir ruta exacta del JDK, por ejemplo:

```properties
org.gradle.java.home=C:/Program Files/Java/jdk-21
```

(Usar slash `/`, no quitar espacios de `Program Files`).

### 4.4 Firebase Android

El proyecto ya contiene:

- `app/google-services.json`

Validar que coincida con el package:

- `com.guardianapp.mobile`

### 4.5 URL del backend para la app

Editar:

- `app/src/main/java/com/guardianapp/mobile/data/api/RetrofitClient.java`

`BASE_URL` debe:

1. Terminar en `/`
2. No incluir endpoint, solo base host

Correcto:

```java
private static final String BASE_URL = "https://TU-NGROK.ngrok-free.app/";
```

Incorrecto (causa `baseUrl must end in /`):

- `http://localhost:8080/api/v1/threats/analyze`

### 4.6 Ejecutar app

Desde Android Studio: `Run app`.

Desde terminal:

```powershell
cd D:\integrador2\MOBILE
.\gradlew.bat :app:assembleDebug
```

APK generado en `app/build/outputs/apk/debug/`.

---

## 5) Conexion Android <-> Backend con ngrok

### 5.1 Levantar backend local

```powershell
cd D:\integrador2\backend\manto-backend
.\mvnw.cmd spring-boot:run
```

### 5.2 Exponer backend con ngrok

```powershell
ngrok http 8080
```

Debes ver algo como:

- `Forwarding https://xxxx.ngrok-free.app -> http://localhost:8080`

Usa esa URL en `RetrofitClient.BASE_URL`.

### 5.3 Errores comunes

- `502 Bad Gateway` en ngrok:
  - ngrok apunta a puerto incorrecto (ej. 8000 en lugar de 8080)
  - backend apagado

- `404` en registro/login:
  - endpoint incorrecto en app
  - URL base mala (path extra)

- `Network error` en Android:
  - URL ngrok expirada
  - backend no disponible
  - internet/permisos dispositivo

---

## 6) Pruebas minimas recomendadas

### 6.1 Backend

1. Abrir Swagger.
2. Probar endpoint de salud/logica basica (users, threats, blacklist).
3. Confirmar inserciones en PostgreSQL.

### 6.2 Android

1. Login/registro.
2. Flujo de host/protegido.
3. Escudo de enlaces (`/api/v1/threats/url/analyze`).
4. Lista negra (`/api/v1/blacklist/urls`).

---

## 7) Checklist final

- [ ] Java 21 activo
- [ ] PostgreSQL arriba y credenciales alineadas
- [ ] Backend en `localhost:8080`
- [ ] ngrok apuntando a `8080`
- [ ] `BASE_URL` Android con `https://.../` y slash final
- [ ] Gradle sincronizado sin error de JDK

