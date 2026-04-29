package com.uisep.slideapi.service;

import com.uisep.slideapi.dto.SlideDTO.*;
import com.uisep.slideapi.entity.processed.ProcessedChannel;
import com.uisep.slideapi.entity.processed.ProcessedSlide;
import com.uisep.slideapi.entity.processed.SlideProcessingStatus;
import com.uisep.slideapi.entity.replica.SlideChannelReplica;
import com.uisep.slideapi.entity.replica.SlideSlideReplica;
import com.uisep.slideapi.repository.processed.ProcessedChannelRepository;
import com.uisep.slideapi.repository.processed.ProcessedSlideRepository;
import com.uisep.slideapi.repository.processed.SlideImageRepository;
import com.uisep.slideapi.repository.processed.SlideProcessingStatusRepository;
import com.uisep.slideapi.repository.replica.SlideChannelReplicaRepository;
import com.uisep.slideapi.repository.replica.SlideSlideReplicaRepository;
import com.uisep.slideapi.service.OdooFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Servicio principal para sincronizar datos desde la réplica y procesarlos.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SlideSyncService {
    
    private final SlideSlideReplicaRepository replicaSlideRepo;
    private final SlideChannelReplicaRepository replicaChannelRepo;
    private final ProcessedSlideRepository processedSlideRepo;
    private final ProcessedChannelRepository processedChannelRepo;
    private final SlideImageRepository imageRepo;
    private final SlideProcessingStatusRepository processingStatusRepo;
    private final Base64ImageExtractor imageExtractor;
    private final OdooFileService odooFileService;
    
    @Value("${migration.base64.batch-size:10}")
    private int batchSize;
    
    @Value("${server.port:8080}")
    private int serverPort;
    
    private String getBaseUrl() {
        return "http://localhost:" + serverPort;
    }
    
    /**
     * Sincronización programada automática.
     * Se ejecuta según el cron definido en application.yml
     */
    @Scheduled(cron = "${migration.sync.cron:0 0 */6 * * *}")
    public void scheduledSync() {
        log.info("Iniciando sincronización programada...");
        try {
            SyncResult result = syncAllSlides(true);
            log.info("Sincronización completada: {} slides procesados, {} creados, {} actualizados",
                result.getSlidesProcessed(), result.getSlidesCreated(), result.getSlidesUpdated());
        } catch (Exception e) {
            log.error("Error en sincronización programada", e);
        }
    }
    
    /**
     * Inicializa tabla de tracking con todos los slides activos.
     * Solo crea registros para slides que no existen en el tracking.
     */
    @Transactional("processedTransactionManager")
    public void initializeTracking() {
        List<Integer> activeSlideIds = replicaSlideRepo.findActiveSlideIds();
        int initialized = 0;
        
        log.info("Inicializando tracking para {} slides activos...", activeSlideIds.size());
        
        for (Integer slideId : activeSlideIds) {
            if (!processingStatusRepo.existsById(slideId)) {
                SlideProcessingStatus status = SlideProcessingStatus.builder()
                    .slideId(slideId)
                    .status(SlideProcessingStatus.ProcessingStatus.PENDING)
                    .retryCount(0)
                    .build();
                processingStatusRepo.save(status);
                initialized++;
            }
        }
        
        log.info("Tracking inicializado: {} nuevos registros", initialized);
    }
    
    /**
     * Sincroniza todos los slides activos desde la réplica.
     * Usa tracking para procesar slide por slide y permitir reanudar.
     * SIN @Transactional aquí - cada slide tiene su propia transacción.
     * 
     * @param activeOnly Si true, solo procesa slides activos
     * @return Resultado de la sincronización
     */
    public SyncResult syncAllSlides(boolean activeOnly) {
        LocalDateTime startTime = LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        
        List<MigrationResult> migrationResults = new ArrayList<>();
        int created = 0, updated = 0, failed = 0, totalProcessed = 0;
        long totalOriginalSize = 0, totalProcessedSize = 0;
        
        // Cargar canales para enriquecer nombres
        Map<Integer, String> channelNames = loadChannelNames();
        
        // 1. Resetear slides que cambiaron en Odoo desde la última sincronización
        int outdatedReset = resetOutdatedSlides();
        if (outdatedReset > 0) {
            log.info("Detectados {} slides modificados en Odoo → reseteados a PENDING", outdatedReset);
        }

        // 2. Inicializar tracking si es necesario
        initializeTracking();
        
        // 3. Resetear slides que quedaron en PROCESSING (por interrupciones previas)
        List<SlideProcessingStatus> stuckInProcessing = processingStatusRepo.findByStatus(
            SlideProcessingStatus.ProcessingStatus.PROCESSING);
        for (SlideProcessingStatus stuck : stuckInProcessing) {
            stuck.setStatus(SlideProcessingStatus.ProcessingStatus.PENDING);
            processingStatusRepo.save(stuck);
        }
        
        // 4. Obtener slides pendientes o fallidos
        List<Integer> slideIds = processingStatusRepo.findPendingSlideIds();
        int totalSlides = slideIds.size();
        
        log.info("Iniciando sincronización: {} slides pendientes...", totalSlides);
        
        for (Integer slideId : slideIds) {
            try {
                // Procesar en transacción individual
                MigrationResult result = processSingleSlideWithTracking(slideId, channelNames);
                
                migrationResults.add(result);
                
                if ("CREATED".equals(result.getStatus())) created++;
                else if ("UPDATED".equals(result.getStatus())) updated++;
                else if ("FAILED".equals(result.getStatus())) failed++;
                
                totalOriginalSize += result.getOriginalSize() != null ? result.getOriginalSize() : 0;
                totalProcessedSize += result.getNewSize() != null ? result.getNewSize() : 0;
                
            } catch (Exception e) {
                log.error("Error crítico procesando slide {}: {}", slideId, e.getMessage());
                failed++;
                migrationResults.add(MigrationResult.builder()
                    .slideId(slideId)
                    .slideName("Unknown")
                    .status("FAILED")
                    .message("Error crítico: " + e.getMessage())
                    .processedAt(LocalDateTime.now())
                    .build());
            }
            
            totalProcessed++;
            
            // Log progreso cada 50 slides (más frecuente para mejor visibilidad)
            if (totalProcessed % 50 == 0) {
                long elapsed = System.currentTimeMillis() - startMs;
                double rate = totalProcessed / (elapsed / 1000.0);
                int remaining = totalSlides - totalProcessed;
                int etaSeconds = (int) (remaining / rate);
                
                log.info("Progreso: {}/{} slides ({} creados, {} actualizados, {} fallidos) - {:.1f} slides/seg - ETA: {}s",
                    totalProcessed, totalSlides, created, updated, failed, rate, etaSeconds);
            }
            
            // Limitar resultados en memoria
            if (migrationResults.size() > 1000) {
                migrationResults = migrationResults.subList(migrationResults.size() - 100, migrationResults.size());
            }
        }
        
        // Sincronizar canales
        int channelsProcessed = syncChannels();
        
        long duration = System.currentTimeMillis() - startMs;
        
        return SyncResult.builder()
            .startedAt(startTime)
            .completedAt(LocalDateTime.now())
            .durationMs(duration)
            .slidesProcessed(totalProcessed)
            .slidesCreated(created)
            .slidesUpdated(updated)
            .slidesFailed(failed)
            .channelsProcessed(channelsProcessed)
            .totalOriginalSize(totalOriginalSize)
            .totalProcessedSize(totalProcessedSize)
            .migrationResults(migrationResults)
            .build();
    }
    
    /**
     * Procesa un slide individual con su propia transacción.
     * Cada slide se guarda independientemente - si falla, no afecta a los demás.
     * REQUIRES_NEW fuerza nueva transacción incluso si se llama desde mismo bean.
     */
    @Transactional(transactionManager = "processedTransactionManager", propagation = Propagation.REQUIRES_NEW)
    public MigrationResult processSingleSlideWithTracking(Integer slideId, Map<Integer, String> channelNames) {
        SlideProcessingStatus trackingStatus = processingStatusRepo.findById(slideId)
            .orElse(SlideProcessingStatus.builder()
                .slideId(slideId)
                .status(SlideProcessingStatus.ProcessingStatus.PENDING)
                .retryCount(0)
                .build());
        
        try {
            // Marcar como en proceso
            trackingStatus.setStatus(SlideProcessingStatus.ProcessingStatus.PROCESSING);
            trackingStatus.setStartedAt(LocalDateTime.now());
            processingStatusRepo.saveAndFlush(trackingStatus);
            
            // Cargar slide individualmente
            SlideSlideReplica replica = replicaSlideRepo.findById(slideId).orElse(null);
            if (replica == null) {
                log.warn("Slide {} no encontrado en réplica", slideId);
                trackingStatus.setStatus(SlideProcessingStatus.ProcessingStatus.FAILED);
                trackingStatus.setFailedAt(LocalDateTime.now());
                trackingStatus.setErrorMessage("Slide no encontrado en réplica");
                processingStatusRepo.saveAndFlush(trackingStatus);
                
                return MigrationResult.builder()
                    .slideId(slideId)
                    .slideName("Unknown")
                    .status("FAILED")
                    .message("No encontrado en réplica")
                    .processedAt(LocalDateTime.now())
                    .build();
            }
            
            // Procesar slide
            MigrationResult result = processSlide(replica, channelNames);
            
            // Actualizar tracking como completado
            trackingStatus.setStatus(SlideProcessingStatus.ProcessingStatus.COMPLETED);
            trackingStatus.setCompletedAt(LocalDateTime.now());
            trackingStatus.setOriginalSizeBytes(result.getOriginalSize());
            trackingStatus.setProcessedSizeBytes(result.getNewSize());
            trackingStatus.setImagesExtracted(result.getImagesExtracted());
            processingStatusRepo.saveAndFlush(trackingStatus);
            
            return result;
            
        } catch (OutOfMemoryError e) {
            // Error de memoria
            System.err.println("[OOM] Slide " + slideId + ": contenido demasiado grande para heap");
            
            trackingStatus.setStatus(SlideProcessingStatus.ProcessingStatus.FAILED);
            trackingStatus.setFailedAt(LocalDateTime.now());
            trackingStatus.setErrorMessage("OutOfMemory - contenido muy grande");
            trackingStatus.setRetryCount(trackingStatus.getRetryCount() + 1);
            processingStatusRepo.saveAndFlush(trackingStatus);
            
            // Forzar GC para recuperar memoria
            System.gc();
            
            return MigrationResult.builder()
                .slideId(slideId)
                .slideName("Unknown")
                .status("FAILED")
                .message("OutOfMemory - slide muy grande")
                .processedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error procesando slide {}: {}", slideId, e.getMessage(), e);
            
            // Marcar como fallido
            trackingStatus.setStatus(SlideProcessingStatus.ProcessingStatus.FAILED);
            trackingStatus.setFailedAt(LocalDateTime.now());
            trackingStatus.setErrorMessage(e.getMessage() != null ? 
                e.getMessage().substring(0, Math.min(1999, e.getMessage().length())) : "Error desconocido");
            trackingStatus.setRetryCount(trackingStatus.getRetryCount() + 1);
            processingStatusRepo.saveAndFlush(trackingStatus);
            
            return MigrationResult.builder()
                .slideId(slideId)
                .slideName("Unknown")
                .status("FAILED")
                .message(e.getMessage())
                .processedAt(LocalDateTime.now())
                .build();
        }
    }
    
    /**
     * Procesa un slide individual: extrae imágenes y guarda en BD procesada.
     */
    private MigrationResult processSlide(SlideSlideReplica replica, Map<Integer, String> channelNames) {
        String slideName = replica.getNameEs();
        String slideType = replica.getSlideType();
        
        // Resolver htmlContent y contentUrl según tipo de slide
        // Para artículos: use_html_embed=true → html_embed_code tiene precedencia sobre html_content
        String htmlContent = "article".equals(slideType) || "certification".equals(slideType)
            ? replica.resolveHtmlContent()
            : null;
        String contentUrl = resolveContentUrl(replica);
        String youtubeId = resolveYoutubeId(replica);

        // Descargar archivo local para slides tipo PDF/presentation/infographic/webpage
        // Evita depender de credenciales Odoo en la app móvil
        boolean isFileType = "pdf".equals(slideType) || "presentation".equals(slideType)
            || "infographic".equals(slideType) || "webpage".equals(slideType);
        boolean fileDownloaded = false;

        if (isFileType) {
            // Always use local API URL — FileController downloads on first request
            contentUrl = "https://slides.universidadisep.com/api/v1/files/" + replica.getId();
            // Pre-download if attachment exists in Odoo
            if (odooFileService.hasRemoteFile(replica.getId())) {
                Optional<Path> localFile = odooFileService.downloadAndSave(replica.getId());
                fileDownloaded = localFile.isPresent();
            }
        }

        
        long originalSize = htmlContent != null ? htmlContent.length() : 0;
        
        // Verificar si ya existe y si necesita actualización
        Optional<ProcessedSlide> existing = processedSlideRepo.findById(replica.getId());
        boolean isNew = existing.isEmpty();
        // Also update if file was just downloaded but Supabase still has the old URL
        // Or if slide has remote file but not yet downloaded locally
        boolean needsFileUrlUpdate = isFileType && !isNew && (
            (fileDownloaded && existing.get().getContentUrl() != null 
             && existing.get().getContentUrl().contains("app.universidadisep.com"))
            || (!fileDownloaded && !odooFileService.exists(replica.getId()) 
                && odooFileService.hasRemoteFile(replica.getId()))
        );
        boolean needsUpdate = needsFileUrlUpdate || (!isNew && replica.getWriteDate() != null &&
            (existing.get().getOdooWriteDate() == null ||
             replica.getWriteDate().isAfter(existing.get().getOdooWriteDate())));
        
        if (!isNew && !needsUpdate) {
            return MigrationResult.builder()
                .slideId(replica.getId())
                .slideName(slideName)
                .status("SKIPPED")
                .message("Sin cambios desde última sincronización")
                .processedAt(LocalDateTime.now())
                .build();
        }
        
        // Extraer imágenes Base64
        String processedHtml = htmlContent;
        int imagesExtracted = 0;
        
        if (imageExtractor.hasBase64Images(htmlContent)) {
            Base64ImageExtractor.ExtractionResult extraction = 
                imageExtractor.extractAndProcess(replica.getId(), htmlContent, getBaseUrl());
            processedHtml = extraction.cleanedHtml();
            imagesExtracted = extraction.extractedImages().size();
        }
        
        long processedSize = processedHtml != null ? processedHtml.length() : 0;
        
        // Crear o actualizar el slide procesado
        ProcessedSlide processed = existing.orElse(new ProcessedSlide());
        boolean hasContent = processedHtml != null && !processedHtml.isEmpty();
        boolean hasUrl = contentUrl != null && !contentUrl.isEmpty();
        String status = imagesExtracted > 0 ? "COMPLETED"
            : (hasContent || hasUrl) ? "NO_MIGRATION_NEEDED"
            : "PENDING";
        
        processed.setId(replica.getId());
        processed.setChannelId(replica.getChannelId());
        processed.setChannelName(channelNames.get(replica.getChannelId()));
        processed.setName(slideName);
        processed.setSlideType(replica.getSlideType());
        processed.setHtmlContent(processedHtml);
        processed.setContentUrl(contentUrl);
        processed.setYoutubeId(youtubeId);
        processed.setDescription(replica.getDescription());
        processed.setActive(replica.getActive());
        processed.setIsPublished(replica.getIsPublished());
        processed.setTotalViews(replica.getTotalViews());
        processed.setOriginalSizeBytes(originalSize);
        processed.setProcessedSizeBytes(processedSize);
        processed.setImagesExtracted(imagesExtracted);
        processed.setHasBase64Original(replica.hasBase64Images());
        processed.setOdooCreateDate(replica.getCreateDate());
        processed.setOdooWriteDate(replica.getWriteDate());
        processed.setMigrationStatus(status);
        processed.setFileDownloaded(fileDownloaded);
        
        processedSlideRepo.save(processed);
        
        return MigrationResult.builder()
            .slideId(replica.getId())
            .slideName(slideName)
            .status(isNew ? "CREATED" : "UPDATED")
            .imagesExtracted(imagesExtracted)
            .originalSize(originalSize)
            .newSize(processedSize)
            .savedBytes(originalSize - processedSize)
            .message(isNew ? "Slide creado" : "Slide actualizado")
            .processedAt(LocalDateTime.now())
            .build();
    }
    

    /**
     * Resuelve la URL del contenido según el tipo de slide.
     * Los slides tipo article tienen su contenido en htmlContent.
     * Los demás tipos exponen una URL a su recurso multimedia.
     */
    private String resolveContentUrl(SlideSlideReplica replica) {
        String type = replica.getSlideType();
        if (type == null) return null;
        return switch (type) {
            case "video"          -> replica.getBunnyUrl();
            case "youtube_video"  -> replica.getUrl();
            case "local_external" -> replica.getExternalUrl();
            case "pdf", "presentation", "infographic", "webpage" -> null;
            default -> null;
        };
    }
    
    /**
     * Extrae el ID de YouTube de la URL del slide.
     */
    private String resolveYoutubeId(SlideSlideReplica replica) {
        if (!"youtube_video".equals(replica.getSlideType())) return null;
        String url = replica.getUrl();
        if (url == null) return null;
        // Formatos: watch?v=ID, youtu.be/ID, embed/ID
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?:v=|youtu[.]be/|embed/)([A-Za-z0-9_-]{11})")
            .matcher(url);
        return m.find() ? m.group(1) : null;
    }
    
    /**
     * Sincroniza canales desde la réplica.
     */
    private int syncChannels() {
        List<SlideChannelReplica> channels = replicaChannelRepo.findByActiveTrue();
        int processed = 0;
        
        for (SlideChannelReplica replica : channels) {
            try {
                ProcessedChannel channel = processedChannelRepo.findById(replica.getId())
                    .orElse(new ProcessedChannel());
                
                channel.setId(replica.getId());
                channel.setName(replica.getNameEs());
                channel.setActive(replica.getActive());
                channel.setIsPublished(replica.getIsPublished());
                channel.setTotalViews(replica.getTotalViews());
                channel.setOdooCreateDate(replica.getCreateDate());
                channel.setOdooWriteDate(replica.getWriteDate());
                
                // Calcular estadísticas de slides (aggregate, sin cargar entidades)
                channel.setSlideCount((int) processedSlideRepo.countByChannelId(replica.getId()));
                channel.setTotalSizeBytes(processedSlideRepo.sumProcessedSizeByChannelId(replica.getId()));
                
                processedChannelRepo.save(channel);
                processed++;
            } catch (Exception e) {
                log.error("Error sincronizando canal {}: {}", replica.getId(), e.getMessage());
            }
        }
        
        return processed;
    }
    
    /**
     * Carga nombres de canales para enriquecer los slides.
     */
    private Map<Integer, String> loadChannelNames() {
        Map<Integer, String> names = new HashMap<>();
        for (SlideChannelReplica channel : replicaChannelRepo.findAll()) {
            names.put(channel.getId(), channel.getNameEs());
        }
        return names;
    }
    
    /**
     * Sincroniza un slide específico por ID.
     */
    @Transactional("processedTransactionManager")
    public MigrationResult syncSlideById(Integer slideId) {
        SlideSlideReplica replica = replicaSlideRepo.findById(slideId)
            .orElseThrow(() -> new RuntimeException("Slide no encontrado: " + slideId));
        
        Map<Integer, String> channelNames = loadChannelNames();
        return processSlide(replica, channelNames);
    }
    
    /**
     * Migra imágenes Base64 de slides pendientes.
     */
    @Transactional("processedTransactionManager")
    public List<MigrationResult> migrateBase64Slides(int limit) {
        List<SlideSlideReplica> slidesWithBase64 = 
            replicaSlideRepo.findSlidesWithBase64ImagesPaginated(limit, 0);
        
        List<MigrationResult> results = new ArrayList<>();
        Map<Integer, String> channelNames = loadChannelNames();
        
        for (SlideSlideReplica replica : slidesWithBase64) {
            results.add(processSlide(replica, channelNames));
        }
        
        return results;
    }
    
    /**
     * Obtiene estadísticas de depuración.
     */
    public DepurationStats getDepurationStats() {
        // Estadísticas de la réplica (aggregate queries, sin cargar entidades)
        long totalSlides = replicaSlideRepo.count();
        long activeSlides = replicaSlideRepo.countByActiveTrue();
        long inactiveSlides = replicaSlideRepo.countByActiveFalse();
        
        // Base64 scan omitido del stats (LIKE sobre TEXT column de 37k filas es muy lento)
        long slidesWithBase64 = -1L;
        long base64Size = -1L;
        
        // Estadísticas de procesados (consultas individuales, evitar Object[] multi-columna)
        Long totalOriginalSizeL = processedSlideRepo.sumOriginalSizeCompleted();
        Long totalProcessedSizeL = processedSlideRepo.sumProcessedSizeCompleted();
        Long totalImagesExtractedL = processedSlideRepo.sumImagesExtractedCompleted();
        long totalOriginalSize = totalOriginalSizeL != null ? totalOriginalSizeL : 0L;
        long totalProcessedSize = totalProcessedSizeL != null ? totalProcessedSizeL : 0L;
        long totalImagesExtracted = totalImagesExtractedL != null ? totalImagesExtractedL : 0L;
        
        long savedBytes = totalOriginalSize - totalProcessedSize;
        double savingsPercentage = totalOriginalSize > 0 
            ? (savedBytes * 100.0 / totalOriginalSize) 
            : 0;
        
        // Acciones pendientes (aggregate queries, sin cargar entidades)
        long inactiveCount = replicaSlideRepo.countByActiveFalse();
        long inactiveSize = replicaSlideRepo.sumHtmlContentSizeInactive();

        PendingActions pending = PendingActions.builder()
            .inactiveSlidesToDelete(inactiveCount)
            .inactiveSlidesSize(inactiveSize)
            .slidesToMigrateBase64(slidesWithBase64)
            .base64Size(base64Size)
            .temporaryFieldsToClean(0L) // TODO: implementar
            .temporaryFieldsSize(0L)
            .build();
        
        return DepurationStats.builder()
            .totalSlides(totalSlides)
            .activeSlides(activeSlides)
            .inactiveSlides(inactiveSlides)
            .slidesWithBase64(slidesWithBase64)
            .totalOriginalSizeBytes(totalOriginalSize)
            .totalProcessedSizeBytes(totalProcessedSize)
            .totalSavedBytes(savedBytes)
            .savingsPercentage(savingsPercentage)
            .totalImagesExtracted(totalImagesExtracted)
            .pendingActions(pending)
            .build();
    }
    
    /**
     * Resetea slides que quedaron atascados en estado PROCESSING.
     */
    @Transactional("processedTransactionManager")
    public int resetStuckSlides() {
        List<SlideProcessingStatus> stuck = processingStatusRepo.findByStatus(
            SlideProcessingStatus.ProcessingStatus.PROCESSING);
        
        for (SlideProcessingStatus status : stuck) {
            status.setStatus(SlideProcessingStatus.ProcessingStatus.PENDING);
            processingStatusRepo.save(status);
        }
        
        log.info("Reseteados {} slides que estaban en PROCESSING", stuck.size());
        return stuck.size();
    }
    
    /**
     * Obtiene el progreso actual de la sincronización.
     */
    public SyncProgressStats getSyncProgress() {
        long pending = processingStatusRepo.countByStatus(SlideProcessingStatus.ProcessingStatus.PENDING);
        long processing = processingStatusRepo.countByStatus(SlideProcessingStatus.ProcessingStatus.PROCESSING);
        long completed = processingStatusRepo.countByStatus(SlideProcessingStatus.ProcessingStatus.COMPLETED);
        long failed = processingStatusRepo.countByStatus(SlideProcessingStatus.ProcessingStatus.FAILED);
        long total = pending + processing + completed + failed;
        
        double completionPercentage = total > 0 ? (completed * 100.0 / total) : 0;
        
        return SyncProgressStats.builder()
            .totalSlides(total)
            .pending(pending)
            .processing(processing)
            .completed(completed)
            .failed(failed)
            .completionPercentage(completionPercentage)
            .build();
    }


    /**
     * Detecta slides COMPLETED cuyo write_date en la réplica supera el odoo_write_date guardado
     * y los resetea a PENDING para que syncAllSlides los vuelva a procesar.
     */
    @Transactional("processedTransactionManager")
    public int resetOutdatedSlides() {
        List<Integer> completedIds = processingStatusRepo.findCompletedSlideIds();
        if (completedIds.isEmpty()) return 0;

        List<Integer> outdatedIds = new ArrayList<>();
        int chunkSize = 500;

        for (int i = 0; i < completedIds.size(); i += chunkSize) {
            List<Integer> chunk = completedIds.subList(i, Math.min(i + chunkSize, completedIds.size()));

            // Fechas en BD procesada
            Map<Integer, java.time.LocalDateTime> processedDates = new HashMap<>();
            for (Object[] row : processedSlideRepo.findWriteDatesByIds(chunk)) {
                processedDates.put(((Number) row[0]).intValue(), (java.time.LocalDateTime) row[1]);
            }

            // Fechas en réplica (nativeQuery devuelve Timestamp)
            Map<Integer, java.time.LocalDateTime> replicaDates = new HashMap<>();
            for (Object[] row : replicaSlideRepo.findWriteDatesByIds(chunk)) {
                Integer id = ((Number) row[0]).intValue();
                java.time.LocalDateTime wd = null;
                if (row[1] instanceof java.sql.Timestamp ts) {
                    wd = ts.toLocalDateTime();
                } else if (row[1] instanceof java.time.LocalDateTime ldt) {
                    wd = ldt;
                }
                if (wd != null) replicaDates.put(id, wd);
            }

            for (Integer id : chunk) {
                java.time.LocalDateTime processed = processedDates.get(id);
                java.time.LocalDateTime replica = replicaDates.get(id);
                if (replica != null && (processed == null || replica.isAfter(processed))) {
                    outdatedIds.add(id);
                }
            }
        }

        if (!outdatedIds.isEmpty()) {
            for (int i = 0; i < outdatedIds.size(); i += chunkSize) {
                List<Integer> chunk = outdatedIds.subList(i, Math.min(i + chunkSize, outdatedIds.size()));
                processingStatusRepo.updateStatusByIds(
                    SlideProcessingStatus.ProcessingStatus.PENDING, chunk);
            }
            log.info("resetOutdatedSlides: {} slides reseteados a PENDING por cambios en Odoo", outdatedIds.size());
        }

        return outdatedIds.size();
    }

    /**
     * Elimina de la BD procesada los slides que ya no están en el conjunto relevante
     * (activos, publicados, en canal publicado). Usa IDs para evitar cargar htmlContent.
     */
    @Transactional("processedTransactionManager")
    public int purgeExcludedSlides() {
        List<Integer> relevantIds = replicaSlideRepo.findActiveSlideIds();
        Set<Integer> relevantSet = new HashSet<>(relevantIds);

        List<Integer> processedIds = processedSlideRepo.findAllIds();
        int deleted = 0;
        for (Integer id : processedIds) {
            if (!relevantSet.contains(id)) {
                imageRepo.deleteBySlideId(id);
                processedSlideRepo.deleteById(id);
                processingStatusRepo.deleteById(id);
                deleted++;
            }
        }
        log.info("purgeExcludedSlides: {} slides eliminados de la BD procesada", deleted);
        return deleted;
    }
}