# ServerGuardIoT — Android + Firebase + ESP32 (Wokwi)

Proyecto académico de monitoreo ambiental y control remoto de una sala de servidores. Cliente Android desarrollado en Kotlin/XML con Firebase Authentication y Firebase Realtime Database; la simulación del ESP32 y sus actuadores se desarrolla por separado en Wokwi.

## Estado de integración (24/11/2026)

- **Compilación y pruebas confirmadas por el desarrollador:** `assembleDebug` y `testDebugUnitTest` fueron exitosos tras las mejoras de Android; las pantallas principales también fueron verificadas. Cualquier cambio posterior requiere repetir la compilación.
- **Protocolo v1:** se verificó Android → Firebase mediante comandos de texto y simulación manual de confirmaciones. **No se ha verificado ESP32 extremo a extremo**.
- **Protocolo v2:** código de repositorio preparado y reglas de Realtime Database publicadas por el desarrollador. **No está activado en los botones** y requiere firmware Wokwi compatible.
- Los valores de telemetría usados inicialmente (24 °C, 50 %) fueron datos de ejemplo cargados manualmente; no prueban un sensor activo.

## Pantallas Android

1. **Inicio de sesión:** registro, login y recuperación de contraseña por correo. Antes de abrir el Dashboard se intenta leer una ruta protegida por las reglas para detectar falta de autorización; la autorización real la aplica Firebase.
2. **Monitoreo:** temperatura, humedad, rack, ventilador, LED, alarma, estado de Firebase, estado ESP32 basado en heartbeat y fecha de última telemetría declarada. Evaluación de alertas con temperaturas mínima y máxima actuales.
3. **Control remoto:** comandos `DOOR_OPEN`, `DOOR_CLOSE`, `FAN_ON`, `FAN_OFF`, `LED_ON`, `LED_OFF`, `ALARM_ON`, `ALARM_OFF`. En v1 se escriben en `serverguard/comando` y se evita sobrescribir un comando pendiente. La confirmación v1 es informativa y no tiene correlación inequívoca.
4. **Historial anterior:** permite consultar los últimos 30 registros de `historial/sala_principal`, con formatos numéricos tolerantes y alertas calculadas con los límites actuales. **No es todavía historial automático del nuevo ESP32**.

### Alertas

El Dashboard evalúa temperatura fuera de límites, rack abierto, alarma activada y alerta de dispositivo. Las **notificaciones locales son opcionales**, requieren habilitación explícita y, en Android 13+, permiso `POST_NOTIFICATIONS`. Se generan mientras el Dashboard está activo y el heartbeat del ESP32 es reciente; no equivalen a Firebase Cloud Messaging ni funcionan como monitoreo persistente en segundo plano.

## Estructura Firebase

```text
/autorizados/{uid}: true             # administrado por Firebase Console/Admin
/dispositivos/{uid}: true            # identidad del ESP32 autorizada
/serverguard/telemetria              # escribe ESP32
/serverguard/configuracion          # actualiza Android
/serverguard/comando                 # v1: String; Android envía y ESP32 devuelve NONE
/serverguard/confirmacion            # v1: último resultado publicado por ESP32
/serverguard/v2/solicitudes/{id}     # v2 preparado, creado por Android
/serverguard/v2/respuestas/{id}      # v2 preparado, creado por ESP32
/historial/sala_principal/{id}       # historial heredado, no el nuevo ESP32
```

**Inicio recomendado para nuevos integrantes:** [Guía de continuación del equipo](docs/GUIA_CONTINUACION_EQUIPO.md). Documentos técnicos: [Integración v1](docs/INTEGRACION_WOKWI.md), [protocolo v2](docs/PROTOCOLO_V2.md) y [pruebas Android](docs/PRUEBAS_ANDROID.md).

**Importante:** Firebase Realtime Database no conserva rutas vacías. No crear manualmente `{id}` como clave ni guardar cadenas vacías para mantener carpetas. Las reglas no deben hacerse públicas. No incluir contraseñas, tokens de acceso o claves privadas en el repositorio.

## Puesta en marcha

1. Abrir el repositorio en Android Studio con el Android SDK correspondiente.
2. Colocar `google-services.json` válido en `app/` (archivo privado; está ignorado por Git).
3. Habilitar Firebase Authentication (correo/contraseña) y usar Realtime Database.
4. Publicar las reglas autorizadas del proyecto y registrar el UID de la cuenta Android en `/autorizados/{uid}`.
5. Compilar desde PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

6. Iniciar sesión en emulador o dispositivo. Una cuenta recién creada **no queda autorizada automáticamente**; el responsable de Firebase debe agregar su UID.
7. Para probar v1, asegurar que `/serverguard/comando` sea `"NONE"`; enviar una orden desde Control remoto, comprobarla en Firebase y simular confirmación únicamente durante pruebas identificadas como simulación.

## Trabajo pendiente de integración

- Autenticación real del ESP32 con identidad independiente, Wi-Fi, telemetría y heartbeat.
- Firmware para sensores/actuadores (incluido servo de puerta), límites dinámicos y respuestas.
- Implementar v2 en firmware; habilitar su uso en Android **después** de probar v2 extremo a extremo. No emitir el mismo comando a v1 y v2 simultáneamente.
- Diseñar y publicar reglas para historial del nuevo ESP32, almacenar registros periódicos y después construir gráficos históricos.
- Pruebas en dispositivo, casos de desconexión, permisos, errores y límites de temperatura.
- Notificaciones persistentes en segundo plano requieren arquitectura adicional; no se prometen en esta versión.

## Seguridad

Las reglas de Realtime Database deciden qué UID puede leer o escribir. El control de acceso de la UI es complementario. Las rutas `/autorizados` y `/dispositivos` no son editables por el cliente Android. La firma de confirmación v1 no valida identidad de orden; v2 utiliza identificadores únicos, aunque la ejecución física aún debe corroborarse con telemetría.

**Proyecto académico:** ninguna parte de la implementación supone certificación industrial o de cumplimiento normativo.
