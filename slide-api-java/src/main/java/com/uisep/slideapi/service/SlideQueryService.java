package com.uisep.slideapi.service;

import com.uisep.slideapi.dto.SlideDTO.*;
import com.uisep.slideapi.entity.processed.ProcessedChannel;
import com.uisep.slideapi.entity.processed.ProcessedSlide;
import com.uisep.slideapi.entity.processed.SlideImage;
import com.uisep.slideapi.repository.processed.ProcessedChannelRepository;
import com.uisep.slideapi.repository.processed.ProcessedSlideRepository;
import com.uisep.slideapi.repository.processed.SlideImageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Servicio para consultar datos procesados (para la app).
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(value = "processedTransactionManager", readOnly = true)
public class SlideQueryService {
    
    private final ProcessedSlideRepository slideRepo;
    private final ProcessedChannelRepository channelRepo;
    private final SlideImageRepository imageRepo;
    
    /**
     * Obtiene un slide por ID con sus imágenes.
     */
    public Optional<SlideResponse> getSlideById(Integer id) {
        return slideRepo.findById(id)
            .map(this::toSlideResponse);
    }
    
    /**
     * Lista todos los slides activos y publicados.
     */
    public List<SlideListItem> getPublishedSlides() {
        return slideRepo.findByActiveTrueAndIsPublishedTrue().stream()
            .map(this::toListItem)
            .collect(Collectors.toList());
    }
    
    /**
     * Lista slides por canal.
     */
    public List<SlideListItem> getSlidesByChannel(Integer channelId) {
        return slideRepo.findByChannelId(channelId).stream()
            .map(this::toListItem)
            .collect(Collectors.toList());
    }
    
    /**
     * Busca slides por nombre.
     */
    public List<SlideListItem> searchSlides(String query) {
        return slideRepo.searchByName(query).stream()
            .map(this::toListItem)
            .collect(Collectors.toList());
    }
    
    /**
     * Lista todos los canales publicados.
     */
    public List<ChannelResponse> getPublishedChannels() {
        return channelRepo.findByActiveTrueAndIsPublishedTrue().stream()
            .map(this::toChannelResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * Obtiene un canal por ID.
     */
    public Optional<ChannelResponse> getChannelById(Integer id) {
        return channelRepo.findById(id)
            .map(this::toChannelResponse);
    }
    
    /**
     * Obtiene una imagen por slide ID y filename.
     */
    public Optional<SlideImage> getImage(Integer slideId, String filename) {
        return imageRepo.findBySlideIdOrderByImageIndex(slideId).stream()
            .filter(img -> filename.equals(img.getOriginalFilename()))
            .findFirst();
    }
    
    /**
     * Obtiene una imagen por ID.
     */
    public Optional<SlideImage> getImageById(Long imageId) {
        return imageRepo.findById(imageId);
    }
    
    /**
     * Lista imágenes de un slide.
     */
    public List<ImageInfo> getSlideImages(Integer slideId) {
        return imageRepo.findBySlideIdOrderByImageIndex(slideId).stream()
            .map(this::toImageInfo)
            .collect(Collectors.toList());
    }
    
    // ======== Mappers ========
    
    private SlideResponse toSlideResponse(ProcessedSlide slide) {
        List<ImageInfo> images = imageRepo.findBySlideIdOrderByImageIndex(slide.getId()).stream()
            .map(this::toImageInfo)
            .collect(Collectors.toList());
        
        return SlideResponse.builder()
            .id(slide.getId())
            .channelId(slide.getChannelId())
            .channelName(slide.getChannelName())
            .name(slide.getName())
            .slideType(slide.getSlideType())
            .htmlContent(slide.getHtmlContent())
            .description(slide.getDescription())
            .active(slide.getActive())
            .isPublished(slide.getIsPublished())
            .totalViews(slide.getTotalViews())
            .originalSizeBytes(slide.getOriginalSizeBytes())
            .processedSizeBytes(slide.getProcessedSizeBytes())
            .imagesExtracted(slide.getImagesExtracted())
            .odooCreateDate(slide.getOdooCreateDate())
            .lastSyncedAt(slide.getLastSyncedAt())
            .migrationStatus(slide.getMigrationStatus())
            .images(images)
            .build();
    }
    
    private SlideListItem toListItem(ProcessedSlide slide) {
        return SlideListItem.builder()
            .id(slide.getId())
            .channelId(slide.getChannelId())
            .channelName(slide.getChannelName())
            .name(slide.getName())
            .slideType(slide.getSlideType())
            .active(slide.getActive())
            .isPublished(slide.getIsPublished())
            .totalViews(slide.getTotalViews())
            .originalSizeBytes(slide.getOriginalSizeBytes())
            .processedSizeBytes(slide.getProcessedSizeBytes())
            .migrationStatus(slide.getMigrationStatus())
            .build();
    }
    
    private ChannelResponse toChannelResponse(ProcessedChannel channel) {
        return ChannelResponse.builder()
            .id(channel.getId())
            .name(channel.getName())
            .description(channel.getDescription())
            .active(channel.getActive())
            .isPublished(channel.getIsPublished())
            .totalViews(channel.getTotalViews())
            .slideCount(channel.getSlideCount())
            .totalSizeBytes(channel.getTotalSizeBytes())
            .odooCreateDate(channel.getOdooCreateDate())
            .lastSyncedAt(channel.getLastSyncedAt())
            .build();
    }
    
    private ImageInfo toImageInfo(SlideImage image) {
        return ImageInfo.builder()
            .id(image.getId())
            .filename(image.getOriginalFilename())
            .mimeType(image.getMimeType())
            .sizeBytes(image.getSizeBytes())
            .publicUrl(image.getPublicUrl())
            .build();
    }
}
