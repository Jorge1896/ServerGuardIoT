# Guía de continuación del proyecto ServerGuardIoT
**Para el equipo a cargo de Wokwi, Firebase y la integración final**  
**Fecha de entrega de la guía:** 24 de noviembre de 2026  
**Repositorio:** https://github.com/Jorge1896/ServerGuardIoT  
**Base Firebase Realtime Database:** `https://serverguardiot-default-rtdb.firebaseio.com/`

> **Objetivo:** terminar la simulación ESP32 en Wokwi, conectarla de forma segura con Firebase, probar los protocolos de comandos e integrar el sistema con la aplicación Android existente. Esta guía es una hoja de ruta; ninguna prueba de ESP32 se considera aprobada hasta ejecutarla y reunir evidencia.

## 0. Qué está listo y qué NO

**Android disponible:** autenticación de usuarios; Dashboard de telemetría; control remoto con v1; configuración de límites; alertas y notificaciones locales cuando el Dashboard está activo y existe heartbeat reciente; pantalla de historial **heredado**; repositorio Kotlin para v2 (sin conectar aún a los botones).

**Verificado previamente:** la compilación `assembleDebug`, las pruebas unitarias Android `testDebugUnitTest` y comprobaciones de pantallas. El envío Android → Firebase y las confirmaciones v1 se probaron **manualmente**, no con un ESP32 real.

**Aún pendiente:** firmware Wokwi autenticado; lectura/escritura real en Realtime Database, sensores y actuadores; heartbeat; comandos v1/v2; respuestas v2; historial automático de mediciones del nuevo ESP32; integración extremo a extremo. Aunque **las reglas de v2 ya fueron publicadas en Firebase** por el responsable del proyecto, la interfaz Android sigue usando v1. Los documentos existentes sobre v2 describen el diseño y pueden tener notas de estado anteriores a la publicación de las reglas.

## 1. Preparación del equipo y GitHub

1. Aceptar la invitación de colaboración al repositorio. Cada persona usa su cuenta; **no** compartir contraseñas.
2. Clonar el repositorio (si aún no está clonado):
   ```powershell
   git clone https://github.com/Jorge1896/ServerGuardIoT.git
   cd ServerGuardIoT
   git fetch origin
   ```
3. La versión Android preparada se encuentra en `feature/unidad3-control-wifi`. Revisarla sin alterarla:
   ```powershell
   git switch --track origin/feature/unidad3-control-wifi
   ```
   Si la rama ya existe localmente, usar `git switch feature/unidad3-control-wifi`.
4. Crear **una rama propia** desde esta base para la integración, por ejemplo:
   ```powershell
   git switch -c feature/wokwi-esp32
   ```
   Si distintos compañeros trabajan en paralelo, usar ramas con nombres diferentes.
5. Guardar firmware y esquema Wokwi en una carpeta como `wokwi/` (por ejemplo, `sketch.ino`, `diagram.json`, `libraries.txt`) sin subir secretos.
6. Al terminar una funcionalidad:
   ```powershell
   git status
   git add wokwi/
   git commit -m "feat(wokwi): integrar telemetria ESP32"
   git push -u origin feature/wokwi-esp32
   ```
   Crear un **Pull Request** hacia `feature/unidad3-control-wifi` para revisar la integración. No hacer push directo a `master` ni fusionar sin una revisión.

**Si no encuentran el botón de PR o existen conflictos**, pedir revisión del responsable del repositorio; no sobrescribir cambios de Android.

## 2. Acceso y seguridad Firebase — hacer ANTES de escribir telemetría

**Dos accesos distintos:**
- Permiso de consola: un integrante con permisos del proyecto los invita desde **Firebase Console → Configuración del proyecto → Usuarios y permisos**. Utilizar el menor rol necesario.
- Permiso de dispositivo (en las reglas): crear una **identidad propia del ESP32** en Firebase Authentication y registrar `dispositivos/{UID_DEL_ESP32}: true` mediante la consola o un administrador autorizado. No confundir este UID con el del usuario Android en `autorizados/{uid}`.

**Muy importante:** Wi-Fi conectado NO da permiso a Firebase. El ESP32 necesita autenticarse realmente y presentar un **ID token Firebase válido** para llamar a la REST API. Las reglas actuales **deniegan** escrituras de telemetría o respuestas si no hay identidad de dispositivo autorizada. Una API key de Firebase por sí sola no es un ID token y no concede permiso.

Recomendaciones:
- Mantener credenciales en Wokwi Secrets u otro mecanismo privado compatible; no subirlas a GitHub.
- No utilizar tokens fijos caducados: deben refrescarse cuando expiren.
- Evitar imprimir credenciales o tokens en el monitor serie, capturas o mensajes.
- No cambiar las reglas a `".read": true` / `".write": true` para “hacerlo funcionar”.
- Acordar con el administrador quién registrará el UID del dispositivo; no crear una cuenta con permisos de administrador dentro del firmware.

**Primera prueba obligatoria:** antes de implementar sensores, conseguir un GET autorizado a `/serverguard/configuracion` y una escritura autorizada de telemetría. Si aparece HTTP 401/403 o `Permission denied`, revisar identidad, token, UID y reglas; NO abrir la base de datos.

## 3. Armar el circuito Wokwi

Componentes mínimos sugeridos, sujetos al diseño que ya tenga el equipo:
- ESP32 con Wi-Fi `Wokwi-GUEST`.
- DHT22 (o sensor equivalente que entregue temperatura y humedad).
- LED indicador y actuador simulado de ventilador (por ejemplo, LED/motor equivalente).
- Alarma con buzzer y/o LED.
- Sensor de apertura de rack (interruptor/reed simulado).
- Servomotor para apertura/cierre comandado de la puerta.

**Importante:** el firmware anterior manejaba FAN/LED/ALARM pero no las órdenes `DOOR_OPEN`/`DOOR_CLOSE`. El nuevo código debe implementarlas y publicar `rack_abierto` de forma coherente. Definir pines en `diagram.json` y en el firmware; verificar cada dispositivo primero sin Firebase.

## 4. Implementar telemetría ESP32

Publicar un objeto completo en `/serverguard/telemetria` (no escribir campos que no aparecen en las reglas):

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
  "ultima_conexion": 1795550000000
}
```

Valores de ejemplo, NO datos reales. Requisitos:
- Temperatura como número entre **-40 y 80**; humedad entre **0 y 100**; estados como booleanos.
- `ultima_conexion` debe ser Unix en **milisegundos**, actualizado con una fuente de tiempo fiable (NTP o tiempo de servidor); no utilizar `millis()` del ESP32 como fecha Unix.
- Actualizar cada pocos segundos (por ejemplo, 2–5 s si red y simulador lo permiten), sin saturar Firebase.
- La app Android considera heartbeat reciente si tiene menos de ~15 s, con tolerancia limitada a desfase de reloj.
- Revisar que el Dashboard reciba datos de **Wokwi** y no los valores estáticos usados durante la preparación.

**Prueba:** cambiar la temperatura del sensor en Wokwi, observar el mismo valor en Firebase y Android. Apagar Wokwi y comprobar que Android deja de considerarlo activo.

## 5. Configuración automática de temperatura

Leer:
```json
{
  "temperatura_minima": 18.0,
  "temperatura_maxima": 28.0
}
```
Ruta: `/serverguard/configuracion`.

- La app puede actualizar ambos límites desde Android.
- El ESP32 debe **leerlos y aplicarlos**, no quedarse con una constante fija como 28 °C.
- Especificar en el firmware la política (por ejemplo, activar ventilador por sobre el máximo y desactivarlo bajo un umbral definido con histéresis).
- Decidir cómo interactúan control manual y automático: una orden manual no debería ser anulada de inmediato por el modo automático sin que el comportamiento se explique.
- Reportar `alerta_activa` y estados físicos coherentes con la política del equipo.

**Prueba:** cambiar el límite máximo en Android, confirmar lectura en firmware y que el comportamiento del ventilador cambie según el nuevo valor.

## 6. Integrar primero el protocolo v1 (existente en Android)

Ruta de comando: `/serverguard/comando`, un **string**, por ejemplo `"FAN_ON"`. Posibles valores:

`DOOR_OPEN`, `DOOR_CLOSE`, `FAN_ON`, `FAN_OFF`, `LED_ON`, `LED_OFF`, `ALARM_ON`, `ALARM_OFF`, `NONE`.

Flujo:
1. Verificar que el comando sea `NONE` antes de una nueva prueba.
2. Pulsar botón en Android, comprobar que Firebase reciba el string.
3. ESP32 lee el comando y valida que esté permitido.
4. ESP32 actúa, observa si se ejecutó, y publica `/serverguard/confirmacion`:
   ```json
   {"comando":"FAN_ON","resultado":"EJECUTADO"}
   ```
   `resultado` puede ser `SIN_COMANDOS`, `EJECUTADO`, `RECHAZADO`, `ERROR`.
5. Solo **después de procesar y publicar** la respuesta, el ESP32 devuelve `/serverguard/comando` a `"NONE"` para desbloquear nuevos controles.
6. Repetir con los ocho comandos; comprobar que telemetría coincida con los actuadores.

**Límite v1:** `confirmacion` es el último resultado general, no demuestra que corresponda inequívocamente a una pulsación específica. No presentar un `EJECUTADO` viejo como prueba nueva.

## 7. Integrar el protocolo v2 (comandos con ID único)

**Situación real:** las reglas de v2 ya están publicadas, el archivo Android `ComandosV2Repository.kt` existe, pero **la UI de Control remoto todavía envía v1**. No esperar que pulsar botones cree solicitudes v2 sin una adaptación posterior.

Estructura:
```text
/serverguard/v2/solicitudes/{id}
/serverguard/v2/respuestas/{id}
```

Solicitud creada por **Android autorizado** (no por ESP32):
```json
{
  "comando": "FAN_ON",
  "creado_por": "UID_ANDROID_AUTORIZADO",
  "creado_en": 1795550000000
}
```
Respuesta creada por **ESP32 autorizado**, con **el mismo ID**:
```json
{
  "comando": "FAN_ON",
  "resultado": "EJECUTADO",
  "respondido_en": 1795550001000
}
```

- Las reglas permiten crear solicitudes únicamente a una identidad Android autorizada; un ESP32 **no** debe intentar crear solicitudes para probar v2.
- El ESP32 debe escuchar/consultar solicitudes, identificar IDs nuevos, ejecutar de forma idempotente y crear una única respuesta por ID. Debe comprobar si ya respondió antes de reintentar.
- Tratar solicitudes muy antiguas según una política de caducidad acordada, en lugar de ejecutar comandos arbitrariamente después de una reconexión.
- Evitar enviar el mismo comando **simultáneamente en v1 y v2**.
- Antes de cambiar la interfaz Android, probar el firmware v2 en un entorno controlado con una solicitud creada desde una cuenta Android autorizada o herramienta autenticada equivalente.
- Activar v2 en Android **solo cuando** lectura, actuación y respuesta con ID estén verificadas. No eliminar v1 como respaldo hasta terminar esa etapa.

Consultar [PROTOCOLO_V2.md](PROTOCOLO_V2.md) para el contrato detallado. Si el documento dice que las reglas aún no están publicadas, esa nota de estado es anterior: **el responsable confirmó su publicación el 24/11/2026**. Esto no certifica que el firmware las haya probado.

## 8. Historial del nuevo ESP32 (trabajo de integración)

La pantalla de Historial existente consulta `historial/sala_principal`, que corresponde a la **versión anterior**. No almacena automáticamente todas las lecturas nuevas.

Para implementar historial real:
1. Acordar nueva ruta (por ejemplo `/serverguard/historial/{id}`) y frecuencia razonable de registro.
2. Definir campos, timestamp Unix ms, límites de retención y quién escribe.
3. Preparar reglas nuevas que autoricen solo la identidad ESP32 para registrar datos válidos (no asumir que las reglas actuales ya permiten esa ruta).
4. Guardar registros desde firmware o un servicio confiable; probar la persistencia.
5. Adaptar la pantalla Android para leer ese historial nuevo y luego construir gráficos.

**No crear rutas con valores vacíos ni abrir reglas públicas solo para probar el historial.**

## 9. Matriz de pruebas de entrega (obligatoria)

| Caso | Acción | Resultado/evidencia esperada |
|---|---|---|
| T-01 | Arranque ESP32 y autenticación Firebase | Conexión a Wi-Fi, token válido, GET autorizado |
| T-02 | DHT22 publica mediciones | Cambios reales visibles en Firebase y Android |
| T-03 | Detener Wokwi | Heartbeat queda antiguo y Dashboard deja de mostrar ESP32 activo |
| T-04 | Cambiar límites desde Android | ESP32 lee y aplica valores |
| T-05 | FAN_ON / FAN_OFF | Actuador cambia, telemetría coincide y se confirma |
| T-06 | LED_ON / LED_OFF | LED cambia y se confirma |
| T-07 | ALARM_ON / ALARM_OFF | Alarma cambia y se confirma |
| T-08 | DOOR_OPEN / DOOR_CLOSE | Servo/puerta cambia, `rack_abierto` coincide |
| T-09 | Rechazo/error de comando | Se publica `RECHAZADO` o `ERROR`, sin fingir ejecución |
| T-10 | Solicitud v2 con ID | Respuesta bajo el mismo ID y valor de comando |
| T-11 | UID sin autorización | Firebase rechaza lectura/escritura según reglas |
| T-12 | Reinicio/reconexión | Sin ejecución duplicada ni reintentos inseguros |

Evidencias: captura del circuito Wokwi, monitor serie **sin secretos**, datos Firebase, pantallas Android, IDs correlacionados, comandos y resultados, prueba de desconexión, y enlaces a commits/PR. Etiquetar claramente las simulaciones manuales.

## 10. Lista de entrega y responsable de cierre

- [ ] Firmware y `diagram.json` en el repositorio; secretos fuera de Git.
- [ ] UID del dispositivo autorizado y GET/PUT/PATCH autenticados probados.
- [ ] DHT22, rack, ventilador, LED, alarma y servo con estados reales.
- [ ] Telemetría y `ultima_conexion` actualizadas desde el ESP32.
- [ ] Límites de temperatura leídos desde Firebase.
- [ ] Comandos v1 reales ejecutados y confirmados.
- [ ] v2 probado con IDs y respuesta; interfaz Android activada **solo tras integración**.
- [ ] Historial nuevo definido, reglas aprobadas y registros guardados si es requisito de la entrega.
- [ ] `assembleDebug` y `testDebugUnitTest` pasan después de cualquier cambio Android.
- [ ] Pull Request revisado, pruebas y capturas adjuntas.

### Qué comunicar ante un problema

Informar **qué se intentó, ruta Firebase, operación, respuesta HTTP/error, rama y archivo afectado, comportamiento esperado, comportamiento observado y captura sin datos sensibles**. No modificar reglas o código Android a ciegas.

## Enlaces útiles dentro del repositorio

- [Contrato v1 y telemetría](INTEGRACION_WOKWI.md)
- [Diseño v2 con IDs](PROTOCOLO_V2.md)
- [Plan de pruebas Android](PRUEBAS_ANDROID.md)
- [Resumen general del proyecto](../README.md)

**Regla principal del equipo:** terminar y demostrar primero telemetría + comandos v1 funcionando con Wokwi; después integrar respuestas v2 y las funcionalidades que necesitan historial. Proteger siempre las reglas y las cuentas Firebase.
