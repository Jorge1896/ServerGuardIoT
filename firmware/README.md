# ServerGuardIoT — Firmware integrado ESP32

## 1. Descripción

Firmware desarrollado para la Unidad 3 del proyecto ServerGuardIoT. Permite integrar un ESP32 simulado en Wokwi con Firebase Realtime Database y la aplicación Android del proyecto.

El sistema permite monitorear las condiciones ambientales de un rack de servidores y controlar remotamente sus actuadores mediante el protocolo V1.

## 2. Tecnologías utilizadas

- ESP32 Dev Module.
- Arduino Framework.
- PlatformIO.
- Visual Studio Code.
- Wokwi Simulator.
- Firebase Authentication.
- Firebase Realtime Database.
- ArduinoJson.
- DHT Sensor Library.
- ESP32Servo.
- HTTPS con validación de certificados TLS.

## 3. Distribución del trabajo

- **Jorge:** desarrollo de la aplicación Android, autenticación, interfaz y comunicación con Firebase.
- **Marina:** desarrollo del circuito Wokwi, sensores, actuadores y procesamiento local de comandos.
- **Luigi:** integración del firmware ESP32 con Firebase, comunicación HTTPS, telemetría, procesamiento remoto de comandos y pruebas de integración con Android.

La carpeta `wokwi/` conserva el desarrollo original de Marina, correspondiente al commit `a67594c` de la rama `feature/wokwi-actuadores`.

La carpeta `firmware/` contiene la versión integrada con Firebase.

## 4. Componentes y conexiones

| Componente | GPIO |
|---|---:|
| Sensor DHT22 | 4 |
| Buzzer | 14 |
| Servomotor de la puerta | 18 |
| Interruptor del rack | 19 |
| LED azul — Ventilador | 25 |
| LED verde — Iluminación | 26 |
| LED rojo — Alarma | 27 |

El ventilador se representa mediante un LED azul y la apertura de la puerta mediante un servomotor.

## 5. Requisitos de ejecución

- Visual Studio Code.
- Extensión PlatformIO IDE.
- Extensión Wokwi Simulator con licencia activa.
- Acceso autorizado al proyecto Firebase.
- Cuenta Firebase Authentication registrada para el ESP32.
- UID del dispositivo autorizado en `/dispositivos`.

## 6. Configuración del firmware

1. Clonar el repositorio.
2. Abrir la carpeta `firmware/` en Visual Studio Code.
3. Esperar la instalación de las dependencias de PlatformIO.
4. Copiar `include/firebase_token.example.h` como `include/firebase_token.h`.
5. Obtener un token de autenticación válido para el ESP32.
6. Reemplazar el valor de ejemplo por el token obtenido.
7. Compilar mediante PlatformIO: Build.
8. Ejecutar Wokwi: Start Simulator.
9. Iniciar la simulación.

El archivo `firebase_token.h` está excluido del control de versiones mediante `.gitignore`.

## 7. Autenticación del ESP32

La versión actual utiliza un token temporal de Firebase Authentication obtenido externamente mediante PowerShell.

Para obtenerlo, ejecutar:

```powershell
$apiKey = Read-Host "API Key de Firebase"
$email = "esp32@serverguardiot.com"
$password = Read-Host "Contrasena del ESP32" -AsSecureString

$body = @{
    email = $email
    password = [System.Net.NetworkCredential]::new("", $password).Password
    returnSecureToken = $true
} | ConvertTo-Json

try {
    $respuesta = Invoke-RestMethod `
        -Method Post `
        -Uri "https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey" `
        -ContentType "application/json" `
        -Body $body

    Write-Host "Autenticacion exitosa"
    Write-Host "Duracion:" $respuesta.expiresIn "segundos"

    $respuesta.idToken | Set-Clipboard
}
finally {
    Remove-Variable body,password -ErrorAction SilentlyContinue
}
```

Pegar el token obtenido en `include/firebase_token.h`.

El token tiene una duración habitual de 3600 segundos. Cuando expire, deberá renovarse.

Si se conserva la sesión de PowerShell, puede renovarse mediante:

```powershell
$nuevaRespuesta = Invoke-RestMethod `
    -Method Post `
    -Uri "https://securetoken.googleapis.com/v1/token?key=$apiKey" `
    -ContentType "application/x-www-form-urlencoded" `
    -Body @{
        grant_type = "refresh_token"
        refresh_token = $respuesta.refreshToken
    }

$nuevaRespuesta.id_token | Set-Clipboard
```

Después de actualizar el token, recompilar el firmware y reiniciar la simulación.

No publicar contraseñas, tokens ni credenciales privadas en GitHub.

## 8. Comunicación con Firebase

| Ruta | Función |
|---|---|
| `/serverguard/telemetria` | Publicación del estado del ESP32 |
| `/serverguard/configuracion` | Lectura de límites de temperatura |
| `/serverguard/comando` | Recepción de comandos V1 |
| `/serverguard/confirmacion` | Confirmación de comandos procesados |

Intervalos programados:

- Telemetría: 3000 ms.
- Consulta de comandos: 1000 ms.
- Consulta de configuración: 15000 ms.

La comunicación utiliza HTTPS con validación de certificados. El firmware sincroniza su reloj mediante NTP y utiliza el timestamp del servidor Firebase para registrar la última conexión.

## 9. Comandos implementados

| Comando | Acción |
|---|---|
| `DOOR_OPEN` | Abrir puerta del rack |
| `DOOR_CLOSE` | Cerrar puerta del rack |
| `FAN_ON` | Encender ventilador |
| `FAN_OFF` | Apagar ventilador |
| `LED_ON` | Encender iluminación |
| `LED_OFF` | Apagar iluminación |
| `ALARM_ON` | Activar alarma visual y sonora |
| `ALARM_OFF` | Desactivar alarma |

Después de procesar un comando, el ESP32 publica su confirmación y, si esta se escribe correctamente, solicita restablecer el comando a `NONE`.

## 10. Funcionalidades automáticas

- Lectura de temperatura y humedad mediante DHT22.
- Monitoreo del estado del rack.
- Activación automática del ventilador por temperatura elevada.
- Histéresis de 2 °C para el control automático del ventilador.
- Detección de condiciones de alerta.
- Publicación periódica de telemetría.
- Actualización de límites de temperatura desde Firebase.
- Confirmación de comandos remotos.

## 11. Pruebas de integración realizadas

Las pruebas se realizaron utilizando un teléfono físico Xiaomi Redmi 9A, Firebase Realtime Database y un ESP32 simulado mediante Wokwi en Visual Studio Code.

| Prueba | Resultado |
|---|---|
| Conexión WiFi del ESP32 | Exitosa |
| Sincronización NTP | Exitosa |
| Comunicación HTTPS con Firebase | Exitosa |
| Autenticación de Android | Exitosa |
| Autorización del dispositivo | Exitosa |
| Actualización de temperatura y humedad | Exitosa |
| Control automático del ventilador | Exitosa |
| Apertura y cierre remoto del rack | Exitosa |
| Encendido y apagado remoto del ventilador | Exitosa |
| Encendido y apagado remoto de iluminación | Exitosa |
| Activación y desactivación de alarma | Exitosa |
| Confirmación de comandos V1 | Exitosa |
| Detección de desconexión en Android | Exitosa |
| Renovación manual del token | Exitosa |
| Compilación con credenciales separadas | Exitosa |

## 12. Alcance y limitaciones

- La integración funcional utiliza el protocolo V1.
- El protocolo V2 no está implementado en este firmware.
- La autenticación autónoma del ESP32 no está implementada.
- El token temporal debe renovarse manualmente.
- Durante el desarrollo se observaron problemas de conexión TLS con los servicios de autenticación de Google desde Wokwi. Se utilizó PowerShell para obtener los tokens como solución alternativa.
- Las pruebas se realizaron con hardware simulado, no con un ESP32 físico.
- Se observaron bloqueos temporales del simulador que se resolvieron reiniciando la simulación. No se determinó su causa exacta.

## 13. Estado del proyecto

Integración funcional del protocolo V1 entre Android, Firebase y ESP32 completada y validada mediante pruebas manuales.

Flujo de comunicación:

Android ↔ Firebase Realtime Database ↔ ESP32 (Wokwi).