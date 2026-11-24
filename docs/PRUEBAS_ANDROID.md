# Plan de pruebas Android — ServerGuardIoT

**Versión:** rama `feature/unidad3-control-wifi`. Estas pruebas deben ejecutarse después de hacer `git pull` y **no se declaran exitosas hasta verificarlas**.

## Compilación y pruebas locales

En PowerShell desde la raíz del proyecto:

```powershell
git status
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
```

Verificar `BUILD SUCCESSFUL` en ambas tareas. Las pruebas `EstadoMonitoreoTest.kt` comprueban temperatura alta/baja, rack abierto, estado normal, datos parciales y heartbeat antiguo.

## Pruebas funcionales sin ESP32 (pueden realizarse ahora)

| ID | Acción | Resultado esperado |
|---|---|---|
| AND-01 | Iniciar sesión con UID autorizado | Permite abrir el Dashboard |
| AND-02 | Iniciar sesión con UID no registrado en `/autorizados` | Rechaza acceso a datos protegidos y no abre el Dashboard |
| AND-03 | Solicitar recuperación de contraseña | Envía la solicitud a Firebase Auth y presenta mensaje informativo |
| AND-04 | Abrir Dashboard con telemetría de prueba antigua y sin heartbeat | Muestra datos, pero indica que ESP32 no está verificado; no envía notificación crítica |
| AND-05 | Cambiar `temperatura_minima` y `temperatura_maxima` desde Android | Firebase guarda los nuevos valores y UI refleja límites (la ejecución en ESP32 queda pendiente) |
| AND-06 | Introducir mínima mayor o igual a máxima | Rechaza el cambio sin escritura |
| AND-07 | Enviar `LED_ON` en v1 con `comando: "NONE"` | `comando` cambia a `LED_ON` y los botones se bloquean |
| AND-08 | Observar v1 con un comando existente | No sobrescribe el comando pendiente |
| AND-09 | Consultar Historial anterior | Muestra los registros heredados con fecha y unidades, o un estado vacío explícito |
| AND-10 | Activar/desactivar alertas locales | La preferencia cambia y se solicita permiso cuando Android lo requiere |

**Precaución en AND-07/08:** la limpieza manual de `comando: "NONE"` en Firebase Console es una **simulación de prueba** y no demuestra ejecución del actuador. No modificar `telemetria` para fingir que Wokwi actuó.

## Pruebas reservadas a la integración con Wokwi

| ID | Acción | Resultado esperado |
|---|---|---|
| IOT-01 | Iniciar ESP32 autenticado | Publica heartbeat y mediciones propias |
| IOT-02 | Detener ESP32 | El Dashboard marca heartbeat antiguo después de ~15 segundos |
| IOT-03 | Forzar temperatura fuera de límites mientras ESP32 está activo | Dashboard identifica alerta y, si se activaron, muestra notificaciones locales sin repetición continua |
| IOT-04 | Abrir/cerrar rack y accionar FAN/LED/ALARM | Estado real del firmware se refleja en telemetría |
| IOT-05 | Enviar comando v2 | Solicitud con ID y respuesta con **mismo ID**, resultado visible en Android después de activar v2 |
| IOT-06 | Desconectar red o intentar escribir con UID no autorizado | Reglas rechazan escrituras indebidas; app refleja error |
| IOT-07 | Reiniciar ESP32 y repetir lectura de solicitudes | No ejecuta dos veces la misma acción si ya existe respuesta |
| IOT-08 | Guardar registros históricos del ESP32 | El nuevo historial de telemetría y sus gráficos se habilitan después de acordar esquema y reglas |

## Evidencias para entrega

Guardar capturas de Dashboard, Control remoto, Historial, prueba de login autorizado/no autorizado, resultados Gradle y Firebase. No mostrar UID completo innecesariamente, contraseñas, tokens ni credenciales del dispositivo en capturas públicas.

**Nota:** no se ha configurado FCM ni un servicio persistente de monitoreo en segundo plano; las alertas locales de esta etapa solo se generan mientras el Dashboard está activo.
