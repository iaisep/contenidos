#!/bin/bash
# ================================================
# Script de Post-Deploy para Slide API en Coolify
# ================================================
# Ejecutar después de cada deploy de Coolify para:
# 1. Conectar el contenedor a las redes necesarias
# 2. Reiniciar el contenedor para aplicar cambios
# ================================================

set -e

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}=== Slide API Post-Deploy Script ===${NC}"

# Encontrar el contenedor más reciente de slide-api
CONTAINER=$(docker ps -a --filter "name=uwg44ossogk4cs8888kgwosc" --format "{{.Names}}" | head -1)

if [ -z "$CONTAINER" ]; then
    echo -e "${RED}Error: No se encontró el contenedor de slide-api${NC}"
    exit 1
fi

echo -e "${GREEN}Contenedor encontrado: $CONTAINER${NC}"

# Redes requeridas
SUPABASE_NETWORK="h4csg4oo4k08wc848008gcgw"
REPLICA_NETWORK="i4s8o8000kc040cgwcwowwwc"

# Conectar a la red de Supabase
echo -e "${YELLOW}Conectando a red Supabase ($SUPABASE_NETWORK)...${NC}"
docker network connect $SUPABASE_NETWORK $CONTAINER 2>/dev/null && \
    echo -e "${GREEN}✓ Conectado a Supabase${NC}" || \
    echo -e "${YELLOW}Ya conectado a Supabase${NC}"

# Conectar a la red de la Réplica
echo -e "${YELLOW}Conectando a red Réplica ($REPLICA_NETWORK)...${NC}"
docker network connect $REPLICA_NETWORK $CONTAINER 2>/dev/null && \
    echo -e "${GREEN}✓ Conectado a Réplica${NC}" || \
    echo -e "${YELLOW}Ya conectado a Réplica${NC}"

# Reiniciar el contenedor para aplicar cambios de red
echo -e "${YELLOW}Reiniciando contenedor...${NC}"
docker restart $CONTAINER

# Esperar a que el contenedor esté listo
echo -e "${YELLOW}Esperando a que la aplicación esté lista...${NC}"
sleep 20

# Verificar health
echo -e "${YELLOW}Verificando health...${NC}"
CONTAINER_IP=$(docker inspect $CONTAINER --format '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' | awk '{print $1}')

if curl -s "http://$CONTAINER_IP:8080/actuator/health" | grep -q '"status":"UP"'; then
    echo -e "${GREEN}✓ Aplicación healthy!${NC}"
    
    # Mostrar estado de conexiones
    echo -e "\n${GREEN}=== Estado de las conexiones ===${NC}"
    curl -s "http://$CONTAINER_IP:8080/actuator/health" | python3 -m json.tool 2>/dev/null || \
        curl -s "http://$CONTAINER_IP:8080/actuator/health"
else
    echo -e "${RED}✗ Aplicación no responde correctamente${NC}"
    echo -e "${YELLOW}Verificando logs...${NC}"
    docker logs $CONTAINER --tail 30
fi

echo -e "\n${GREEN}=== Script completado ===${NC}"
