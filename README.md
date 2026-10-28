# ServerGuardIoT

**Sistema de monitoreo IoT para salas de servidores**

Aplicación móvil desarrollada en Android Studio utilizando Kotlin y Firebase. Su propósito es simular un sistema de monitoreo ambiental y de seguridad para racks de servidores, permitiendo visualizar mediciones, detectar condiciones de riesgo y sincronizar información entre dispositivos Android.

## 1. Descripción del proyecto

ServerGuardIoT aborda la necesidad de supervisar las condiciones ambientales y de seguridad de una sala de servidores.

El prototipo simula sensores IoT sin utilizar Arduino ni hardware físico. Los datos se generan mediante la aplicación Android y se almacenan en Firebase Realtime Database.

El sistema permite que dos dispositivos Android autorizados consulten y actualicen la misma información mediante una conexión a Internet.

## 2. Tecnologías utilizadas

- **Android Studio:** entorno de desarrollo.
- **Kotlin:** lenguaje de programación.
- **XML:** diseño de interfaces.
- **Firebase Authentication:** autenticación mediante correo y contraseña.
- **Firebase Realtime Database:** almacenamiento y sincronización de mediciones.
- **Android Notification API:** notificaciones locales ante situaciones de riesgo.
- **Git y GitHub:** control de versiones y repositorio del código.

## 3. Sensores simulados

| Componente | Funcionalidad |
|---|---|
| DHT22 | Simulación de temperatura y humedad |
| LDR | Simulación de apertura y cierre del rack |
| Ventilador | Activación automática cuando la temperatura supera los 28 °C |
| Indicadores de alerta | Visualización de condiciones normales y peligrosas |

Los sensores son simulaciones realizadas mediante software. No existe una conexión física con Arduino u otros dispositivos IoT.

## 4. Funcionalidades principales

### Autenticación

- Registro de usuarios mediante correo y contraseña.
- Inicio y cierre de sesión.
- Persistencia de la sesión mediante Firebase Authentication.
- Control de acceso a los datos mediante UID autorizados.

### Monitoreo

- Visualización de temperatura y humedad.
- Consulta del estado del rack.
- Visualización del estado del ventilador.
- Fecha y hora de la última actualización.
- Generación manual de lecturas simuladas.

### Alertas

- Alerta de temperatura al superar los 28 °C.
- Activación automática del ventilador.
- Alerta de seguridad cuando se abre el rack.
- Notificaciones locales de Android cuando se detectan nuevas condiciones de alerta mientras el panel está activo.

### Historial

- Almacenamiento de mediciones anteriores.
- Registro de fecha y hora.
- Consulta de las últimas 30 mediciones.
- Visualización del estado ambiental y de seguridad de cada registro.

### Sincronización

La aplicación utiliza Firebase Realtime Database para sincronizar los datos entre dispositivos autorizados.

Los cambios realizados desde un dispositivo se reflejan automáticamente en otro mientras ambos mantienen conexión a Internet y las pantallas correspondientes están escuchando los datos.

## 5. Arquitectura del sistema

La arquitectura está compuesta por tres elementos:

1. **Aplicación Android:** presenta las interfaces, permite simular sensores y muestra las alertas.
2. **Firebase Authentication:** administra la autenticación de usuarios.
3. **Firebase Realtime Database:** almacena las mediciones actuales, el historial y las autorizaciones.

La comunicación se realiza mediante Internet, utilizando las conexiones seguras proporcionadas por los SDK de Firebase.

No se utiliza Bluetooth ni comunicación Wi-Fi directa entre los teléfonos.

## 6. Estructura de Firebase

La base de datos utiliza las siguientes rutas:

```text
autorizados/
    UID_USUARIO: true

salas/
    sala_principal/
        temperatura
        humedad
        rackAbierto
        ventiladorEncendido
        ultimaActualizacion

historial/
    sala_principal/
        ID_REGISTRO/
            temperatura
            humedad
            rackAbierto
            ventiladorEncendido
            ultimaActualizacion
```

Las reglas de seguridad restringen el acceso a las mediciones a usuarios autenticados cuyos UID hayan sido autorizados.

## 7. Seguridad

Se han implementado medidas relacionadas con los principios de seguridad IoT contemplados en ISO/IEC 27400:

- Autenticación mediante Firebase Authentication.
- Restricción de acceso mediante UID.
- Reglas de lectura y escritura en Realtime Database.
- Uso de conexiones cifradas mediante los SDK de Firebase.
- Exclusión de archivos de configuración local y credenciales privadas del repositorio.

Estas medidas forman parte de un prototipo académico y no constituyen una certificación de cumplimiento de ISO/IEC 27400.

## 8. Instalación y configuración

### Requisitos

- Android Studio.
- Android SDK compatible con el proyecto.
- Conexión a Internet.
- Proyecto Firebase con Authentication y Realtime Database habilitados.
- Dispositivo Android o emulador.

### Instalación

1. Clonar el repositorio:

```bash
git clone https://github.com/Jorge1896/ServerGuardIoT.git
```

2. Abrir la carpeta en Android Studio.
3. Sincronizar las dependencias de Gradle.
4. Registrar una aplicación Android en Firebase con el paquete `com.serverguard.iot`.
5. Descargar el archivo `google-services.json` y colocarlo dentro de `app/`.
6. Configurar Firebase Authentication con el proveedor correo electrónico/contraseña.
7. Crear Realtime Database y establecer reglas de seguridad equivalentes a las utilizadas en el proyecto.
8. Configurar la URL de Realtime Database en las Activities correspondientes.
9. Registrar y autorizar los UID de los usuarios de prueba.
10. Ejecutar la aplicación en un dispositivo Android.

El archivo `google-services.json` no se incluye en el repositorio público.

## 9. Pruebas realizadas

El prototipo fue probado utilizando:

- Emulador Android de Android Studio.
- Xiaomi Redmi Note 14 4G.

Se verificó el funcionamiento de:

- Registro e inicio de sesión.
- Generación de mediciones simuladas.
- Sincronización bidireccional mediante Firebase.
- Apertura y cierre del rack.
- Activación automática del ventilador.
- Almacenamiento y consulta del historial.
- Notificaciones ante nuevas condiciones de alerta.

Las pruebas se realizaron con usuarios autorizados y conexión a Internet.

## 10. Limitaciones

- No utiliza sensores físicos.
- No tiene integración con Arduino.
- La comunicación entre dispositivos se realiza mediante Firebase y no directamente por Bluetooth.
- Las notificaciones implementadas dependen de que el Dashboard esté activo y escuchando Firebase.
- El historial muestra las últimas 30 mediciones, aunque la base de datos puede conservar más registros.
- El proyecto corresponde a un prototipo académico, no a un sistema industrial certificado.

## 11. Posibles mejoras

- Incorporar sensores físicos.
- Integrar Firebase Cloud Messaging para notificaciones con la aplicación cerrada.
- Implementar roles diferenciados para administradores y operadores.
- Agregar gráficos de temperatura y humedad.
- Incorporar políticas de retención y exportación de datos.
- Ampliar las pruebas de seguridad.

---

**Proyecto académico — Aplicaciones Móviles para IoT, Unidad 2.**

Desarrollado con Android Studio, Kotlin y Firebase.