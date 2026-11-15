export default {
  "version": "UX-02-v1.1",
  "scope": "Prototipo visual aislado; no conecta API, wallet ni proveedor",
  "cases": [
    {
      "id": "fresh",
      "title": "Saldo disponible",
      "copy": "Saldo actualizado de ejemplo.",
      "action": "Enviar / Recibir",
      "rule": "Saldo de lectura confirmada; nunca representa una cuenta bancaria o una cotización garantizada."
    },
    {
      "id": "loading",
      "title": "Carga inicial",
      "copy": "Estamos consultando tu información.",
      "action": "Esperar o volver",
      "rule": "No presentar cero antes de conocer el dato."
    },
    {
      "id": "refreshing",
      "title": "Actualización parcial",
      "copy": "Actualizando. Puedes seguir consultando los datos anteriores.",
      "action": "Actualizar consulta",
      "rule": "Conservar fecha del dato anterior; no bloquear toda la página."
    },
    {
      "id": "zero",
      "title": "Saldo cero confirmado",
      "copy": "Todavía no tienes fondos. Puedes empezar recibiendo USDC o USDT.",
      "action": "Recibir",
      "rule": "Cero solo cuando la lectura lo confirma."
    },
    {
      "id": "unavailable",
      "title": "Saldo no disponible",
      "copy": "No pudimos consultar tu saldo. Tu dirección sigue disponible.",
      "action": "Reintentar / Recibir",
      "rule": "Mostrar —; nunca sustituir por cero."
    },
    {
      "id": "stale",
      "title": "Datos desactualizados",
      "copy": "Este es tu último saldo conocido. La consulta más reciente no se pudo completar.",
      "action": "Reintentar consulta",
      "rule": "Fecha/hora conocidas; envío conserva sus validaciones."
    },
    {
      "id": "empty",
      "title": "Sin movimientos",
      "copy": "Aquí aparecerán tus transferencias y órdenes.",
      "action": "Recibir",
      "rule": "Una cuenta sin actividad no es un fallo de conexión."
    },
    {
      "id": "filtered",
      "title": "Filtro sin coincidencias",
      "copy": "No hay movimientos para este filtro.",
      "action": "Mostrar todos",
      "rule": "Mantener la actividad original y limpiar solo el filtro."
    },
    {
      "id": "invalid",
      "title": "Campo inválido",
      "copy": "Revisa el tag o la dirección e inténtalo otra vez.",
      "action": "Corregir el campo",
      "rule": "Error asociado al campo; conservar monto y otros datos."
    },
    {
      "id": "expired",
      "title": "Cotización vencida",
      "copy": "Esta cotización venció. Revisa el pago otra vez para continuar.",
      "action": "Revisar otra vez",
      "rule": "Confirmar bloqueado; conservar resumen y solicitar revisión nueva."
    },
    {
      "id": "pending",
      "title": "Pago pendiente",
      "copy": "Estamos confirmando este pago. Puedes consultar el recibo.",
      "action": "Ver recibo",
      "rule": "Una referencia o hash no equivale a confirmación."
    },
    {
      "id": "ambiguous",
      "title": "Pago por comprobar",
      "copy": "Estamos comprobando este pago. No lo envíes otra vez.",
      "action": "Consultar estado",
      "rule": "No ofrecer reenvío ni afirmar éxito."
    },
    {
      "id": "reverted",
      "title": "Pago revertido",
      "copy": "El destinatario no recibió este pago. La red pudo haber cobrado un costo.",
      "action": "Ver recibo",
      "rule": "Nuevo envío exige revisión/autorización nuevas; no prometer devolución del costo."
    },
    {
      "id": "ramp-pending",
      "title": "Orden de prueba en proceso",
      "copy": "Modo de prueba · STAGING. Esta orden no representa saldo mainnet recibido.",
      "action": "Ver orden",
      "rule": "Proveedor y entorno visibles; no inventar ETA, comisión o recepción final."
    },
    {
      "id": "unsupported",
      "title": "Función no disponible",
      "copy": "Esta función no está disponible para esta configuración.",
      "action": "Volver / Elegir otra opción",
      "rule": "No promocionar capacidades deshabilitadas; idioma no determina país."
    },
    {
      "id": "temporary-error",
      "title": "Falla temporal",
      "copy": "No pudimos actualizar la información. Conservamos tus datos para que puedas reintentar.",
      "action": "Reintentar consulta",
      "rule": "Reintentar una consulta nunca autoriza repetir un pago."
    },
    {
      "id": "confirmed",
      "title": "Pago confirmado",
      "copy": "El destinatario recibió el importe aprobado.",
      "action": "Ver recibo",
      "rule": "Comunicar éxito solo con resultado confirmado, no por animación."
    }
  ],
  "screens": [
    {
      "id": "access",
      "title": "Acceso",
      "blocks": [
        "Marca y stablecoins",
        "Crear nuevo monedero",
        "Abrir monedero existente"
      ],
      "primary": "Crear o abrir",
      "next": "home"
    },
    {
      "id": "onboarding-backup",
      "title": "Guardar respaldo",
      "blocks": [
        "12 posiciones de ejemplo, sin frase real",
        "Instrucción de custodia",
        "Confirmaciones de respaldo",
        "Siguiente paso de comprobación"
      ],
      "primary": "Continuar",
      "next": "onboarding-verify"
    },
    {
      "id": "onboarding-verify",
      "title": "Comprobar respaldo",
      "blocks": [
        "Progreso de creación",
        "Dos comprobaciones de ejemplo",
        "Error corregible sin perder contexto",
        "No registrar palabras reales"
      ],
      "primary": "Comprobar",
      "next": "onboarding-pin"
    },
    {
      "id": "onboarding-pin",
      "title": "Proteger acceso",
      "blocks": [
        "PIN representado sin dato real",
        "Control de longitud disponible en producto",
        "Explicación de desbloqueo",
        "Finalizar alta de ejemplo"
      ],
      "primary": "Continuar",
      "next": "home"
    },
    {
      "id": "recover-entry",
      "title": "Recuperar wallet",
      "blocks": [
        "12 palabras como mecanismo principal",
        "Archivo opcional con descriptor",
        "Validación antes de establecer PIN",
        "Sin secretos reales en este prototipo"
      ],
      "primary": "Continuar",
      "next": "onboarding-pin"
    },
    {
      "id": "unlock",
      "title": "Desbloquear",
      "blocks": [
        "PIN representado sin dato real",
        "Error y estado de espera",
        "Protecciones vigentes conservadas"
      ],
      "primary": "Continuar",
      "next": "home"
    },
    {
      "id": "home",
      "title": "Inicio",
      "blocks": [
        "Saldo, unidad y actualización",
        "Enviar / Recibir",
        "USDC y USDT",
        "Actividad reciente"
      ],
      "primary": "Enviar o recibir",
      "next": "send-recipient"
    },
    {
      "id": "send-recipient",
      "title": "Destinatario",
      "blocks": [
        "Progreso 1 de 3",
        "Tag o dirección",
        "Pegar / Escanear",
        "Error junto al campo"
      ],
      "primary": "Continuar",
      "next": "send-amount"
    },
    {
      "id": "send-amount",
      "title": "Monto",
      "blocks": [
        "Progreso 2 de 3",
        "Destinatario conservado",
        "Importe exacto y unidad",
        "Costo en mismo token"
      ],
      "primary": "Revisar pago",
      "next": "send-review"
    },
    {
      "id": "send-review",
      "title": "Revisión",
      "blocks": [
        "Progreso 3 de 3",
        "Destinatario e importe",
        "Costo estimado / máximo",
        "Débito máximo y vencimiento"
      ],
      "primary": "Confirmar y enviar",
      "next": "send-pin"
    },
    {
      "id": "send-pin",
      "title": "Autorización",
      "blocks": [
        "Resumen sin cambios",
        "PIN protegido",
        "Error sin perder contexto"
      ],
      "primary": "Confirmar",
      "next": "send-pending"
    },
    {
      "id": "send-pending",
      "title": "Seguimiento",
      "blocks": [
        "Estado pendiente o ambiguo",
        "No duplicar operación",
        "Referencia y actualización"
      ],
      "primary": "Ver recibo",
      "next": "receipt"
    },
    {
      "id": "receipt",
      "title": "Recibo",
      "blocks": [
        "Estado / importe exacto",
        "Fecha / destinatario",
        "Costo conocido / referencia",
        "Compartir información revisada"
      ],
      "primary": "Volver a Inicio",
      "next": "home"
    },
    {
      "id": "receive",
      "title": "Recibir",
      "blocks": [
        "Origen Midori u otro servicio",
        "USDC / USDT y Base",
        "QR y tag o dirección",
        "Copiar / Compartir / alternativa"
      ],
      "primary": "Copiar o compartir",
      "next": "home"
    },
    {
      "id": "ramps",
      "title": "Agregar / Retirar",
      "blocks": [
        "Proveedor y entorno",
        "Activo / moneda / método disponible",
        "Monto y unidad",
        "Cotización final en proveedor"
      ],
      "primary": "Continuar",
      "next": "ramp-provider"
    },
    {
      "id": "ramp-provider",
      "title": "Proveedor",
      "blocks": [
        "Continuidad y contexto",
        "Verificación / métodos del proveedor",
        "Costo final donde se conoce",
        "Cerrar no confirma ni cancela orden"
      ],
      "primary": "Volver a Midori",
      "next": "ramp-order"
    },
    {
      "id": "ramp-order",
      "title": "Orden",
      "blocks": [
        "Proveedor / STAGING",
        "Estado e identificador",
        "Importes conocidos",
        "Retorno y próxima acción"
      ],
      "primary": "Ver movimientos",
      "next": "activity"
    },
    {
      "id": "activity",
      "title": "Movimientos",
      "blocks": [
        "Filtros con alcance real",
        "Dirección / monto / estado",
        "Fuentes diferenciadas",
        "Vacío / error parcial / cargar más"
      ],
      "primary": "Abrir detalle",
      "next": "receipt"
    },
    {
      "id": "account",
      "title": "Cuenta",
      "blocks": [
        "Perfil y tag",
        "Preferencias",
        "Seguridad / recuperación",
        "Conexiones / ayuda / acerca de"
      ],
      "primary": "Abrir recuperación",
      "next": "recovery"
    },
    {
      "id": "recovery",
      "title": "Recuperación",
      "blocks": [
        "12 palabras restauran la wallet",
        "Archivo opcional con descriptor",
        "Guardar / Verificar archivo",
        "No contiene claves privadas"
      ],
      "primary": "Volver a Cuenta",
      "next": "account"
    }
  ]
};
