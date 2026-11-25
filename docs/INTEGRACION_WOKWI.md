# Contrato de integración Android ↔ Firebase ↔ ESP32 (Wokwi)

**Proyecto:** ServerGuardIoT — Unidad 3  
**Rama Android:** `feature/unidad3-control-wifi`  
**Base Realtime Database:** `https://serverguardiot-default-rtdb.firebaseio.com/`

## Estado actual (protocolo v1 compatible)

La app Android usa Firebase Authentication y Realtime Database. El ESP32 todavía debe implementar autenticación válida y autorización de escritura; **no abrir las reglas públicas**.

### Rutas

| Ruta | Tipo | Quién escribe | Quién lee |
|---|---|---|---|
| `/serverguard/comando` | String | Android autorizado; ESP32 autorizado solo para devolver `NONE` | Android y ESP32 autorizados |
| `/serverguard/configuracion` | Objeto | Android autorizado | Android y ESP32 autorizados |
| `/serverguard/telemetria` | Objeto | ESP32 autorizado | Android y ESP32 autorizados |
| `/serverguard/confirmacion` | Objeto | ESP32 autorizado | Android y ESP32 autorizados |

El usuario Android necesita su UID en `/autorizados/{uid}: true`. El ESP32 necesita una identidad Firebase independiente registrada en `/dispositivos/{uid}: true`; este registro se administra desde Firebase Console/Admin, no por el cliente. Wi-Fi sin autenticación Firebase **no es suficiente**.

### Comandos exactos admitidos

`DOOR_OPEN`, `DOOR_CLOSE`, `FAN_ON`, `FAN_OFF`, `LED_ON`, `LED_OFF`, `ALARM_ON`, `ALARM_OFF` y `NONE` (sin orden pendiente).

La app publica **una cadena simple**, NO un objeto JSON:

```json
"FAN_ON"
```

**Importante:** el firmware compartido anteriormente solo procesa FAN/LED/ALARM. Deben implementar DOOR_OPEN/DOOR_CLOSE y verificar que un servomotor o actuador cambie el estado.

### Lectura y ejecución por ESP32

1. Conectarse a Wi-Fi y autenticarse en Firebase.
2. Leer `/serverguard/comando`; ignorar `NONE`.
3. Ejecutar el comando admitido y comprobar el resultado del actuador.
4. Publicar `/serverguard/confirmacion` con `comando` y `resultado`.
5. Devolver `/serverguard/comando` a `NONE` **después** de procesar la orden y registrar el resultado. Si falla, registrar `ERROR` o `RECHAZADO` según corresponda. Gestionar reintentos de forma idempotente para evitar repetir acciones.
6. Continuar enviando telemetría y marca temporal.

Ejemplo de confirmación:

```json
{ "comando": "FAN_ON", "resultado": "EJECUTADO" }
```

`resultado`: `SIN_COMANDOS`, `EJECUTADO`, `RECHAZADO` o `ERROR`.

**Limitación v1:** la confirmación muestra el **último resultado publicado**; no está correlacionada criptográfica o inequívocamente con cada pulsación. No utilizarla como prueba definitiva de una orden nueva. Una versión posterior deberá incluir IDs únicos y confirmaciones atómicas, con cambios coordinados en Android, ESP32 y reglas.

### Telemetría (ejemplo)

```json
{
  "temperatura": 24.0,
  "humedad": 50.0,
  "rack_abierto": false,
  "ventilador": false,
  "led": false,
  "alarma": false,
  "alerta_activa": false,
  "estado_conexion": "Conectado",
  "ultima_conexion": 1795543200000
}
```

`ultima_conexion`: tiempo Unix **en milisegundos**, actualizado cada pocos segundos con hora sincronizada. Android considera reciente un valor de los últimos 15 segundos. Un estado textual `"Conectado"` sin una marca reciente **no prueba** conexión.

`/serverguard/configuracion`:

```json
{ "temperatura_minima": 18.0, "temperatura_maxima": 28.0 }
```

El ESP32 debe leer los límites y respetar los valores guardados, no usar exclusivamente una constante fija. Los rangos válidos en reglas son -40 a 80 °C, con mínima menor a máxima.

### Pruebas que deben ejecutar los compañeros

1. Android muestra `Firebase: conectado` y los valores de `telemetria`.
2. Wokwi autenticado escribe mediciones y la app se actualiza.
3. `ultima_conexion` se actualiza; al detener Wokwi, Android muestra desconexión tras ~15 s.
4. Con `comando: "NONE"`, pulsar FAN_ON en Android; verificar `comando: "FAN_ON"`.
5. Wokwi ejecuta; publica `confirmacion` y restaura `NONE`. La app vuelve a permitir comandos.
6. Repetir con FAN_OFF, LED, ALARM y DOOR (estos últimos requieren firmware/servo).
7. Cambiar temperaturas en Android; Wokwi debe leer y aplicar.
8. Probar Wi-Fi desconectado, reglas denegadas, reconexión y comandos desconocidos.

### Estado de entrega

- Android lee telemetría, muestra límites, emite comandos string y consulta la última confirmación.
- Android deshabilita el envío mientras `/serverguard/comando` tenga una orden pendiente.
- El simulador ESP32 aún **no se ha probado end-to-end** en este proyecto.
- El firmware debe proporcionar autenticación, escritura autorizada, servo, límites dinámicos y confirmaciones.
- No enviar contraseñas, tokens o claves privadas en commits o capturas.
