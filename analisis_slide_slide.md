# Análisis de Depuración - Tabla `slide_slide`

**Fecha de análisis:** 10 de febrero de 2026  
**Base de datos:** UisepFinal (Réplica PostgreSQL)  
**Tabla analizada:** `slide_slide` (Odoo eLearning)

---

## Resumen Ejecutivo

| Métrica | Valor |
|---------|-------|
| **Tamaño Total** | 11 GB |
| **Registros Totales** | 37,608 |
| **Campo más pesado** | `html_content` = 9.8 GB |
| **Problema principal** | Imágenes Base64 embebidas en HTML |

---

## Estructura de la Tabla

### Campos Principales (68 columnas totales)

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | integer | ID único |
| `channel_id` | integer | FK a `slide_channel` (curso) |
| `name` | jsonb | Nombre multiidioma |
| `slide_type` | varchar | Tipo: article, bunny, pdf, scorm, etc. |
| `html_content` | jsonb | **Contenido HTML principal (9.8 GB)** |
| `active` | boolean | Si está activo |
| `is_published` | boolean | Si está publicado |
| `total_views` | integer | Vistas totales |
| `embeddings_json` | text | Embeddings IA (regenerable) |
| `convert` | text | Campo temporal |
| `preconverthtml` | text | Campo temporal |
| `preconvertdes` | text | Campo temporal |

### Tamaño por Campo de Texto

| Campo | Tamaño Total | Promedio | Registros |
|-------|--------------|----------|-----------|
| `html_content` | **9,887 MB** | 614 KB | 16,492 |
| `embeddings_json` | 250 MB | 21 KB | 12,332 |
| `html_embed_code` | 83 MB | 12 KB | 7,351 |
| `convert` | 29 MB | 2.4 KB | 12,699 |
| `preconverthtml` | 21 MB | 3.3 KB | 6,713 |
| `preconvertdes` | 790 KB | 4 KB | 199 |

---

## Distribución de Registros

### Por Año de Creación

| Año | Total | Activos | Inactivos | Publicados | HTML Size |
|-----|-------|---------|-----------|------------|-----------|
| 2023 | 16 | 13 | 3 | 14 | 31 bytes |
| 2024 | 27,616 | 24,084 | 3,532 | 14,484 | 9,706 MB |
| 2025 | 9,540 | 9,490 | 50 | 6,893 | 181 MB |
| 2026 | 436 | 436 | 0 | 331 | - |

### Por Tipo de Slide

| Tipo | Total | Inactivos | HTML Size | Promedio |
|------|-------|-----------|-----------|----------|
| article | 22,132 | 1,719 | **9,830 MB** | 611 KB |
| bunny | 1,111 | 62 | 56 MB | 28 MB |
| certification | 2,124 | 60 | 69 KB | 7 KB |
| pdf | 11,073 | 1,450 | - | - |
| scorm | 126 | 2 | 48 KB | 8 KB |
| youtube_video | 597 | 35 | - | - |
| local_external | 422 | 247 | - | - |

---

## Problema Principal: Imágenes Base64

### Estadísticas de Base64 Embebido

| Métrica | Valor |
|---------|-------|
| Registros con base64 | 2,866 |
| Tamaño total | **9,770 MB** (89% del total) |
| Promedio por registro | 3.4 MB |

### Distribución por Estado y Año

| Año | Con Base64 | Inactivos | No Publicados | Tamaño |
|-----|------------|-----------|---------------|--------|
| 2024 | 2,814 | 162 | 1,866 | 9,595 MB |
| 2025 | 52 | 1 | 33 | 176 MB |

### Registros Más Pesados (Top 10)

| ID | Nombre | Tipo | Tamaño | Base64 |
|----|--------|------|--------|--------|
| 4224 | Contenidos Didácticos | article | 81 MB | SÍ |
| 14141 | What is a Thesis... | article | 80 MB | SÍ |
| 13982 | Conceptos Básicos 2da parte | article | 80 MB | SÍ |
| 30794 | Relaciones medidas tendencia | article | 76 MB | SÍ |
| 4222 | Contenidos Didácticos | article | 72 MB | SÍ |
| 13985 | Conceptos Básicos 5ta parte | article | 70 MB | SÍ |
| 30709 | Rapport y empatía | article | 67 MB | SÍ |
| 30570 | Intro Neuropsicología | article | 67 MB | SÍ |
| 1020 | Acessibilidade ferramentas | article | 67 MB | SÍ |
| 30791 | Tendencia central | article | 66 MB | SÍ |

---

## Análisis de Uso Real

### Relación Canal-Slide

| Estado Canal | Slide Publicado | Slides | Tamaño | Vistas |
|--------------|-----------------|--------|--------|--------|
| Publicado | SÍ | 921 | 3,267 MB | 22,572 |
| Publicado | NO | 1,707 | 5,619 MB | 68,386 |
| No Publicado | SÍ | 41 | 36 MB | 45 |
| No Publicado | NO | 34 | 600 MB | 51 |

> **IMPORTANTE:** En Odoo eLearning, `is_published=false` en un slide NO impide el acceso si el canal está publicado. Significa "visible solo para miembros inscritos".

### Uso por Estado del Canal

| Estado Canal | Slides | Con Base64 | Tamaño | Vistas |
|--------------|--------|------------|--------|--------|
| **Activo y Publicado** | 30,906 | 2,628 | 8,987 MB | **613,760** |
| No publicado | 3,117 | 75 | 641 MB | 2,546 |

### Interacciones Recientes (desde Agosto 2025)

| Estado | Usuarios | Interacciones | Completados |
|--------|----------|---------------|-------------|
| Publicados | 1,356 | 86,942 | 84,812 |
| No Publicados | 1,151 | 93,332 | 91,492 |

---

## Candidatos a Depuración

### 🟢 SEGUROS (Sin impacto en usuarios)

#### 1. Slides Inactivos (`active = false`)
- **Registros:** 3,585
- **Tamaño:** 259 MB
- **Vistas históricas:** 4,897

```sql
-- Query para identificar
SELECT id, name, slide_type, create_date, total_views
FROM slide_slide 
WHERE active = false;

-- Query para eliminar (ejecutar en producción)
DELETE FROM slide_slide WHERE active = false;
```

#### 2. Campos Temporales/Regenerables
- **Tamaño total:** ~300 MB
- **Campos:** embeddings_json, convert, preconverthtml, preconvertdes

```sql
-- Query para limpiar
UPDATE slide_slide SET 
    embeddings_json = NULL,
    convert = NULL,
    preconverthtml = NULL,
    preconvertdes = NULL
WHERE embeddings_json IS NOT NULL 
   OR convert IS NOT NULL 
   OR preconverthtml IS NOT NULL;
```

### 🟡 REVISAR (Posible depuración)

#### 3. Canales No Publicados Principales

| Canal ID | Nombre | Slides | Tamaño | Vistas |
|----------|--------|--------|--------|--------|
| 1028 | Plantilla Módulo Licenciatura | 67 | 565 MB | 47 |
| 1113 | Plantilla Módulo Licenciatura | 24 | 32 MB | 10 |
| 1112 | Ingeniería Económica | 22 | 21 MB | 12 |
| 491 | PRUEBAS NO BORRAR | 15 | 400 KB | 24 |

```sql
-- Query para explorar canales no publicados
SELECT c.id, c.name, COUNT(s.id) as slides,
       pg_size_pretty(SUM(pg_column_size(s.html_content))::bigint) as size
FROM slide_channel c
JOIN slide_slide s ON s.channel_id = c.id
WHERE c.is_published = false AND c.active = true
GROUP BY c.id, c.name
ORDER BY SUM(pg_column_size(s.html_content)) DESC;
```

### 🔴 REQUIERE MIGRACIÓN (En uso activo)

#### 4. Imágenes Base64 en Contenido Activo
- **Registros:** 2,628 (en canales publicados)
- **Tamaño:** ~9 GB
- **Vistas:** 613,760

**No se pueden eliminar directamente.** Requieren migración a `ir_attachment`.

---

## Plan de Migración Base64 → Attachments

### Concepto

1. Extraer imágenes base64 del campo `html_content`
2. Crear `ir_attachment` para cada imagen
3. Reemplazar base64 por URL `/web/image/{attachment_id}`
4. Actualizar `html_content` con las nuevas URLs

### Estructura de ir_attachment

```sql
-- Campos relevantes de ir_attachment
INSERT INTO ir_attachment (
    name,           -- Nombre del archivo
    res_model,      -- 'slide.slide'
    res_id,         -- ID del slide
    type,           -- 'binary'
    datas,          -- Imagen en base64 (PostgreSQL la almacena eficientemente)
    mimetype,       -- 'image/png', 'image/jpeg', etc.
    public          -- true para acceso web
) VALUES (...);
```

### Patrón de Base64 a Detectar

```regex
data:image/(png|jpeg|jpg|gif|webp);base64,[A-Za-z0-9+/=]+
```

### API Requerida

```python
# Endpoint sugerido para migración
POST /api/v1/slide/migrate-base64/{slide_id}

# Respuesta esperada
{
    "slide_id": 4224,
    "images_migrated": 15,
    "size_before": "81 MB",
    "size_after": "50 KB",
    "attachments_created": [123, 124, 125, ...]
}
```

---

## Queries Útiles para API

### Obtener Slides con Base64

```sql
SELECT 
    id,
    name,
    pg_size_pretty(pg_column_size(html_content)::bigint) as size,
    LENGTH(html_content::text) as chars,
    (SELECT COUNT(*) 
     FROM regexp_matches(html_content::text, 'data:image/[^;]+;base64,', 'g')) as img_count
FROM slide_slide 
WHERE html_content::text LIKE '%data:image%base64%'
ORDER BY pg_column_size(html_content) DESC;
```

### Extraer URLs de Imágenes Base64

```sql
SELECT 
    id,
    (regexp_matches(html_content::text, 'data:image/([^;]+);base64,([A-Za-z0-9+/=]+)', 'g'))[1] as mime,
    LENGTH((regexp_matches(html_content::text, 'data:image/([^;]+);base64,([A-Za-z0-9+/=]+)', 'g'))[2]) as base64_length
FROM slide_slide
WHERE id = 4224;
```

### Verificar Estado de Replicación

```sql
SELECT 
    pg_is_in_recovery() as is_replica,
    pg_last_wal_receive_lsn() as last_wal,
    pg_last_xact_replay_timestamp() as last_replay,
    now() - pg_last_xact_replay_timestamp() as lag;
```

---

## Resumen de Ahorro Estimado

| Acción | Ahorro | Riesgo | Prioridad |
|--------|--------|--------|-----------|
| Eliminar slides inactivos | 259 MB | 🟢 Ninguno | Alta |
| Limpiar campos temporales | ~300 MB | 🟢 Regenerables | Alta |
| Eliminar canal plantilla 1028 | 565 MB | 🟡 Verificar | Media |
| **Migrar base64 → attachment** | **~9 GB** | 🟡 Requiere API | Alta |
| **TOTAL POSIBLE** | **~10 GB (91%)** | | |

---

## Conexión a la Réplica

```bash
# Acceso directo
docker exec postgres-replica-i4s8o8000kc040cgwcwowwwc psql -U odoo -d UisepFinal

# Query rápida
docker exec postgres-replica-i4s8o8000kc040cgwcwowwwc psql -U odoo -d UisepFinal -c "TU_QUERY"
```

| Parámetro | Valor |
|-----------|-------|
| Host | localhost (desde servidor) |
| Puerto | 56432 |
| Base de datos | UisepFinal |
| Usuario | odoo |
| Modo | Hot Standby (solo lectura) |
