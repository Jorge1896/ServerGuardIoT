# Protocolo de órdenes v2 — propuesta de migración segura

**Estado:** preparado para implementación; **NO activado** en Android ni en Firebase/Wokwi.  
**Compatibilidad:** la app existente continúa enviando texto a `/serverguard/comando` (v1).

## Por qué cambiar

El protocolo v1 solo contiene `"FAN_ON"` y una confirmación global `{ "comando": "FAN_ON", "resultado": "EJECUTADO" }`. Si la misma orden se envía varias veces, Android no puede relacionar una respuesta con una pulsación específica. Los comandos perdidos, reinicios y confirmaciones antiguas tampoco se pueden distinguir.

## Modelo de datos v2

Las rutas nuevas están separadas de v1:

- `/serverguard/v2/solicitudes/{id}`: un registro inmutable, creado una sola vez por Android autorizado.
- `/serverguard/v2/respuestas/{id}`: respuesta inmutable, creada por el ESP32 autorizado después de ejecutar o rechazar la solicitud del mismo ID.

Android genera `{id}` con `push().key` y lo utiliza en ambas rutas para correlación; los IDs son claves opacas, no información secreta ni pruebas de autorización.

Ejemplo de solicitud `/serverguard/v2/solicitudes/-NxEjemploID`:

```json
{
  "comando": "FAN_ON",
  "creado_por": "UID_DEL_USUARIO",
  "creado_en": 1795550000000
}
```

Ejemplo de respuesta `/serverguard/v2/respuestas/-NxEjemploID`:

```json
{
  "comando": "FAN_ON",
  "resultado": "EJECUTADO",
  "respondido_en": 1795550001000
}
```

**Timestamps:** tiempo Unix en milisegundos. Android debe utilizar `ServerValue.TIMESTAMP` al crear la solicitud. El dispositivo debe generar el tiempo con una fuente sincronizada o usar timestamp de servidor. Un reloj arbitrario del ESP32 no sirve para medir latencia de forma fiable.

### Comandos y estados

Comandos: `DOOR_OPEN`, `DOOR_CLOSE`, `FAN_ON`, `FAN_OFF`, `LED_ON`, `LED_OFF`, `ALARM_ON`, `ALARM_OFF`.

Respuestas: `EJECUTADO`, `RECHAZADO`, `ERROR`. Una solicitud sin respuesta permanece **pendiente**. Después de 30 segundos Android puede marcarla *sin respuesta observada*, pero **no** interpretar ese tiempo de espera como ejecución fallida.

### Flujo esperado en Wokwi

1. Autenticar ESP32 con una cuenta Firebase independiente cuyo UID esté en `/dispositivos`.
2. Consultar solicitudes de v2; usar `orderByChild("creado_en")` para procesamiento en orden de creación, con `limitToFirst` y `.indexOn` apropiado.
3. Para cada ID sin respuesta, validar comando y el estado real del actuador.
4. Hacer la operación de forma **idempotente**: encender/apagar o establecer puerta abierta/cerrada; no ejecutar una acción irreversible varias veces al reintentar.
5. Publicar `respuestas/{mismo_id}` una sola vez. Si ya existe, no duplicar; conservar y comparar el resultado.
6. En reconexión, volver a revisar solicitudes pendientes. Para evitar ejecutar órdenes muy antiguas se deberá acordar una política de caducidad (por ejemplo, no ejecutar órdenes anteriores a 2 minutos y devolver `RECHAZADO`).
7. Android escucha solamente `respuestas/{id}` de la solicitud que creó y muestra el resultado verificable para ese ID.

**Advertencia:** escribir una respuesta no es una prueba física infalible. Para pruebas académicas se debe verificar también la telemetría y, donde sea posible, el estado real del actuador.

## Seguridad y reglas para revisión

Las reglas actuales **no incluyen** `/serverguard/v2`: de momento estas rutas serán denegadas. Antes de habilitar v2 hay que publicar reglas con estas invariantes:

- Lectura de solicitudes y respuestas solo para Android autorizado o ESP32 autorizado.
- Creación de solicitudes **solo Android autorizado** y únicamente cuando no existan; `creado_por === auth.uid`, comando de lista blanca y timestamp de servidor validado.
- Creación de respuestas **solo ESP32 autorizado**, únicamente si existe una solicitud con igual ID y aún no existe respuesta. El comando de la respuesta debe coincidir con la solicitud.
- Validación de tipos, longitudes, estados permitidos; prohibir campos adicionales y modificaciones o borrados.
- Controlar tamaño y retención: la base de datos necesita limpieza administrativa periódica; con permisos de creación inmutable, los clientes no borran registros.
- En producción, un servicio servidor de confianza debe coordinar la cola y la entrega una sola vez si la semántica del dispositivo lo exige.

Las rutas `/serverguard/comando`, `/serverguard/confirmacion`, `/serverguard/telemetria` y `/serverguard/configuracion` **no cambian** durante esta migración.

## Plan de activación coordinada

1. Compartir este documento y `docs/INTEGRACION_WOKWI.md` con compañeros.
2. Configurar identidad Firebase del ESP32 de forma segura y verificar que puede leer/escribir en rutas permitidas.
3. Preparar firmware que procese v2 con IDs, sin dejar de soportar v1 hasta concluir la migración.
4. Publicar reglas y probar v2 con datos de prueba.
5. Activar en Android la implementación de v2 mediante una actualización coordinada.
6. Probar ON/OFF, apertura/cierre, rechazos, reconexión, reintentos y repetición de un mismo comando con IDs distintos.

**No publicar reglas abiertas** ni subir secretos de Firebase al repositorio.
