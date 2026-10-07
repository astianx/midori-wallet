# Ampliación de grupos cifrados

Decisiones del 2026-10-07: conservar cifrado extremo a extremo obligatorio y preparar grupos de hasta **200.000 personas**. El propietario autoriza un protocolo nuevo y clientes Midori actualizados porque sólo existen grupos de prueba. Esa cifra sigue siendo un objetivo, no una capacidad habilitada. No diseñamos criptografía propia ni presentamos un prototipo como más seguro que OMEMO.

## Frontera actual

`chat/GroupPolicy.kt` concentra el límite operativo de 200 miembros, incluido el creador, y lo separa del objetivo futuro. Crear, invitar, consultar participantes y enviar aplican la misma política. La interfaz muestra el límite y el contador de selección; una sala que lo excede se bloquea sin recortar destinatarios ni enviar texto plano.

La implementación actual utiliza OMEMO legado: consulta afiliaciones completas (también miembros desconectados), descubre dispositivos y cifra una cabecera para cada dispositivo. Además guarda el historial completo como un documento cifrado. Subir una constante o el `max_users` de ejabberd no resuelve estos costes. La [especificación OMEMO](https://xmpp.org/extensions/xep-0384.html#group-chats) describe el tratamiento de participantes y dispositivos; nuestra implementación conserva su namespace legado, no implementa automáticamente la revisión actual de la XEP. Los [límites de salas de ejabberd](https://docs.ejabberd.im/admin/configuration/modules/#mod-muc) son independientes del presupuesto criptográfico del cliente.

## Trabajo previo a habilitar más miembros

1. Calificar una biblioteca mantenida de cifrado de grupos basada en [MLS, RFC 9420](https://www.rfc-editor.org/rfc/rfc9420.html). El laboratorio ejecutable se describe abajo. Faltan identidad, verificación de dispositivos, servicio de entrega, épocas e integración Android. MLS no garantiza 200.000 miembros ni está habilitado en la app.
2. Completar negociación explícita de capacidad y versión sobre el transporte de grupos ya separado del adaptador OMEMO legado. No migrar una sala existente silenciosamente; rechazar clientes incompatibles sin rebajar el cifrado.
3. Reemplazar la colección completa de afiliaciones de UI por consultas paginadas, conteos del servidor y búsqueda. Mantener separada la lista visible del estado criptográfico necesario para validar cada época. Nunca usar sólo la página visible como destinatarios.
4. Sustituir el documento de historial por almacenamiento cifrado incremental, índices por conversación e ID y paginación. Medir CPU, memoria, batería, almacenamiento y recuperación tras interrupción antes de elegir tamaños de página o caché.
5. Diseñar moderación, rate limits, protección de invitaciones, orden y deduplicación, cuotas y distribución de entregas en servidor. Mantener metadatos sensibles fuera de notificaciones y registros.

## Puertas de aceptación

Escalas progresivas: 20, 100, 1.000, 10.000, 50.000 y 200.000 miembros; incluir múltiples dispositivos y miembros desconectados. Ejecutar en infraestructura aislada con cuentas sintéticas, nunca contra contactos o salas reales.

En cada escala registrar latencias p50/p95/p99 de envío/recepción, coste de alta/baja, tamaño de mensajes, memoria máxima y batería en Android 33/34/35; tasas de error y colas del servidor. Definir presupuestos cuantitativos antes de aceptar una escala. Simular cambio de red, reinicio, entrega repetida/desordenada, incorporación concurrente, dispositivo desconocido, expulsión y compromiso de una clave. Comprobar que no se omiten destinatarios, no se entregan claves nuevas a miembros expulsados y nunca hay fallback en claro. Revisar el diseño criptográfico antes de anunciar soporte.

## Implementación y aceptación automatizada

`chat/GroupTransport.kt` separa grupos y mensajes individuales. El registro local del protocolo, separado por cuenta, persiste tras salida, expulsión y reconexión; reconoce las salas del historial anterior y rechaza versiones desconocidas. Una sala abandonada no puede caer en la ruta individual.

`GroupSendGuard` valida afiliaciones completas y dispositivos activos, incluidos los desconectados y otros dispositivos propios. Exige coincidencia exacta con los destinatarios del ciphertext, sin omisiones. Se vuelven a consultar miembros y dispositivos antes de emitir. El máximo actual es 200 personas, incluido el creador, según el `max_users: 200` comprobado en una sala temporal de ejabberd. El presupuesto defensivo es 1.000 dispositivos destinatarios por envío, excluido el dispositivo emisor. **El límite de 1.000 se prueba unitariamente; la aceptación de capacidad completa usa 200 cuentas y un segundo dispositivo del propietario.** No equivale a cinco dispositivos por persona.

Para alcanzar ese límite con OMEMO se consultan las listas PEP completas con hasta ocho solicitudes simultáneas. Se recogen todas antes de actualizar el almacén de forma serial; un fallo cancela las pendientes y bloquea el envío completo. No se usa una lista obsoleta como respaldo. Se omiten escrituras de listas que no han cambiado para evitar recifrar el vault innecesariamente. El cifrado, las sesiones y la confianza siguen en Smack/Signal.

El `max_users: 200` inspeccionado en ejabberd limita ocupantes simultáneos de una sala, no todas sus afiliaciones desconectadas; tampoco es el máximo absoluto que admite configurar ejabberd. Midori limita el conjunto completo a 200 personas para poder incluirlas a todas en cada envío. Las altas iniciales se agrupan en una petición de afiliaciones, y se verifica la sala antes de emitir las invitaciones.

La primera ejecución con 200 clientes detectó un timeout durante la creación con el perfil local de **3.000 bytes/s y ráfaga de 20.000 bytes por conexión**. Se ajustó el perfil `normal` del laboratorio a **64.000 bytes/s y ráfaga de 256.000 bytes**, conservando cuotas finitas y el máximo de 200 ocupantes. No hay una excepción reservada a cuentas de prueba. El cambio sólo afecta al ejabberd `localhost` inspeccionado; otro despliegue necesita verificar sus propias cuotas y capacidad. Se conserva una copia exacta del archivo anterior dentro del contenedor; el script imprime su ruta y las huellas antes/después. No se amplió el timeout de las peticiones ni el presupuesto de entrega de 15 segundos.

Las invitaciones existentes son idempotentes incluso con la sala llena. Después de conceder membresía se verifica otra vez el límite y se intenta revocar la concesión si el estado excede la capacidad. Una marca local desactiva autoentrada al salir o ser expulsado, aunque quede un bookmark remoto anterior. El callback de presencia no hace peticiones de red: podían bloquear la confirmación de salida. Una entrada explícita exitosa vuelve a habilitarla.

**Sigue existiendo una ventana entre la última comprobación y la emisión.** Consultar dos veces no hace atómica la membresía del servidor ni resuelve un servidor malicioso. La expulsión no borra mensajes ni archivos ya recibidos.

Con JDK 17, emuladores API 33+, ejabberd local y su pin previamente inspeccionado/configurado:

```sh
python3 scripts/configure-group-capacity-lab.py          # Inspección sin cambios
python3 scripts/configure-group-capacity-lab.py --apply  # Perfil local con respaldo y recarga
python3 scripts/test-encrypted-groups.py --serial emulator-5556 --build-install --report /tmp/groups-5556.json
python3 scripts/test-encrypted-groups.py --serial emulator-5554 --build-install --report /tmp/groups-5554.json
```

El ejecutor compara el certificado con el pin local autorizado; no confía automáticamente en uno nuevo. Instala sin borrar datos, crea 201 cuentas temporales y limpia sólo sus cuentas, sala y fixture. La prueba nunca abre el vault real ni utiliza fondos/contactos existentes. Un test omitido no pasa: exige un informe nuevo con todos los casos.

Comprueba rechazo del miembro 201 al crear e invitar, permisos, invitación idempotente, dispositivo desconocido, diez mensajes descifrados por los 199 receptores sin plaintext, segundo dispositivo propio aprobado, desconexión y recuperación MAM, expulsión, salida y reconexión sin ruta individual, y bloqueo total ante un miembro sin claves. Guarda p50/p95/p99, tamaño de stanza y PSS al finalizar. Presupuesto local p95: 15 segundos para entrega a todos. Diez muestras son una regresión funcional, no estadística de producción.

Las 200 conexiones viven en un proceso Android por ejecución, con almacén sintético en memoria. Repetir en dos emuladores no representa 200 teléfonos independientes ni prueba de carga del vault real; éste tiene pruebas instrumentadas separadas.

## MLS ejecutable, fuera del APK

`tools/mls-lab` usa OpenMLS 0.9.1 / RFC 9420 con toolchain y dependencias fijados. `python3 scripts/test-mls-groups.py --report /tmp/mls-groups.json` compila, prueba y recorre las seis escalas. Cada una genera N KeyPackages reales, un emisor y tres receptores completos muestreados. **N son dispositivos, no personas ni sockets.** El ejecutor limita cada escala a 600 segundos y 3 GiB de espacio de direcciones, conserva resultados parciales y se detiene con error en la primera que no pasa. Las siguientes no se consideran probadas.

Comprueba autenticación, alteración, duplicados, mensajes desordenados, expulsión, cambios de época, recuperación sin conexión y recarga. Mide CPU, memoria y tamaños de Welcome/commit/ciphertext. La prueba descubrió consumo de estado de recepción tras un ciphertext alterado: el laboratorio restaura el almacén y recarga al rechazarlo, y exige que la copia válida siga descifrándose. Android necesitará transacciones cifradas incrementales; copiar todo el estado sólo sirve para esta calificación. No se omiten validaciones ni se fabrican árboles para acelerar resultados.

Véanse metodología y límites en [el laboratorio](../tools/mls-lab/README.md). El [proyecto OpenMLS](https://github.com/openmls/openmls) distingue destinos Android compilados en CI de plataformas probadas. Estos resultados no acreditan integración Android, distribución masiva ni auditoría de seguridad. No se cambia ejabberd productivo ni se habilita MLS en el APK.

## Siguiente implementación y dimensionamiento

1. **Identidad y permisos:** vincular cuenta/dispositivo a credencial autenticada, admisión explícita y rotación verificable. Las BasicCredential sintéticas no verifican usernames. Todos los clientes deben comprobar autorización de altas/bajas antes de aceptar un commit: estar cifrado no lo autoriza. Mantener claves independientes de la wallet.
2. **Android:** JNI/ABI, almacén cifrado incremental y transacciones para ratchet, época, ID deduplicado e historial. Estado durable antes de confirmar/enviar, recuperación tras crash, borrado de secretos antiguos y pruebas API 33/34/35 en equipos físicos.
3. **Entrega:** ordenar commits por grupo/época esperada, rechazar conflictos y reproponer después de recuperar commits faltantes. Log persistente, outbox transaccional, idempotencia por ID/hash, backoff, cuotas y backpressure. La cola transporta ciphertext; no rebaja cifrado para resolver conflictos.
4. **Datos:** historial por conversación/ID con índices y páginas; UI paginada separada del árbol criptográfico. PostgreSQL candidato para log/outbox/cursors, particiones grupo/fecha y réplicas de lectura; un mismo grupo requiere orden coherente de épocas. Caché de metadatos públicos con TTL/invalidez por revisión, nunca fuente de autorización ni almacén de secretos.
5. **Operación:** replicar log/cola y probar failover. Medir sin contenido p95/p99, errores, conflictos de época, memoria y retraso de cola. Alertar ante latencia fuera del presupuesto durante cinco minutos, errores >1% y crecimiento sostenido del backlog.
6. **Despliegue:** salas nuevas con protocolo explícito; piloto 20→100→1.000→10.000→50.000→200.000 personas y conteo de dispositivos real. Rollback desactiva nuevas altas y conserva datos recuperables; no convierte salas silenciosamente a OMEMO o texto plano.

Estimación inicial, **no tráfico observado**: 200.000 personas × 2 dispositivos = 400.000 hojas/suscripciones. A 10 mensajes/s y 1 KiB, fanout completo supone hasta 4 millones de entregas/s y unos 4 GB/s, antes de TLS/reintentos. Log compartido de 30 días: aproximadamente 26,5 GB, o 79,6 GB con tres réplicas, sin adjuntos/índices/commits. Guardar copias por destinatario multiplicaría ese volumen. La carga y proporción conectada deben guiar cuotas y particiones; el servidor local no demuestra esa capacidad.

Presupuestos propuestos para el piloto, pendientes de aceptación: CPU de envío p95 <250 ms, recepción online p95 <2 s/p99 <5 s bajo carga declarada, transición de época p95 <5 s, incremento de PSS <128 MiB, backlog limitado y entrega disponible 99,9%. Medir batería, cambios de red, expulsión concurrente, reinicio entre persistencia/emisión y servidor que repite/omite/diverge commits. Revisión criptográfica de la integración antes de anunciar escala.

Evaluación `system-design` de la implementación actual: **4/10 (3 de 8 criterios)**. Requisitos, estimación y estrategia de despliegue documentados. Faltan redundancia operativa, almacenamiento escalable, caché de lectura, cola y monitorización con alertas desplegada; las acciones están en los puntos 3–5. Los informes de laboratorio no sustituyen estos componentes. La app permite hasta 200 miembros según la configuración local y la aceptación del cliente OMEMO; la ampliación posterior debe superar las puertas restantes.

## Resultado inicial con 20 miembros (2026-10-07)

[Informe completo](verification-v0.1-encrypted-groups-2026-10-07.json), con huella del APK, métricas y límites de cada prueba.

- Compilación mainnet debug, **73 pruebas unitarias**, traducciones de las 463 claves EN/ES/PT y comprobaciones de redes mainnet: correctas. Lint: 0 errores, 16 advertencias existentes.
- Aceptación de 20 cuentas + segundo dispositivo propio: **correcta en ambos emuladores API 34**, sin casos omitidos. p95 de entrega a los 19 receptores: 9,51 s en emulator-5556 y 4,95 s en emulator-5554. Se ejecutaron en el laboratorio compartido, con carga criptográfica del anfitrión; no se atribuye la diferencia al modelo de teléfono.
- Pruebas existentes de archivos/grupos cifrados y vault: correctas. Cero cuentas temporales de esta prueba restantes; fixtures eliminados y aplicaciones reabiertas sin borrar datos.

| Dispositivos MLS | Resultado | Alta masiva | Cifrado p95 | Ciphertext de 256 bytes |
| ---: | --- | ---: | ---: | ---: |
| 20 | Pasa | 6,44 ms | 0,103 ms | 402 bytes |
| 100 | Pasa | 32,67 ms | 0,118 ms | 402 bytes |
| 1.000 | Pasa | 744,28 ms | 0,201 ms | 402 bytes |
| 10.000 | Pasa | 82,75 s | 1,018 ms | 402 bytes |
| 50.000 | **No pasa: timeout de 600 s** | Sin resultado completo | Sin resultado | Sin resultado |
| 200.000 | **No ejecutado**, detenido por el nivel anterior | — | — | — |

Los cuatro niveles que pasan verifican los tres receptores muestreados y los casos criptográficos descritos; no todos los dispositivos reciben en el laboratorio. A 10.000, el Welcome ocupa 3.439.917 bytes y el proceso alcanza aproximadamente 152 MiB de RSS. El coste de altas masivas es un bloqueo medido, aunque los mensajes ordinarios sean pequeños. La siguiente decisión debe comparar implementación/estrategia de incorporación y perfiles del árbol antes de integrar MLS en Android; subir límites o relajar las pruebas ocultaría este problema.
