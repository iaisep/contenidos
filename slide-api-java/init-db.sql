-- ===============================================
-- Script de inicialización para Supabase PostgreSQL
-- Schema: slide_api (separado de las tablas de Supabase)
-- ===============================================

-- Crear schema dedicado para la API
CREATE SCHEMA IF NOT EXISTS slide_api;

-- Extensiones útiles (ya disponibles en Supabase)
-- CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
-- CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Las tablas se crean automáticamente por Hibernate en el schema slide_api
-- Este script es para configuración adicional

-- Función para buscar slides por texto
CREATE OR REPLACE FUNCTION slide_api.search_slides(search_query TEXT)
RETURNS TABLE (
    id INTEGER,
    name VARCHAR,
    slide_type VARCHAR,
    channel_name VARCHAR,
    relevance REAL
) AS $$
BEGIN
    RETURN QUERY
    SELECT 
        s.id,
        s.name,
        s.slide_type,
        s.channel_name,
        similarity(s.name, search_query) AS relevance
    FROM slide_api.slides s
    WHERE s.name ILIKE '%' || search_query || '%'
       OR s.description ILIKE '%' || search_query || '%'
    ORDER BY relevance DESC
    LIMIT 100;
END;
$$ LANGUAGE plpgsql;

-- Vista para estadísticas de migración
CREATE OR REPLACE VIEW slide_api.migration_stats AS
SELECT 
    migration_status,
    COUNT(*) as count,
    SUM(original_size_bytes) as total_original_size,
    SUM(processed_size_bytes) as total_processed_size,
    SUM(original_size_bytes - processed_size_bytes) as total_saved,
    SUM(images_extracted) as total_images
FROM slide_api.slides
GROUP BY migration_status;

-- Vista para estadísticas por canal
CREATE OR REPLACE VIEW slide_api.channel_stats AS
SELECT 
    c.id,
    c.name,
    c.is_published,
    c.slide_count,
    c.total_size_bytes,
    (SELECT COUNT(*) FROM slide_api.slides s WHERE s.channel_id = c.id AND s.has_base64_original = true) as slides_with_base64,
    (SELECT SUM(images_extracted) FROM slide_api.slides s WHERE s.channel_id = c.id) as total_images_extracted
FROM slide_api.channels c
ORDER BY c.total_size_bytes DESC;
