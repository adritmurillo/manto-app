# Manual de Instalacion y Configuracion - Manto

Este manual cubre **3 frentes**:
1. Backend (`D:\integrador2\backend\manto-backend`)
2. App Android (`D:\integrador2\MOBILE`)
3. Base de datos PostgreSQL

---

## 1) Versiones Requeridas

### Backend
- Java / JDK: **21**
- Maven: usar **Maven Wrapper** del proyecto (`mvnw`, `mvnw.cmd`)
- Spring Boot: **4.0.5** (definido en `pom.xml`)
- Puerto API: **8080**

### Android
- Android Studio: version reciente compatible con **AGP 9.0.0**
- Gradle Wrapper: **9.3.1**
- JDK para Gradle: **21**
- compileSdk: **36**
- targetSdk: **36**
- minSdk: **24**

### PostgreSQL
- Recomendado: **PostgreSQL 16** (en Docker Compose se usa `postgres:16-alpine`)
- Puerto DB: **5432**

---

## 2) Manual PostgreSQL (Paso a paso)

## Opcion A (Recomendada): Docker

1. Abrir terminal en:
   `D:\integrador2\backend\manto-backend`

2. Levantar PostgreSQL:
```powershell
docker compose up -d
```

3. Verificar contenedor:
```powershell
docker ps
```
Debe aparecer `guardian-postgres`.

4. Credenciales que crea Docker Compose:
- DB: `guardian_db`
- Usuario: `guardian_user`
- Password: `guardian_pass`

## Opcion B: Instalacion local de PostgreSQL

1. Instalar PostgreSQL 16.
2. Crear base de datos y usuario segun tu estrategia.

Ejemplo SQL:
```sql
CREATE DATABASE guardian_db;
CREATE USER guardian_user WITH PASSWORD 'guardian_pass';
GRANT ALL PRIVILEGES ON DATABASE guardian_db TO guardian_user;
```

## Punto critico de configuracion (muy importante)

Tu `application.yml` actual del backend tiene:
- `username: postgres`
- `password: 12345678`

Pero `docker-compose.yml` crea:
- `guardian_user / guardian_pass`

Debes alinear ambos lados. Tienes 2 caminos:

1. **Editar `application.yml`** para usar `guardian_user/guardian_pass`.
2. Mantener `application.yml` como esta y crear ese usuario/password en tu PostgreSQL local.

Si no alineas esto, fallara la conexion a DB.

---

## 3) Manual Backend (Paso a paso)

## 3.1 Requisitos previos
- Tener JDK 21 instalado.
- Tener Docker Desktop (si usaras DB en contenedor).

Verificaciones:
```powershell
java --version
```
Debe mostrar Java 21.

## 3.2 Ubicarse en backend
```powershell
cd D:\integrador2\backend\manto-backend
```

## 3.3 Configurar secretos / integraciones

Archivo principal:
- `src/main/resources/application.yml`

Valores importantes:
- `server.port: 8080`
- `spring.datasource.*`
- `firebase.service-account-path`
- `google.safe-browsing.api-key`

Recomendacion de buenas practicas:
- No dejar API keys reales hardcodeadas.
- Mover API keys a variables de entorno.

Variables utiles:
- `FIREBASE_SERVICE_ACCOUNT_PATH`
- `GOOGLE_SAFE_BROWSING_ENABLED`
- `GOOGLE_SAFE_BROWSING_API_KEY`
- `GOOGLE_DRIVE_ENABLED`
- `GOOGLE_DRIVE_SERVICE_ACCOUNT_PATH`

## 3.4 Levantar backend

1. Levantar DB (si aplica):
```powershell
docker compose up -d
```

2. Ejecutar backend:
```powershell
.\mvnw.cmd spring-boot:run
```

## 3.5 Validar backend

Probar Swagger UI:
- `http://localhost:8080/swagger-ui/index.html`

Probar endpoint de registro usuario:
- `POST http://localhost:8080/api/v1/users`

Body ejemplo:
```json
{
  "name": "Usuario Demo",
  "email": "demo@manto.com",
  "phone": "+51999999999"
}
```

Probar analisis URL:
- `POST http://localhost:8080/api/v1/threats/url/analyze`

Body ejemplo:
```json
{
  "url": "http://testsafebrowsing.appspot.com/apiv4/ANY_PLATFORM/SOCIAL_ENGINEERING/URL/"
}
```

---

## 4) Manual Android Studio (Paso a paso)

## 4.1 Requisitos previos
- Android Studio instalado.
- Android SDK Platform 36.
- Emulador o dispositivo fisico.
- JDK 21 disponible.

## 4.2 Abrir proyecto
1. Abrir Android Studio.
2. `Open` -> `D:\integrador2\MOBILE`.

## 4.3 Configurar JDK de Gradle

En el proyecto existe:
- `gradle.properties` con:
  `org.gradle.java.home=C:/Program Files/Java/jdk-21`

Verifica que la ruta exista realmente.

Error comun:
- `Value 'C:Program FilesJavajdk-21binjava.exe' ... invalid`

Causa:
- Ruta mal escrita (sin `/` o `\`).

Solucion:
- Usar exactamente: `C:/Program Files/Java/jdk-21`

## 4.4 Verificar Firebase en Android

Archivo requerido:
- `app/google-services.json`

El proyecto ya lo referencia con plugin:
- `com.google.gms.google-services`

## 4.5 Configurar URL del backend en Android

Archivo:
- `app/src/main/java/com/guardianapp/mobile/data/api/RetrofitClient.java`

Variable:
- `BASE_URL`

Reglas obligatorias:
1. Debe terminar en `/`
2. Si usas ngrok, usar URL `https://...ngrok-free.app/`

Ejemplo correcto:
```java
private static final String BASE_URL = "https://xxxx-xx-xxx-xxx-xx.ngrok-free.app/";
```

Error comun:
- `IllegalArgumentException: baseUrl must end in /`

Solucion:
- Agregar `/` al final.

## 4.6 Compilar APK debug

En terminal del proyecto Android:
```powershell
cd D:\integrador2\MOBILE
.\gradlew.bat :app:assembleDebug
```

APK generado en:
- `app\build\outputs\apk\debug\app-debug.apk`

---

## 5) Conexion Android <-> Backend con ngrok

Usar cuando pruebas en dispositivo fisico o emulador externo.

1. Ejecutar backend en `localhost:8080`.
2. En otra terminal:
```powershell
ngrok http 8080
```
3. Copiar URL HTTPS publica de ngrok.
4. Pegarla en `RetrofitClient.BASE_URL` con `/` final.
5. Reinstalar/ejecutar app.

Errores comunes:
- `502 Bad Gateway`: backend caido o ngrok apuntando a puerto incorrecto.
- `404 Not Found`: endpoint incorrecto o ruta mal armada.
- `Network error`: URL vencida de ngrok o conectividad.

---

## 6) Pruebas Minimas Recomendadas (Smoke Test)

## Backend (Insomnia/Postman)
1. Crear usuario:
- `POST /api/v1/users`

2. Analizar URL sospechosa:
- `POST /api/v1/threats/url/analyze`

3. Registrar URL en blacklist:
- `POST /api/v1/blacklist/urls`

## Android
1. Login/registro en app.
2. Navegar a Escudo de Enlaces.
3. Analizar URL phishing de prueba.
4. Confirmar respuesta en UI.

---

## 7) Checklist final de entorno

- [ ] JDK 21 instalado
- [ ] PostgreSQL 16 activo (Docker o local)
- [ ] Credenciales DB alineadas entre backend y PostgreSQL
- [ ] Backend arriba en puerto 8080
- [ ] ngrok activo (si aplica)
- [ ] `BASE_URL` Android actualizado con `/` final
- [ ] `google-services.json` presente
- [ ] `assembleDebug` exitoso

---

## 8) Nota sobre login

En esta arquitectura, la autenticacion principal del lado movil se apoya en Firebase (cliente Android).
El backend expone operaciones de usuarios (`/api/v1/users`) pero no un endpoint clasico de login con password.

