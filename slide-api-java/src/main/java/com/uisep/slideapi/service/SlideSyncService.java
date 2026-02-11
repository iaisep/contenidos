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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * 
     * @param activeOnly Si true, solo procesa slides activos
     * @return Resultado de la sincronización
     */
    @Transactional("processedTransactionManager")
    public SyncResult syncAllSlides(boolean activeOnly) {
        LocalDateTime startTime = LocalDateTime.now();
        long startMs = System.currentTimeMillis();
        
        List<MigrationResult> migrationResults = new ArrayList<>();
        int created = 0, updated = 0, failed = 0, totalProcessed = 0;
        long totalOriginalSize = 0, totalProcessedSize = 0;
        
        // Cargar canales para enriquecer nombres
        Map<Integer, String> channelNames = loadChannelNames();
        
        // 1. Inicializar tracking si es necesario
        initializeTracking();
        
        // 2. Resetear slides que quedaron en PROCESSING (por interrupciones previas)
        List<SlideProcessingStatus> stuckInProcessing = processingStatusRepo.findByStatus(
            SlideProcessingStatus.ProcessingStatus.PROCESSING);
        for (SlideProcessingStatus stuck : stuckInProcessing) {
            stuck.setStatus(SlideProcessingStatus.ProcessingStatus.PENDING);
            processingStatusRepo.save(stuck);
        }
        
        // 3. Obtener slides pendientes o fallidos
        List<Integer> slideIds = processingStatusRepo.findPendingSlideIds();
        int totalSlides = slideIds.size();
        
        log.info("Iniciando sincronización: {} slides pendientes...", totalSlides);
        
        for (Integer slideId : slideIds) {
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
                    failed++;
                    continue;
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
                
                migrationResults.add(result);
                
                if ("CREATED".equals(result.getStatus())) created++;
                else if ("UPDATED".equals(result.getStatus())) updated++;
                
                totalOriginalSize += result.getOriginalSize() != null ? result.getOriginalSize() : 0;
                totalProcessedSize += result.getNewSize() != null ? result.getNewSize() : 0;
                
            } catch (Exception e) {
                log.error("Error procesando slide {}: {}", slideId, e.getMessage(), e);
                
                // Marcar como fallido
                trackingStatus.setStatus(SlideProcessingStatus.ProcessingStatus.FAILED);
                trackingStatus.setFailedAt(LocalDateTime.now());
                trackingStatus.setErrorMessage(e.getMessage() != null ? 
                    e.getMessage().substring(0, Math.min(1999, e.getMessage().length())) : "Error desconocido");
                trackingStatus.setRetryCount(trackingStatus.getRetryCount() + 1);
                processingStatusRepo.saveAndFlush(trackingStatus);
                
                failed++;
                migrationResults.add(MigrationResult.builder()
                    .slideId(slideId)
                    .slideName("Unknown")
                    .status("FAILED")
                    .message(e.getMessage())
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
     * Procesa un slide individual: extrae imágenes y guarda en BD procesada.
     */
    private MigrationResult processSlide(SlideSlideReplica replica, Map<Integer, String> channelNames) {
        String slideName = replica.getNameEs();
        String htmlContent = replica.getHtmlContentEs();
        long originalSize = htmlContent != null ? htmlContent.length() : 0;
        
        // Verificar si ya existe y si necesita actualización
        Optional<ProcessedSlide> existing = processedSlideRepo.findById(replica.getId());
        boolean isNew = existing.isEmpty();
        boolean needsUpdate = !isNew && replica.getWriteDate() != null && 
            (existing.get().getOdooWriteDate() == null || 
             replica.getWriteDate().isAfter(existing.get().getOdooWriteDate()));
        
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
        processed.setId(replica.getId());
        processed.setChannelId(replica.getChannelId());
        processed.setChannelName(channelNames.get(replica.getChannelId()));
        processed.setName(slideName);
        processed.setSlideType(replica.getSlideType());
        processed.setHtmlContent(processedHtml);
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
        processed.setMigrationStatus(imagesExtracted > 0 ? "COMPLETED" : "NO_MIGRATION_NEEDED");
        
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
                
                // Calcular estadísticas de slides
                List<ProcessedSlide> channelSlides = processedSlideRepo.findByChannelId(replica.getId());
                channel.setSlideCount(channelSlides.size());
                channel.setTotalSizeBytes(channelSlides.stream()
                    .mapToLong(s -> s.getProcessedSizeBytes() != null ? s.getProcessedSizeBytes() : 0)
                    .sum());
                
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
        // Estadísticas de la réplica
        long totalSlides = replicaSlideRepo.count();
        long activeSlides = replicaSlideRepo.findByActiveTrue().size();
        long inactiveSlides = totalSlides - activeSlides;
        
        Object[] base64Stats = replicaSlideRepo.getBase64Statistics();
        long slidesWithBase64 = base64Stats[0] != null ? ((Number) base64Stats[0]).longValue() : 0;
        long base64Size = base64Stats[1] != null ? ((Number) base64Stats[1]).longValue() : 0;
        
        // Estadísticas de procesados
        Object[] migrationStats = processedSlideRepo.getMigrationStatistics();
        long totalOriginalSize = migrationStats[1] != null ? ((Number) migrationStats[1]).longValue() : 0;
        long totalProcessedSize = migrationStats[2] != null ? ((Number) migrationStats[2]).longValue() : 0;
        long totalImagesExtracted = migrationStats[3] != null ? ((Number) migrationStats[3]).longValue() : 0;
        
        long savedBytes = totalOriginalSize - totalProcessedSize;
        double savingsPercentage = totalOriginalSize > 0 
            ? (savedBytes * 100.0 / totalOriginalSize) 
            : 0;
        
        // Acciones pendientes
        List<SlideSlideReplica> inactiveList = replicaSlideRepo.findByActiveFalse();
        long inactiveSize = inactiveList.stream()
            .mapToLong(SlideSlideReplica::getHtmlContentSizeBytes)
            .sum();
        
        PendingActions pending = PendingActions.builder()
            .inactiveSlidesToDelete((long) inactiveList.size())
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
}
