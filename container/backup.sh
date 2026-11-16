#!/usr/bin/env bash
# midori-wallet — backup diario de la base de datos dedicada (sin
# storage/: la app no guarda ningún archivo en disco, todo vive en
# PostgreSQL — ver server/lib/storage.js, encrypted_blobs).
#
# Cifrado en reposo (AES-256-CBC, clave propia separada de las credenciales
# de la base y del propio directorio de backups) antes de dejar nada en
# disco; solo queda el ".enc".
#
# Corre en el host como el usuario rootless de los Quadlet (nunca root).
set -Eeuo pipefail
umask 077

log() { printf '[backup] %s\n' "$*"; }
warn() { printf '[backup][warn] %s\n' "$*" >&2; }
die() {
    printf 'Error: %s\n' "$*" >&2
    exit 1
}

DB_CONTAINER="midori-wallet-db"
DB_IMAGE="docker.io/library/postgres:17-alpine"
DB_NAME="midori_wallet"
BACKUP_DIR="${HOME}/backups/midori-wallet"
BACKUP_KEY_FILE="${XDG_CONFIG_HOME:-${HOME}/.config}/midori-wallet/backup.key"
RETENTION_DAYS=30

install -d -m 700 "${BACKUP_DIR}"
install -d -m 700 "$(dirname "${BACKUP_KEY_FILE}")"

if [ ! -s "${BACKUP_KEY_FILE}" ]; then
    openssl rand -base64 32 >"${BACKUP_KEY_FILE}"
    chmod 600 "${BACKUP_KEY_FILE}"
    log "Clave de cifrado de backups generada en ${BACKUP_KEY_FILE} (guárdala; sin ella los .enc no se pueden restaurar)."
fi

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
failed=0

log_line() { printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >>"${BACKUP_DIR}/backup.log"; }
file_size() { stat -c%s "$1" 2>/dev/null || wc -c <"$1"; }

encrypt_artifact() {
    local plain="$1" enc="$1.enc"
    # AES-256-CBC, no GCM: "openssl enc" no soporta cifrados AEAD. El plano
    # ya se verificó íntegro (pg_restore --list) antes de llegar aquí.
    if openssl enc -aes-256-cbc -pbkdf2 -iter 200000 -salt \
        -pass "file:${BACKUP_KEY_FILE}" -in "${plain}" -out "${enc}" 2>/dev/null \
        && [ -s "${enc}" ]; then
        rm -f "${plain}"
        chmod 600 "${enc}"
        printf '%s' "${enc}"
        return 0
    fi
    rm -f "${enc}"
    return 1
}

db_partial="${BACKUP_DIR}/db-${timestamp}.dump.part"
db_plain="${BACKUP_DIR}/db-${timestamp}.dump"

if podman exec -u postgres "${DB_CONTAINER}" pg_dump -Fc -d "${DB_NAME}" >"${db_partial}" 2>/dev/null \
    && [ -s "${db_partial}" ]; then

    # Verificación de integridad con un contenedor postgres efímero (misma
    # imagen), sin tocar la base de producción.
    if podman run --rm -v "${BACKUP_DIR}:/backup:ro,Z" "${DB_IMAGE}" \
        pg_restore --list "/backup/$(basename "${db_partial}")" >/dev/null 2>&1; then
        mv "${db_partial}" "${db_plain}"
        if db_final="$(encrypt_artifact "${db_plain}")"; then
            checksum="$(sha256sum "${db_final}" | cut -d' ' -f1)"
            log_line "db backup=$(basename "${db_final}") size=$(file_size "${db_final}")B sha256=${checksum}"
            log "Dump aceptado y cifrado: $(basename "${db_final}")"
        else
            warn "No se pudo cifrar el dump."
            log_line "db backup FAILED (encryption)"
            failed=1
        fi
    else
        warn "El dump no superó pg_restore --list."
        rm -f "${db_partial}"
        log_line "db backup FAILED (integrity check)"
        failed=1
    fi
else
    warn "pg_dump falló; revisa que ${DB_CONTAINER} esté arriba."
    rm -f "${db_partial}"
    log_line "db backup FAILED (pg_dump)"
    failed=1
fi

if [ "${failed}" = "0" ]; then
    find "${BACKUP_DIR}" -maxdepth 1 -type f -name 'db-*.dump.enc' \
        -mtime "+${RETENTION_DAYS}" -print -delete 2>/dev/null | while IFS= read -r removed; do
        log_line "rotated $(basename "${removed}")"
    done
else
    warn "Backup de hoy con fallos; no se rota ningún backup anterior."
    die "Backup incompleto; revisa ${BACKUP_DIR}/backup.log"
fi

log "Backup completado en ${BACKUP_DIR}."
