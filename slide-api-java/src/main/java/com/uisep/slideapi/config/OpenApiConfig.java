package com.uisep.slideapi.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.*;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("UISEP Slide API")
                .version("1.3.0")
                .description("""
                    API REST para servir contenido educativo (slides) de Odoo 16 a la app movil UISEP.

                    ## Autenticacion
                    Todos los endpoints requieren el header `X-API-Key`.
                    Excluidos: `/api/v1/admin/health`, `/swagger-ui/**`, `/api-docs/**`, `/actuator/**`.

                    ## Fuentes de datos
                    - **Replica Odoo** (postgres-replica-i4s8o8000kc040cgwcwowwwc) - solo lectura
                    - **Supabase** (supabase-db-h4csg4oo4k08wc848008gcgw, schema slide_api) - lectura/escritura

                    ## Flujo de sincronizacion
                    Odoo produccion -> Replica PostgreSQL -> POST /admin/sync -> BD procesada -> App movil

                    ## Novedades v1.3.0
                    - POST /admin/sync/reset-outdated: detecta slides modificados en Odoo y los re-encola
                    - GET /admin/sync/log: historial de slides creados/actualizados por sync
                    - GET /admin/sync/log/runs: resumen de ejecuciones de sync por syncRunId
                    - Descarga automatica de archivos binarios desde Odoo (PDFs, presentaciones)
                    - Paginacion en listados de slides y canales
                    - Autenticacion via X-API-Key
                    """)
                .contact(new Contact()
                    .name("Vicepresidencia de TI - Universidad ISEP")
                    .email("iallamadas@universidadisep.com")))
            .addServersItem(new Server()
                .url("https://slides.universidadisep.com")
                .description("Produccion"))
            .addServersItem(new Server()
                .url("http://localhost:8080")
                .description("Local"))
            .components(new Components()
                .addSecuritySchemes("ApiKeyAuth", new SecurityScheme()
                    .type(SecurityScheme.Type.APIKEY)
                    .in(SecurityScheme.In.HEADER)
                    .name("X-API-Key")
                    .description("API key requerida en todos los endpoints /api/**")))
            .security(List.of(new SecurityRequirement().addList("ApiKeyAuth")))
            .tags(List.of(
                new Tag().name("Slides").description("Consulta de slides educativos procesados"),
                new Tag().name("Channels").description("Canales/cursos de Odoo"),
                new Tag().name("Images").description("Imagenes extraidas de slides"),
                new Tag().name("Files").description("Archivos binarios - PDF y presentaciones"),
                new Tag().name("Odoo Proxy").description("Proxy de consulta directa a Odoo produccion"),
                new Tag().name("Admin").description("Sincronizacion, log de cambios y mantenimiento")
            ));
    }
}
