package com.uisep.slideapi.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.*;
import java.util.List;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI slideApiOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("UISEP Slide API")
                .version("1.0.0")
                .description("""
                    ## API de contenidos educativos — Universidad ISEP
                    
                    Intermediario entre la app móvil UISEP y Odoo 16. Sirve los slides \
                    (presentaciones, PDFs y videos) de los cursos de eLearning, con imágenes \
                    ya extraídas del HTML para evitar transferir Base64 al cliente.
                    
                    ### Arquitectura
                    ```
                    App Móvil ──► slide-api (Java/Spring) ──► Supabase DB (slides procesados)
                                                          ──► Réplica PostgreSQL (Odoo, solo lectura)
                    ```
                    
                    ### Flujo de sincronización
                    1. `POST /api/v1/admin/sync` — lee la réplica Odoo y procesa los slides
                    2. Las imágenes Base64 embebidas en HTML se extraen y guardan por separado
                    3. El HTML se entrega limpio; las imágenes se sirven por `/api/v1/images/{slideId}/{filename}`
                    
                    ### Filtro de slides relevantes
                    Solo se sincronizan slides que cumplan **los 3 criterios**:
                    - `slide.active = true`
                    - `slide.is_published = true`
                    - `channel.active = true AND channel.is_published = true`
                    
                    ### Notas de rendimiento
                    - Los endpoints de **listado** (`GET /slides`, `GET /channels`) \
                    no incluyen `htmlContent` — usar `GET /slides/{id}` para obtenerlo.
                    - La sincronización completa puede tardar varios minutos \
                    dependiendo del número de slides pendientes.
                    """)
                .contact(new Contact()
                    .name("Vicepresidencia de TI — Universidad ISEP")
                    .email("iallamadas@universidadisep.com"))
                .license(new License().name("Uso interno UISEP")))
            .servers(List.of(
                new Server()
                    .url("https://slides.universidadisep.com")
                    .description("Producción"),
                new Server()
                    .url("http://localhost:8080")
                    .description("Local / desarrollo")))
            .components(new Components());
    }
}
