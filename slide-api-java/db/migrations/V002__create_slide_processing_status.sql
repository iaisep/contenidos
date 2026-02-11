-- Tabla de control para tracking de sincronización de slides
CREATE TABLE IF NOT EXISTS slide_processing_status (
    slide_id INTEGER PRIMARY KEY,
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    failed_at TIMESTAMP,
    error_message VARCHAR(2000),
    retry_count INTEGER DEFAULT 0,
    original_size_bytes BIGINT,
    processed_size_bytes BIGINT,
    images_extracted INTEGER
);

-- Índices para mejorar consultas
CREATE INDEX IF NOT EXISTS idx_processing_status ON slide_processing_status(status);
CREATE INDEX IF NOT EXISTS idx_processing_completed_at ON slide_processing_status(completed_at);

-- Comentarios
COMMENT ON TABLE slide_processing_status IS 'Tabla de control para trackear el progreso de sincronización de slides';
COMMENT ON COLUMN slide_processing_status.slide_id IS 'ID del slide en la réplica de Odoo';
COMMENT ON COLUMN slide_processing_status.status IS 'Estado actual del procesamiento: PENDING, PROCESSING, COMPLETED, FAILED';
COMMENT ON COLUMN slide_processing_status.retry_count IS 'Número de reintentos en caso de fallo';
