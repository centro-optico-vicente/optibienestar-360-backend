# Guía de Inicio Rápido - Entorno Local (OptiBienestar 360)

Esta guía documenta los pasos necesarios para configurar y levantar la base de datos, el backend (**Spring Boot 4**) y el frontend (**Nuxt 4**) en tu entorno local sin depender de Docker Compose.

---

## 1. Requisitos Previos

- **Java**: JDK 21 / 25
- **Node.js**: Node.js `>= 20.12.0` o `Node.js 22 LTS` (Recomendado)
- **Package Manager**: `pnpm` (`npm install -g pnpm`)
- **PostgreSQL**: Instancia corriendo localmente o mediante contenedor Docker (`postgres13`).

> **Tip para actualizar Node.js en Windows**:
> ```cmd
> winget install OpenJS.NodeJS.LTS
> ```

---

## 2. Configuración de la Base de Datos (PostgreSQL)

Si estás usando el contenedor Docker `postgres13` expuesto en el puerto host `5413`:

### Paso 2.1: Crear la Base de Datos
```bash
docker exec postgres13 psql -U postgres -c "CREATE DATABASE optibienestar360;"
```

### Paso 2.2: Inicializar Roles de Usuario
Ejecuta el script SQL [init-db-roles.sql](file:///l:/fenix-core/optibienestar-360-backend/scripts/init-db-roles.sql) ubicado en el backend:

```powershell
# Desde la raíz del backend (l:\fenix-core\optibienestar-360-backend):
Get-Content scripts\init-db-roles.sql | docker exec -i postgres13 psql -U postgres -d optibienestar360
```

### Paso 2.3: Conceder Permisos al Rol de Migración
Otorgar permisos de `SUPERUSER` y `CREATEROLE` al usuario `optibienestar360_migration` para permitir la ejecución de scripts DDL de Flyway (`V1` a `V51`):

```bash
docker exec postgres13 psql -U postgres -d optibienestar360 -c "ALTER ROLE optibienestar360_migration WITH SUPERUSER CREATEROLE;"
```

> **Nota**: Las 51 migraciones Flyway (tablas, funciones, catálogos, roles y semillas) se aplican **automáticamente** al arrancar el backend por primera vez.

---

## 3. Levantar el Backend (`optibienestar-360-backend`)

### En Windows (PowerShell / CMD):

```cmd
cd l:\fenix-core\optibienestar-360-backend

gradlew.bat bootRun --args=--spring.profiles.active=dev
```

### Configuración por Defecto (Perfil `dev`):
Los valores por defecto para desarrollo local están preconfigurados en `src/main/resources/application-dev.properties`:
- `DATABASE_PORT=5413`: Mapeo del puerto de Postgres.
- `DATABASE_MIGRATION_USER=optibienestar360_migration`: Usuario con permisos para ejecutar Flyway.
- `AUTH_TOKEN_BLACKLIST_PROVIDER=memory`: Evita la dependencia de Redis local para la blacklist de tokens JWT.

*(Nota: Si deseas sobreescribir cualquiera de estos valores sin modificar el código, puedes pasar la variable de entorno correspondiente al momento de ejecutar).*

### Verificación:
- **API Base**: `http://localhost:8080`
- **Health Check**: `http://localhost:8080/actuator/health` (debe responder `UP`)
- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`

---

## 4. Levantar el Frontend (`optibienestar-360-frontend`)

### Paso 4.1: Instalación de Dependencias
```cmd
cd l:\fenix-core\optibienestar-360-frontend

pnpm install
```

### Paso 4.2: Iniciar Servidor de Desarrollo
```cmd
pnpm dev
```

### Verificación:
- **Interfaz Web**: `http://localhost:3000/`

---

## 5. Resumen de Puertos y Servicios

| Servicio | URL / Host | Puerto |
|---|---|---|
| PostgreSQL | `localhost` | `5413` |
| Backend REST API | `http://localhost:8080` | `8080` |
| Documentación Swagger | `http://localhost:8080/swagger-ui/index.html` | `8080` |
| Frontend Nuxt | `http://localhost:3000` | `3000` |
