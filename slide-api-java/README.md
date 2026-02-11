# UISEP Slide API

API Java (Spring Boot) para depurar y procesar slides desde la réplica de PostgreSQL de Odoo eLearning.

## Arquitectura

```
┌─────────────────────────────────────────────────────────────────┐
│                        SERVIDOR COOLIFY                          │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  ┌─────────────────┐    ┌─────────────────┐    ┌─────────────┐ │
│  │  PostgreSQL     │    │  Slide API      │    │  SUPABASE   │ │
│  │  Réplica Odoo   │───▶│  Spring Boot    │───▶│  PostgreSQL │ │
│  │  Puerto: 56432  │    │  Puerto: 8080   │    │  (existente)│ │
│  │  (Solo lectura) │    └────────┬────────┘    │  schema:    │ │
│  └─────────────────┘             │             │  slide_api  │ │
│                                  │             └─────────────┘ │
│                                  ▼                              │
│                        ┌─────────────────┐                      │
│                        │  Tu App/Frontend│                      │
│                        │  (Consume API)  │                      │
│                        └─────────────────┘                      │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

## Conexión a Supabase

La API usa el PostgreSQL de Supabase existente en Coolify:

| Parámetro | Valor |
|-----------|-------|
| **Contenedor** | `supabase-db-h4csg4oo4k08wc848008gcgw` |
| **Red Docker** | `h4csg4oo4k08wc848008gcgw` |
| **Puerto** | `5432` (interno) |
| **Usuario** | `postgres` |
| **Password** | `1fqsZrj7ATbnjSjCKShU2h5A2wi0SIrl` |
| **Schema** | `slide_api` (separado de tablas Supabase) |

## Funcionalidades

### 1. Sincronización de Slides
- Lee slides desde la réplica de Odoo (solo lectura)
- Extrae imágenes Base64 embebidas en HTML
- Almacena datos limpios en nueva base de datos
- Sincronización programada cada 6 horas

### 2. Migración de Imágenes Base64
- Detecta imágenes Base64 en `html_content`
- Decodifica y almacena como binarios
- Reemplaza Base64 con URLs de la API
- Deduplicación por hash SHA-256

### 3. API REST para tu App
- Slides procesados con HTML limpio
- Imágenes servidas via API
- Búsqueda por nombre
- Filtrado por canal/curso

## Endpoints Principales

### Para la App
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| GET | `/api/v1/slides` | Lista slides publicados |
| GET | `/api/v1/slides/{id}` | Obtiene slide con HTML procesado |
| GET | `/api/v1/slides/search?q=` | Busca por nombre |
| GET | `/api/v1/channels` | Lista canales/cursos |
| GET | `/api/v1/images/{slideId}/{filename}` | Sirve imagen |

### Administración
| Método | Endpoint | Descripción |
|--------|----------|-------------|
| POST | `/api/v1/admin/sync` | Sincroniza todos los slides |
| POST | `/api/v1/admin/sync/slide/{id}` | Sincroniza un slide |
| POST | `/api/v1/admin/migrate-base64?limit=10` | Migra imágenes Base64 |
| GET | `/api/v1/admin/stats` | Estadísticas de depuración |

### Documentación
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

## Despliegue en Coolify

### Paso 1: Verificar Supabase PostgreSQL

El schema `slide_api` ya está creado en Supabase. Verificar:

```bash
docker exec supabase-db-h4csg4oo4k08wc848008gcgw psql -U postgres -c "\\dn"
```

### Paso 2: Desplegar la API

1. En Coolify, ir a **Resources** → **New** → **Service**
2. Seleccionar **Dockerfile**
3. Configurar:
   - **Name**: `uisep-slide-api`
   - **Repository**: (URL de tu repo con este código)
   - **Branch**: `main`
   - **Dockerfile**: `Dockerfile`
   - **Port**: `8080`

4. **Variables de Entorno** (Settings → Environment Variables):

```env
# Conexión a réplica de Odoo (SOLO LECTURA)
SPRING_DATASOURCE_REPLICA_URL=jdbc:postgresql://host.docker.internal:56432/UisepFinal
SPRING_DATASOURCE_REPLICA_USERNAME=odoo
REPLICA_DB_PASSWORD=odoo

# Conexión a Supabase PostgreSQL (LECTURA/ESCRITURA)
SUPABASE_DB_HOST=supabase-db-h4csg4oo4k08wc848008gcgw
SUPABASE_DB_USER=postgres
SUPABASE_DB_PASSWORD=1fqsZrj7ATbnjSjCKShU2h5A2wi0SIrl

# Java y Spring
JAVA_OPTS=-Xms256m -Xmx1g
SPRING_PROFILES_ACTIVE=production
```

5. **Networking** (IMPORTANTE):
   - Conectar la API a la red de Supabase: `h4csg4oo4k08wc848008gcgw`
   - En Coolify → Service Settings → Networks → Añadir red externa

6. Guardar y desplegar

### Paso 3: Sincronización Inicial

Una vez desplegado, ejecutar sincronización inicial:

```bash
# Desde el servidor o usando la UI de Swagger
curl -X POST http://localhost:8080/api/v1/admin/sync

# O para un número limitado (probar primero)
curl -X POST "http://localhost:8080/api/v1/admin/migrate-base64?limit=5"
```

## Desarrollo Local

### Requisitos
- Java 17+
- Maven 3.9+
- Docker (para conectar a Supabase)

### Ejecutar con Maven

```bash
cd slide-api-java

# Configurar variables de entorno
export REPLICA_DB_PASSWORD=odoo
export SUPABASE_DB_HOST=supabase-db-h4csg4oo4k08wc848008gcgw
export SUPABASE_DB_PASSWORD=1fqsZrj7ATbnjSjCKShU2h5A2wi0SIrl

# Compilar y ejecutar
./mvnw spring-boot:run
```

### Ejecutar con Docker Compose

```bash
# Iniciar servicios
docker-compose up -d

# Ver logs
docker-compose logs -f slide-api

# Parar
docker-compose down
```

## Estructura del Proyecto

```
slide-api-java/
├── src/main/java/com/uisep/slideapi/
│   ├── config/                 # Configuración de DataSources
│   │   ├── ReplicaDataSourceConfig.java    # BD réplica (lectura)
│   │   └── ProcessedDataSourceConfig.java  # BD procesada (escritura)
│   ├── entity/
│   │   ├── replica/           # Entidades de Odoo (solo lectura)
│   │   └── processed/         # Entidades procesadas
│   ├── repository/
│   │   ├── replica/           # Repositorios de lectura
│   │   └── processed/         # Repositorios de escritura
│   ├── service/
│   │   ├── Base64ImageExtractor.java   # Extrae imágenes
│   │   ├── SlideSyncService.java       # Sincronización
│   │   └── SlideQueryService.java      # Consultas para app
│   ├── controller/             # Endpoints REST
│   └── dto/                    # Objetos de transferencia
├── src/main/resources/
│   └── application.yml         # Configuración
├── Dockerfile                  # Build de contenedor
├── docker-compose.yml          # Orquestación local
└── init-db.sql                 # Script de inicialización BD
```

## Ahorro Estimado

Basado en el análisis de `slide_slide`:

| Acción | Ahorro Estimado |
|--------|-----------------|
| Eliminar slides inactivos | 259 MB |
| Limpiar campos temporales | ~300 MB |
| Migrar Base64 → API images | **~9 GB** |
| **TOTAL** | **~10 GB (91%)** |

## Notas Importantes

1. **La réplica es SOLO LECTURA**: La API nunca escribe en la réplica de Odoo.

2. **Imágenes duplicadas**: El sistema detecta duplicados por hash SHA-256 para ahorrar espacio.

3. **Sincronización automática**: Se ejecuta cada 6 horas. Modificar en `application.yml` si es necesario.

4. **HTML procesado**: Las URLs de imágenes en el HTML procesado apuntan a `/api/v1/images/{slideId}/{filename}`.

## Soporte

Para problemas o preguntas, revisar:
- Logs: `/var/log/slide-api/application.log`
- Health check: `GET /api/v1/admin/health`
- Swagger: `http://localhost:8080/swagger-ui.html`
