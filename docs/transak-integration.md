# Compra y venta con Transak Production

La wallet conserva Base mainnet (8453) y Bitcoin mainnet. Android muestra compra/recarga y venta; el servicio `services/fiat` utiliza **@transak/wdk-protocol-fiat-transak 1.0.2**, fijado con su lockfile. No se incluyen claves de Transak en el APK ni se ejecuta el módulo fiat en el worklet que custodia la wallet.

## Estado y alcance

- Autenticación de partner y catálogo comprobados contra Production el 2026-10-07. El catálogo devolvió USDC/Base, ETH/Base y BTC/Bitcoin, con compra y venta, y 26 monedas fiat. El USDT puenteado de esta wallet no coincidió con un activo disponible; no se ofrece mediante Transak.
- La disponibilidad por país, importe, medio de pago y KYC la determina Transak en su checkout; el catálogo global no garantiza la elegibilidad de cada usuario.
- Queda pendiente un **dominio HTTPS real para el backend**, autorizar ese dominio y la IP de salida en el panel Transak y comprobar el checkout desde Android. No se ha creado ni pagado una orden real. La autenticación del partner, por sí sola, no prueba que la IP o el dominio permitan crear sesiones.
- Sin backend configurado, Android muestra que la compra y venta no están disponibles. Recibir por dirección y enviar desde la wallet siguen funcionando.

## Flujo

1. Android consulta `/v1/fiat/capabilities`. El adaptador solo publica coincidencias exactas de símbolo, red, decimales y contrato; BTC utiliza el identificador Transak `mainnet`, Base `base`.
2. El usuario elige compra o venta, activo, moneda fiat e importe. Compra expresa el importe en moneda fiat; venta, en cripto. Los importes se convierten a enteros de unidades mínimas sin redondear.
3. Al pulsar Continuar, `/v1/fiat/challenge` valida la selección y devuelve nonce aleatorio y vencimiento. Android reconstruye un mensaje acotado al origen, wallet, operación, activo, red e importe; WDK firma con la cuenta Base de la wallet abierta. No firma texto arbitrario recibido del servidor. La firma autoriza únicamente una sesión, nunca una transferencia.
4. `/v1/fiat/session` verifica EIP-191, cuenta, IP y vencimiento. Consume el nonce antes de cualquier petición al proveedor, impidiendo replays. Los parámetros efectivos proceden del reto guardado.
5. El adaptador renueva el access token de partner cuando corresponde y llama `buy()` o `sell()` del módulo WDK. El callback crea la sesión Transak desde el servidor. No oculta las comisiones; fija activo/red/importe y bloquea la edición de la dirección de compra.
6. Android abre una URL de su propio backend con un ticket de un solo uso (60 segundos). La página navega a `https://global.transak.com/` con `Referrer-Policy: origin`. Así el navegador aporta el dominio HTTPS autorizado, sin depender de que acepte un referrer de paquete Android. La URL Transak vence según el proveedor y no se reutiliza.
7. Transak gestiona cotización final, pago, KYC e instrucciones de depósito para ventas. Volver al navegador solo actualiza saldos; no crea transacciones confirmadas ni envía cripto automáticamente. El usuario revisa cualquier envío desde la wallet como de costumbre.

## Configuración del servidor

Requiere Node 22 o posterior. Desde `services/fiat`:

```sh
npm ci --ignore-scripts
npm test
# Solo si aún no existe; nunca sobrescribir un .env ya configurado.
cp -n .env.example .env
chmod 600 .env
```

Configurar `.env` con `TRANSAK_API_KEY`, `TRANSAK_API_SECRET`, `PUBLIC_ORIGIN=https://<dominio-real>`, `HOST=127.0.0.1`, `PORT=8787` y las IP exactas del proxy en `TRUSTED_PROXY_IPS`. `.env` está ignorado por Git. Las credenciales suministradas se guardaron únicamente en este archivo local con permisos 0600; no se reproducen en esta documentación. En producción, aprovisionar secretos fuera del repositorio y restringir su lectura al usuario del servicio.

`npm start` arranca en loopback. `GET /healthz` verifica el proceso, no la disponibilidad de Transak. `Caddyfile.example` sirve de plantilla para HTTPS y sobrescribe `X-Forwarded-For` con la IP observada. El servicio rechaza cadenas de IP; no acepta `x-user-ip` del cuerpo ni encabezados de clientes como autoridad. Si el proxy está en otro contenedor/servidor, usar su IP real y restringir el puerto del backend a ese proxy.

`npm run check:production` repite explícitamente la consulta del catálogo y la autenticación de partner, sin crear sesiones ni órdenes. Solo imprime estados HTTP y cobertura; no imprime credenciales o tokens. La renovación debe ejecutarse desde la única instancia propietaria del access token.

Autorizar en Transak la **IP pública de salida** del servidor y el **dominio HTTPS** usado en `PUBLIC_ORIGIN` (es el `referrerDomain` de esta integración con navegador). Las credenciales deben ser Production. `midori-fiat.service.example` muestra un proceso systemd con usuario dedicado; adaptar rutas y cuenta antes de instalarlo. No se ha desplegado ningún servicio remoto automáticamente.

La implementación usa una sola instancia: retos, tickets y access token están en memoria, con límites y expiración. Reiniciar obliga a reintentar la sesión, sin perder fondos ni crear pagos. Antes de escalar a varias instancias, implementar almacenamiento compartido con consumo atómico de nonce/ticket y coordinación de renovación del token. No habilitar registros de cuerpos, firmas, tokens o URLs de lanzamiento en el proxy.

## Configuración Android

En el `local.properties` ignorado por Git:

```properties
midori.fiat.backendUrl=https://<dominio-real>
```

También se admite `MIDORI_FIAT_BACKEND_URL` o `-PmidoriFiatBackendUrl=https://<dominio-real>`. Solo se acepta un origen HTTPS, sin credenciales, path, query o fragmento. No se admite HTTP ni fallback a staging. Después, compilar `./gradlew assembleMainnetDebug`. Las claves de partner y el access token nunca son campos de `BuildConfig`.

## Pruebas y límites

Resultado final: **85 unitarias Android, 10 del backend, 6 recorridos de interfaz en 5556 y 1 integración OMEMO real aprobados**. Build aprobado; lint sin errores, con 18 avisos y limitaciones de checks Compose registradas. Ver el [registro de verificación](verification-v0.1-wallet-transak-2026-10-07.json), que identifica el APK y distingue las pruebas sintéticas de las llamadas reales a Production.

`npm test` usa el módulo WDK fijado, respuestas HTTP sintéticas y firmas EIP-191 reales de claves exclusivamente de prueba. Cubre redes/contratos, unidades, compra/venta, renovación de token, IP por petición, replay, expiración, dominio de apertura, límites HTTP y errores sin secretos. Los tests Android cubren la conversión, el reto acotado, navegación HTTPS, cambio de wallet durante una petición, recuperación de errores y pulsaciones repetidas. El QR se codifica y decodifica con ZXing para todos los activos y debe devolver únicamente la dirección de la wallet.

`FiatUiTest` recorre compra y venta en inglés, español y portugués con estado y gateway de prueba, sin abrir vaults de usuario ni realizar pagos. La apertura en un proveedor real, el KYC, una compra financiada y una venta con depósito siguen siendo aceptación pendiente y no se sustituyen por estos tests.

## Revisión de arquitectura

Skill aplicada: [clean-architecture](/home/ponchale/.agents/skills/clean-architecture/SKILL.md). Evaluación de la integración nueva: **7/7 criterios, 10/10**. Políticas e importes se prueban sin servidor ni Android; dependencias apuntan hacia DTO/puertos; almacenamiento efímero no filtra una base de datos al dominio; checkout no recibe objetos HTTP; Compose/HTTPS/WDK son adaptadores; no hay ciclos entre componentes; `main.js` y la composición Android construyen los adaptadores. No hay filas fallidas en este alcance ni inversión adicional requerida. No es una auditoría de toda la aplicación ni de seguridad del proveedor.

## Referencias oficiales

- [Configuración WDK Transak: backend, token, IP y sesiones](https://docs.wdk.tether.io/sdk/fiat-modules/fiat-transak/configuration/).
- [API del módulo fiat](https://docs.wdk.tether.io/sdk/fiat-modules/fiat-transak/api-reference/).
- [Sesiones Transak y referencia de origen](https://docs.transak.com/guides/migration-to-api-based-transak-widget-url).
- [Parámetros para fijar red, activo, importe y dirección](https://docs.transak.com/customization/query-parameters).
