#!/usr/bin/env bash
# midori-wallet — Pod con su PROPIA PostgreSQL dedicada (no arca). A
# diferencia de otras apps de Astian en este servidor, midori-wallet pidió
# explícitamente una RDBMS propia, aislada, no compartida.
#
# Aprovecha el propio aprovisionamiento del repo (server/scripts/bootstrap.mjs):
# crea dos roles (owner para DDL/migraciones, app de mínimo privilegio para
# runtime) y aplica los grants exactos tabla por tabla. No se reinventa esa
# lógica a mano con SQL suelto.
set -Eeuo pipefail

log() { printf '[setup] %s\n' "$*"; }
die() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}
rand_hex() { openssl rand -hex 32; }

# === Ajustar por proyecto ===
SERVICE_NAME="midori-wallet"
IMAGE_NAME="localhost/${SERVICE_NAME}:latest"
PG_IMAGE="docker.io/library/postgres:17-alpine"

DB_NAME="midori_wallet"
PUBLIC_ORIGIN="https://midoriwallet.com"

PUBLISH_ADDR="127.0.0.1"
PUBLISH_PORT="2006" # siguiente libre confirmado contra `podman ps -a` (2026-11-04)
CONTAINER_PORT="8080"
# ============================

CONTAINER_DIR="$(readlink -f "$(dirname "$0")")"
PROJECT_DIR="$(readlink -f "${CONTAINER_DIR}/..")"

QUADLET_PATH="${XDG_CONFIG_HOME:-${HOME}/.config}/containers/systemd/${SERVICE_NAME}"
VOLUMES_PATH="${HOME}/volume/${SERVICE_NAME}"

ENV_FILE="${CONTAINER_DIR}/${SERVICE_NAME}.env"                     # runtime (rol app, DATABASE_URL de mínimo privilegio)
OWNER_ENV_FILE="${CONTAINER_DIR}/${SERVICE_NAME}-owner.env"          # solo migraciones/mantenimiento (rol owner)
PG_ADMIN_ENV_FILE="${CONTAINER_DIR}/${SERVICE_NAME}-postgres-admin.env" # superusuario postgres, solo para bootstrap inicial

DB_VOLUME="${SERVICE_NAME}-db"

install -d -m 700 "${QUADLET_PATH}"

## ================= ##
##    Volumen DB     ##
## ================= ##
# postgres:17-alpine corre internamente como uid/gid 70 (usuario "postgres"
# del paquete Alpine, distinto del 999 de Debian). Confirmar con
# `podman run --rm postgres:17-alpine id postgres` si la imagen cambia.
[ -d "${VOLUMES_PATH}/${DB_VOLUME}" ] ||
    podman unshare install -d -m 700 -o 70 -g 70 "${VOLUMES_PATH}/${DB_VOLUME}"
cat >"${QUADLET_PATH}/${DB_VOLUME}.volume" <<EOF
[Volume]
VolumeName=${DB_VOLUME}
Type=none
Device=${VOLUMES_PATH}/${DB_VOLUME}
Options=bind,rw
Copy=false
EOF

## ============= ##
##    Network    ##
## ============= ##
# Red propia y simple (no es el patrón *-psql de shared.sh: esa es
# específicamente para conectarse a arca. Esta DB es privada del Pod).
cat >"${QUADLET_PATH}/${SERVICE_NAME}.network" <<EOF
[Network]
NetworkName=${SERVICE_NAME}-net
Driver=bridge
EOF

## ========= ##
##    Pod    ##
## ========= ##
cat >"${QUADLET_PATH}/${SERVICE_NAME}.pod" <<EOF
[Unit]
Description=${SERVICE_NAME} Pod

[Pod]
PodName=${SERVICE_NAME}
ServiceName=${SERVICE_NAME}
Network=${SERVICE_NAME}.network
ExitPolicy=stop
PublishPort=${PUBLISH_ADDR}:${PUBLISH_PORT}:${CONTAINER_PORT}
PodmanArgs=--cpus=2 --memory=1408m

[Service]
Restart=always
TimeoutStartSec=900

[Install]
WantedBy=default.target
EOF

## ========================================= ##
##    Secreto: superusuario de PostgreSQL    ##
## ========================================= ##
if [ -s "${PG_ADMIN_ENV_FILE}" ]; then
    PG_SUPERUSER_PASSWORD="$(grep '^POSTGRES_PASSWORD=' "${PG_ADMIN_ENV_FILE}" | cut -d= -f2-)"
else
    PG_SUPERUSER_PASSWORD="$(rand_hex)"
fi
[ -n "${PG_SUPERUSER_PASSWORD}" ] || die "No se pudo generar/leer la contraseña del superusuario de PostgreSQL."
printf 'POSTGRES_PASSWORD=%s\n' "${PG_SUPERUSER_PASSWORD}" >"${PG_ADMIN_ENV_FILE}"
chmod 600 "${PG_ADMIN_ENV_FILE}"

## ============================= ##
##    Container: PostgreSQL      ##
## ============================= ##
cat >"${QUADLET_PATH}/${SERVICE_NAME}-db.container" <<EOF
[Container]
Pod=${SERVICE_NAME}.pod
ContainerName=${SERVICE_NAME}-db
Image=${PG_IMAGE}
EnvironmentFile=${PG_ADMIN_ENV_FILE}
AutoUpdate=registry
Pull=newer

Environment=POSTGRES_USER=postgres
Environment=POSTGRES_INITDB_ARGS=--auth-host=scram-sha-256 --auth-local=scram-sha-256

Volume=${DB_VOLUME}.volume:/var/lib/postgresql/data:Z

Memory=768m
PidsLimit=256
PodmanArgs=--cpus=1

HealthCmd=pg_isready -U postgres
HealthInterval=10s
HealthRetries=5
HealthStartPeriod=20s

[Service]
Restart=always
RestartSec=5s
TimeoutStartSec=900
EOF

## ================= ##
##    Imagen app     ##
## ================= ##
log "Construyendo ${IMAGE_NAME}..."
podman build --pull=newer -t "${IMAGE_NAME}" \
    -f "${CONTAINER_DIR}/containerfile" \
    --ignorefile "${CONTAINER_DIR}/.containerignore" \
    "${PROJECT_DIR}"

## ======================================= ##
##    Arrancar SOLO la DB primero          ##
## ======================================= ##
systemctl --user daemon-reload
log "Arrancando ${SERVICE_NAME}-db (crea el Pod implícitamente)..."
# "enable" no aplica a contenedores miembro de un Pod (no tienen [Install];
# systemd los marca "transient or generated"). Solo "start": los arrastra
# el Requires=/After= del *-app.container, que sí está enabled más abajo.
systemctl --user start "${SERVICE_NAME}-db.service"

log "Esperando a que PostgreSQL esté listo..."
for i in $(seq 1 30); do
    podman exec "${SERVICE_NAME}-db" pg_isready -U postgres >/dev/null 2>&1 && break
    [ "${i}" -lt 30 ] || die "PostgreSQL no respondió a tiempo."
    sleep 2
done

## ======================================================= ##
##    Aprovisionamiento (una sola vez): bootstrap.mjs        ##
## ======================================================= ##
if [ -s "${ENV_FILE}" ] && grep -q '^DATABASE_URL=' "${ENV_FILE}"; then
    log "${ENV_FILE} ya existe con DATABASE_URL; NO se vuelve a correr bootstrap.mjs (evita tocar una base ya provisionada)."
else
    log "Primer arranque: aprovisionando roles/esquema con bootstrap.mjs..."
    BOOTSTRAP_CONTAINER="${SERVICE_NAME}-bootstrap-$$"
    TMP_DIR="$(mktemp -d)"
    trap 'podman rm -f "${BOOTSTRAP_CONTAINER}" >/dev/null 2>&1 || true; rm -rf "${TMP_DIR}"' EXIT

    podman run --name "${BOOTSTRAP_CONTAINER}" \
        --pod "${SERVICE_NAME}" \
        --entrypoint node \
        -e "MIDORI_PG_ADMIN_URL=postgresql://postgres:${PG_SUPERUSER_PASSWORD}@127.0.0.1:5432/postgres" \
        -e "MIDORI_DATABASE_NAME=${DB_NAME}" \
        "${IMAGE_NAME}" scripts/bootstrap.mjs ||
        die "bootstrap.mjs falló; revisar logs: podman logs ${BOOTSTRAP_CONTAINER}"

    # bootstrap.mjs escribe relativo a su propia ubicacion
    # (scripts/bootstrap.mjs -> ../.env), que en esta imagen es
    # /app/server/.env (server/ y packages/ son hermanos bajo /app; ver
    # containerfile).
    podman cp "${BOOTSTRAP_CONTAINER}:/app/server/.env" "${TMP_DIR}/app.env"
    podman cp "${BOOTSTRAP_CONTAINER}:/app/server/.env.migrations" "${TMP_DIR}/owner.env"
    podman rm -f "${BOOTSTRAP_CONTAINER}" >/dev/null 2>&1 || true
    trap 'rm -rf "${TMP_DIR}"' EXIT

    runtime_database_url="$(grep '^DATABASE_URL=' "${TMP_DIR}/app.env" | cut -d= -f2- | sed 's/^"//;s/"$//')"
    owner_database_url="$(grep '^DATABASE_URL=' "${TMP_DIR}/owner.env" | cut -d= -f2- | sed 's/^"//;s/"$//')"
    [ -n "${runtime_database_url}" ] && [ -n "${owner_database_url}" ] ||
        die "bootstrap.mjs no devolvió las URLs esperadas."

    cat >"${ENV_FILE}" <<EOF
NODE_ENV=production
DATABASE_URL=${runtime_database_url}
MIDORI_NETWORK=base
MIDORI_CHAIN_ID=8453
MIDORI_PUBLIC_ORIGIN=${PUBLIC_ORIGIN}
# 0.0.0.0, NO 127.0.0.1: el Pod usa red bridge propia (no slirp4netns), así
# que PublishPort llega a la IP del bridge del contenedor, no a su loopback.
# Con host=127.0.0.1 el proceso acepta el handshake pero resetea la
# conexión al no reconocer esa interfaz como propia (bug real, encontrado
# en el primer despliegue). El aislamiento real ya lo da
# PublishPort=127.0.0.1:... en el host, no hace falta repetirlo adentro.
MIDORI_API_HOST=0.0.0.0
MIDORI_API_PORT=${CONTAINER_PORT}
MIDORI_DB_POOL_SIZE=10
MIDORI_RPC_URL=https://mainnet.base.org
MIDORI_TRANSACTIONS_ENABLED=false
MIDORI_SPONSORSHIP_ENABLED=false
MIDORI_DEPLOYMENT=manual
MIDORI_CHALLENGE_SECONDS=120
MIDORI_SESSION_SECONDS=86400
MIDORI_RATE_LIMIT=60
MIDORI_MAX_DEVICES=100
MIDORI_WORKER_INTERVAL_MS=60000
MIDORI_NATIVE_ORIGINS=https://localhost,app://localhost
MIDORI_ALLOWED_ORIGINS=
MIDORI_INDEX_ENABLED=false
EOF
    chmod 600 "${ENV_FILE}"

    # A proposito NO se monta dentro del contenedor (ni como volumen de
    # archivo ni via EnvironmentFile): el mapeo de UID de Podman rootless
    # hace que un archivo 0600 del usuario del host quede ilegible para el
    # usuario "node" dentro del contenedor (y viceversa si se hace
    # "podman unshare chown" para arreglarlo). migrate.mjs ya acepta la URL
    # del owner directamente por variable de entorno
    # (MIDORI_MIGRATION_DATABASE_URL tiene prioridad sobre DATABASE_URL) —
    # ver la instrucción de mantenimiento al final de este script.
    printf 'DATABASE_URL=%s\n' "${owner_database_url}" >"${OWNER_ENV_FILE}"
    chmod 600 "${OWNER_ENV_FILE}"

    rm -rf "${TMP_DIR}"
    trap - EXIT
    log "Provisionado OK. Runtime en ${ENV_FILE} (0600), owner/migraciones en ${OWNER_ENV_FILE} (0600)."
fi

## ======================================= ##
##    Container: App (server.js+worker.js) ##
## ======================================= ##
# Nombrar "${SERVICE_NAME}-app.container" (no "${SERVICE_NAME}.container"):
# el Pod ya usa ServiceName=${SERVICE_NAME}, y un .container con el mismo
# nombre generaría la misma unidad systemd (bug ya documentado en el
# patrón de Astian). ContainerName sí puede quedar igual.
cat >"${QUADLET_PATH}/${SERVICE_NAME}-app.container" <<EOF
[Unit]
Requires=${SERVICE_NAME}-db.service
After=${SERVICE_NAME}-db.service

[Container]
Pod=${SERVICE_NAME}.pod
ContainerName=${SERVICE_NAME}
Image=${IMAGE_NAME}
EnvironmentFile=${ENV_FILE}
AutoUpdate=local
Pull=newer

ReadOnly=true
Tmpfs=/tmp:rw,nosuid,nodev,size=32m

# App 100% sin estado (todo vive en PostgreSQL, ver server/lib/storage.js):
# no hace falta ninguna capability — ni siquiera las que Laravel necesita
# para chown de storage/. Node ya corre como el usuario "node" (no root)
# desde el propio containerfile.
DropCapability=all
NoNewPrivileges=true

Memory=512m
PidsLimit=256
PodmanArgs=--cpus=1

HealthCmd=wget -qO- http://127.0.0.1:${CONTAINER_PORT}/healthz
HealthInterval=30s
HealthRetries=5
HealthStartPeriod=20s
Notify=healthy

[Service]
Restart=always
RestartSec=5s
TimeoutStartSec=900

[Install]
WantedBy=default.target
EOF

## ================================= ##
##    Backups periódicos (timer)     ##
## ================================= ##
SYSTEMD_USER_PATH="${XDG_CONFIG_HOME:-${HOME}/.config}/systemd/user"
install -d -m 700 "${SYSTEMD_USER_PATH}"
sed "s|__BACKUP_SCRIPT__|${CONTAINER_DIR}/backup.sh|g" \
    "${CONTAINER_DIR}/systemd/${SERVICE_NAME}-backup.service" \
    >"${SYSTEMD_USER_PATH}/${SERVICE_NAME}-backup.service"
install -m 644 "${CONTAINER_DIR}/systemd/${SERVICE_NAME}-backup.timer" \
    "${SYSTEMD_USER_PATH}/${SERVICE_NAME}-backup.timer"

systemctl --user daemon-reload
# Las unidades generadas por Quadlet (Pod/.container) no se "enable": systemd
# las marca "transient or generated". El [Install] del .pod ya lo resuelve
# el propio generador en daemon-reload; a runtime solo hace falta "start".
systemctl --user start "${SERVICE_NAME}-app.service"
systemctl --user enable --now "${SERVICE_NAME}-backup.timer"

log "Listo. Verificar:"
printf '    podman ps --filter pod=%s\n' "${SERVICE_NAME}"
printf '    curl -s http://127.0.0.1:%s/healthz\n' "${PUBLISH_PORT}"
printf '    curl -s http://127.0.0.1:%s/readyz\n' "${PUBLISH_PORT}"
printf '\nFalta (manual, fuera de este script):\n'
printf '    1. Apuntar el DNS de %s a este servidor.\n' "${PUBLIC_ORIGIN#https://}"
printf '    2. Instalar el vhost: container/nginx-reverse.conf -> /etc/nginx/vhosts.d/%s.conf\n' "${SERVICE_NAME}"
printf '    3. nginx -t && systemctl reload nginx\n'
printf '    4. sudo certbot --nginx -d %s --non-interactive --agree-tos -m <email>\n' "${PUBLIC_ORIGIN#https://}"
printf '\nMantenimiento — aplicar nuevas migraciones tras un deploy de código:\n'
printf '    OWNER_URL=$(grep "^DATABASE_URL=" %s | cut -d= -f2-)\n' "${OWNER_ENV_FILE}"
printf '    podman exec -e MIDORI_MIGRATION_DATABASE_URL="$OWNER_URL" -e MIDORI_DB_RUNTIME_ROLE=midori_wallet_app \\\n'
printf '        %s node scripts/migrate.mjs\n' "${SERVICE_NAME}"
