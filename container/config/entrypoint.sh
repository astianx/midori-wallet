#!/bin/sh
# Orquestador minimo: arranca server.js (API + SPA) y worker.js (limpieza de
# expiraciones, indexado de historial, reconciliacion de pagos) como los
# unicos dos procesos de este contenedor. No se usa supervisord a proposito:
# evita instalar Python/supervisor en una imagen que solo necesita Node, y
# ambos procesos ya loguean JSON estructurado a stdout/stderr por su cuenta.
#
# Si CUALQUIERA de los dos procesos termina (exito o error), se manda
# SIGTERM al otro y el script sale: Podman/systemd reinician el contenedor
# completo (Restart=always), evitando quedar en un estado a medias (API
# arriba sin worker, o viceversa).
set -eu
cd /app/server

pids=""
cleanup() {
  for pid in $pids; do kill -TERM "$pid" 2>/dev/null || true; done
  wait 2>/dev/null || true
}
trap cleanup TERM INT

( node server.js; kill -TERM $$ ) &
pids="$pids $!"

( node worker.js; kill -TERM $$ ) &
pids="$pids $!"

wait
