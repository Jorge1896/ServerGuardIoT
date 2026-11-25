
#include <Arduino.h>
#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiClientSecure.h>
#include <ArduinoJson.h>
#include <DHT.h>
#include <ESP32Servo.h>
#include <time.h>
#include "firebase_ca.h"
#include "firebase_token.h"

// ==========================================
// CONFIGURACION DE FIREBASE
// ==========================================

const String DATABASE_URL =
    "https://serverguardiot-default-rtdb.firebaseio.com";

String idToken = FIREBASE_ID_TOKEN;  // VARIABLE PARA EL TOKEN DE FIREBASE, SE DEBE REEMPLAZAR CON EL TOKEN REAL

// ==========================================
// MAPA DE PINES - MARINA
// ==========================================

#define PIN_DHT        4
#define PIN_BUZZER     14
#define PIN_SERVO      18
#define PIN_RACK_SW    19
#define PIN_FAN_LED    25
#define PIN_IND_LED    26
#define PIN_ALARM_LED  27

#define DHTTYPE DHT22

DHT dht(PIN_DHT, DHTTYPE);
Servo servoPuerta;

// ==========================================
// CONFIGURACION DEL SISTEMA
// ==========================================

float tempMinima = 18.0;
float tempMaxima = 28.0;

const float HISTERESIS = 2.0;

// ==========================================
// ESTADOS DEL SISTEMA
// ==========================================

float temperatura = 24.0;
float humedad = 50.0;

bool puertaComandadaAbierta = false;
bool rackAbierto = false;
bool ventiladorEncendido = false;
bool ventiladorModoManual = false;
bool ledEncendido = false;
bool alarmaEncendida = false;
bool alertaActiva = false;

// ==========================================
// INTERVALOS DE EJECUCION
// ==========================================

unsigned long ultimoReporte = 0;
unsigned long ultimaConsulta = 0;
unsigned long ultimaConfiguracion = 0;

const unsigned long INTERVALO_REPORTE = 3000;
const unsigned long INTERVALO_COMANDOS = 1000;
const unsigned long INTERVALO_CONFIG = 15000;

// ==========================================
// CONEXION WIFI
// ==========================================

void conectarWiFi() {
    Serial.println("Conectando a WiFi...");

    WiFi.begin("Wokwi-GUEST", "", 6);

    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }

    Serial.println("\nWiFi conectado correctamente");

    Serial.print("IP del ESP32: ");
    Serial.println(WiFi.localIP());

    Serial.print("IP del Gateway: ");
    Serial.println(WiFi.gatewayIP());
}

// ==========================================
// SINCRONIZACION DE HORA
// ==========================================

bool sincronizarHora() {
    configTime(0, 0, "pool.ntp.org", "time.google.com");

    struct tm hora;

    if (!getLocalTime(&hora, 15000)) {
        Serial.println("Error al sincronizar la hora");
        return false;
    }

    Serial.println("Hora sincronizada correctamente");
    Serial.println(&hora, "%Y-%m-%d %H:%M:%S UTC");

    return true;
}

// ==========================================
// SOLICITUDES HTTP A FIREBASE
// ==========================================

bool firebaseGET(const String& ruta, String& respuesta) {
    WiFiClientSecure cliente;
    cliente.setCACert(ROOT_CA);

    HTTPClient http;

    String url = DATABASE_URL + ruta +
                 ".json?auth=" + idToken;

    if (!http.begin(cliente, url)) {
        return false;
    }

    http.setTimeout(5000);

    int codigo = http.GET();

    if (codigo == 200) {
        respuesta = http.getString();
    } else {
        Serial.print("Error GET: ");
        Serial.println(codigo);
    }

    http.end();

    return codigo == 200;
}

bool firebasePUT(const String& ruta, const String& json) {
    WiFiClientSecure cliente;
    cliente.setCACert(ROOT_CA);

    HTTPClient http;

    String url = DATABASE_URL + ruta +
                 ".json?auth=" + idToken;

    if (!http.begin(cliente, url)) {
        return false;
    }

    http.setTimeout(5000);
    http.addHeader("Content-Type", "application/json");

    int codigo = http.PUT(json);

    Serial.print("PUT ");
    Serial.print(ruta);
    Serial.print(" -> HTTP ");
    Serial.println(codigo);

    http.end();

    return codigo == 200;
}

// ==========================================
// ACTUALIZAR ACTUADORES - MARINA
// ==========================================

void actualizarActuadores() {
    digitalWrite(PIN_FAN_LED,
                 ventiladorEncendido ? HIGH : LOW);

    digitalWrite(PIN_IND_LED,
                 ledEncendido ? HIGH : LOW);

    digitalWrite(PIN_ALARM_LED,
                 alarmaEncendida ? HIGH : LOW);

    if (alarmaEncendida) {
        tone(PIN_BUZZER, 1000);
    } else {
        noTone(PIN_BUZZER);
    }

    servoPuerta.write(
        puertaComandadaAbierta ? 90 : 0
    );
}

void actualizarEstadoRack() {
    bool sensorFisicoAbierto =
        digitalRead(PIN_RACK_SW) == LOW;

    rackAbierto =
        puertaComandadaAbierta || sensorFisicoAbierto;

    alertaActiva =
        temperatura >= tempMaxima ||
        temperatura <= tempMinima ||
        rackAbierto ||
        alarmaEncendida;
}

// ==========================================
// LEER CONFIGURACION DE FIREBASE
// ==========================================

void leerConfiguracion() {
    String respuesta;

    if (!firebaseGET("/serverguard/configuracion",
                     respuesta)) {
        return;
    }

    JsonDocument datos;

    if (deserializeJson(datos, respuesta)) {
        Serial.println("Error al interpretar configuracion");
        return;
    }

    if (!datos["temperatura_minima"].is<float>() ||
        !datos["temperatura_maxima"].is<float>()) {
        return;
    }

    float nuevaMin = datos["temperatura_minima"];
    float nuevaMax = datos["temperatura_maxima"];

    if (nuevaMin >= -40.0 &&
        nuevaMax <= 80.0 &&
        nuevaMin < nuevaMax) {

        tempMinima = nuevaMin;
        tempMaxima = nuevaMax;

        Serial.print("Temperatura minima: ");
        Serial.println(tempMinima);

        Serial.print("Temperatura maxima: ");
        Serial.println(tempMaxima);
    }
}

// ==========================================
// SENSORES Y CONTROL AUTOMATICO - MARINA
// ==========================================

void leerSensoresYControlAuto() {
    float t = dht.readTemperature();
    float h = dht.readHumidity();

    if (!isnan(t) && t >= -40.0 && t <= 80.0) {
        temperatura = t;
    }

    if (!isnan(h) && h >= 0.0 && h <= 100.0) {
        humedad = h;
    }

    if (!ventiladorModoManual) {
        if (temperatura >= tempMaxima) {
            ventiladorEncendido = true;
        } else if (temperatura <= tempMaxima - HISTERESIS) {
            ventiladorEncendido = false;
        }
    } else if (temperatura >= tempMaxima + 4.0) {
        ventiladorModoManual = false;
        ventiladorEncendido = true;
    }

    actualizarEstadoRack();
    actualizarActuadores();
}

// ==========================================
// ENVIAR TELEMETRIA A FIREBASE
// ==========================================

void enviarTelemetria() {
    JsonDocument datos;

    datos["temperatura"] = temperatura;
    datos["humedad"] = humedad;
    datos["rack_abierto"] = rackAbierto;
    datos["ventilador"] = ventiladorEncendido;
    datos["led"] = ledEncendido;
    datos["alarma"] = alarmaEncendida;
    datos["alerta_activa"] = alertaActiva;
    datos["estado_conexion"] = "CONECTADO";

    datos["ultima_conexion"][".sv"] = "timestamp";

    String json;
    serializeJson(datos, json);

    if (firebasePUT("/serverguard/telemetria", json)) {
        Serial.println("Telemetria enviada correctamente");

        Serial.print("Temperatura: ");
        Serial.println(temperatura);

        Serial.print("Humedad: ");
        Serial.println(humedad);
    }
}

// ==========================================
// PROCESAR COMANDOS V1 - MARINA
// ==========================================

String procesarComandoV1(const String& cmd) {

    if (cmd == "FAN_ON") {
        ventiladorEncendido = true;
        ventiladorModoManual = true;

    } else if (cmd == "FAN_OFF") {
        ventiladorEncendido = false;
        ventiladorModoManual = true;

    } else if (cmd == "LED_ON") {
        ledEncendido = true;

    } else if (cmd == "LED_OFF") {
        ledEncendido = false;

    } else if (cmd == "ALARM_ON") {
        alarmaEncendida = true;

    } else if (cmd == "ALARM_OFF") {
        alarmaEncendida = false;

    } else if (cmd == "DOOR_OPEN") {
        puertaComandadaAbierta = true;

    } else if (cmd == "DOOR_CLOSE") {
        puertaComandadaAbierta = false;

    } else {
        return "RECHAZADO";
    }

    actualizarActuadores();
    actualizarEstadoRack();

    return "EJECUTADO";
}

// ==========================================
// RECIBIR COMANDOS DESDE FIREBASE
// ==========================================

void consultarComandos() {
    String respuesta;

    if (!firebaseGET("/serverguard/comando",
                     respuesta)) {
        return;
    }

    JsonDocument datos;

    if (deserializeJson(datos, respuesta)) {
        return;
    }

    if (!datos.is<const char*>()) {
        return;
    }

    String comando = datos.as<String>();

    comando.trim();
    comando.toUpperCase();

    if (comando == "" || comando == "NONE") {
        return;
    }

    Serial.print("Comando recibido: ");
    Serial.println(comando);

    String resultado = procesarComandoV1(comando);

    // Publicar confirmacion
    JsonDocument confirmacion;

    confirmacion["comando"] = comando;
    confirmacion["resultado"] = resultado;

    String json;
    serializeJson(confirmacion, json);

    if (firebasePUT("/serverguard/confirmacion", json)) {

        // Limpiar comando solo despues de confirmar
        firebasePUT("/serverguard/comando", "\"NONE\"");

        Serial.print("Resultado: ");
        Serial.println(resultado);
    }

    // Informar inmediatamente el nuevo estado
    enviarTelemetria();
}

// ==========================================
// SETUP
// ==========================================

void setup() {
    Serial.begin(115200);

    dht.begin();

    pinMode(PIN_RACK_SW, INPUT_PULLUP);
    pinMode(PIN_FAN_LED, OUTPUT);
    pinMode(PIN_IND_LED, OUTPUT);
    pinMode(PIN_ALARM_LED, OUTPUT);
    pinMode(PIN_BUZZER, OUTPUT);

    servoPuerta.attach(PIN_SERVO, 500, 2400);

    actualizarActuadores();

    conectarWiFi();

    if (!sincronizarHora()) {
        Serial.println("Reiniciando por error de hora");
        delay(2000);
        ESP.restart();
    }

    leerConfiguracion();
    leerSensoresYControlAuto();
    enviarTelemetria();

    ultimoReporte = millis();
    ultimaConsulta = millis();
    ultimaConfiguracion = millis();

    Serial.println("==============================");
    Serial.println("SERVERGUARD IOT INICIADO");
    Serial.println("Esperando comandos de Android");
    Serial.println("==============================");
}

// ==========================================
// LOOP
// ==========================================

void loop() {
    if (WiFi.status() != WL_CONNECTED) {
        conectarWiFi();
    }

    unsigned long ahora = millis();

    if (ahora - ultimaConsulta >= INTERVALO_COMANDOS) {
        ultimaConsulta = ahora;
        consultarComandos();
    }

    if (ahora - ultimoReporte >= INTERVALO_REPORTE) {
        ultimoReporte = ahora;

        leerSensoresYControlAuto();
        enviarTelemetria();
    }

    if (ahora - ultimaConfiguracion >= INTERVALO_CONFIG) {
        ultimaConfiguracion = ahora;
        leerConfiguracion();
    }

    delay(10);
}
