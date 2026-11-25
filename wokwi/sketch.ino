#include <DHT.h>
#include <ESP32Servo.h>

// ===== MAPA DE PINES (compartir con Luigi) =====
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

// ===== CONFIGURACIÓN (/serverguard/configuracion) =====
float tempMinima = 18.0;
float tempMaxima = 28.0;
const float HISTERESIS = 2.0;

// ===== ESTADOS DEL SISTEMA =====
float temperatura = 24.0;
float humedad = 50.0;
bool puertaComandadaAbierta = false;
bool rackAbierto = false;
bool ventiladorEncendido = false;
bool ventiladorModoManual = false;
bool ledEncendido = false;
bool alarmaEncendida = false;
bool alertaActiva = false;

unsigned long ultimoReporte = 0;
const unsigned long INTERVALO_REPORTE = 3000;

void actualizarActuadores() {
  digitalWrite(PIN_FAN_LED, ventiladorEncendido ? HIGH : LOW);
  digitalWrite(PIN_IND_LED, ledEncendido ? HIGH : LOW);
  digitalWrite(PIN_ALARM_LED, alarmaEncendida ? HIGH : LOW);

  if (alarmaEncendida) {
    tone(PIN_BUZZER, 1000);
  } else {
    noTone(PIN_BUZZER);
  }

  servoPuerta.write(puertaComandadaAbierta ? 90 : 0);
}

// Aplica nuevos límites validando las reglas de Firebase (-40..80 y min < max)
bool aplicarConfiguracion(float nuevaMin, float nuevaMax) {
  if (nuevaMin < -40.0 || nuevaMax > 80.0 || nuevaMin >= nuevaMax) {
    Serial.println("CONFIG RECHAZADA: rangos invalidos");
    return false;
  }
  tempMinima = nuevaMin;
  tempMaxima = nuevaMax;
  Serial.print("CONFIG ACTUALIZADA -> min: ");
  Serial.print(tempMinima, 1);
  Serial.print(" | max: ");
  Serial.println(tempMaxima, 1);
  return true;
}

// Flujo oficial del contrato v1:
// 1) Ignorar NONE -> 2) Ejecutar -> 3) Publicar /confirmacion -> 4) Devolver /comando a NONE
String procesarComandoV1(String cmd) {
  cmd.trim();
  cmd.replace("\"", ""); // Limpia comillas si viene directo de REST JSON ("FAN_ON")
  cmd.toUpperCase();

  if (cmd == "" || cmd == "NONE") {
    return "SIN_COMANDOS";
  }

  String resultado = "EJECUTADO";

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
    resultado = "RECHAZADO";
  }

  if (resultado == "EJECUTADO") {
    actualizarActuadores();
    // Actualiza rack_abierto de inmediato para que la telemetría coincida
    bool sensorFisicoAbierto = (digitalRead(PIN_RACK_SW) == LOW);
    rackAbierto = puertaComandadaAbierta || sensorFisicoAbierto;
  }

  // [INTEGRACION FIREBASE - PASO 1]: PUT a /serverguard/confirmacion
  String jsonConfirmacion = "{\"comando\":\"" + cmd + "\",\"resultado\":\"" + resultado + "\"}";
  Serial.println("CONFIRMACION -> " + jsonConfirmacion);

  // [INTEGRACION FIREBASE - PASO 2]: Solo despues de confirmar, PUT "\"NONE\"" a /serverguard/comando
  Serial.println("COMANDO -> restablecer /serverguard/comando a \"NONE\"");

  return resultado;
}

void leerSensoresYControlAuto() {
  float t = dht.readTemperature();
  float h = dht.readHumidity();

  if (!isnan(t) && t >= -40.0 && t <= 80.0) temperatura = t;
  if (!isnan(h) && h >= 0.0 && h <= 100.0) humedad = h;

  bool sensorFisicoAbierto = (digitalRead(PIN_RACK_SW) == LOW);
  rackAbierto = puertaComandadaAbierta || sensorFisicoAbierto;

  if (!ventiladorModoManual) {
    if (temperatura >= tempMaxima) {
      ventiladorEncendido = true;
    } else if (temperatura <= (tempMaxima - HISTERESIS)) {
      ventiladorEncendido = false;
    }
  } else if (temperatura >= tempMaxima + 4.0) {
    ventiladorModoManual = false;
    ventiladorEncendido = true;
  }

  alertaActiva = (temperatura >= tempMaxima) || (temperatura <= tempMinima) || rackAbierto || alarmaEncendida;
  actualizarActuadores();
}

String construirJsonTelemetria(unsigned long long timestampMs) {
  String json = "{";
  json += "\"temperatura\":" + String(temperatura, 1) + ",";
  json += "\"humedad\":" + String(humedad, 1) + ",";
  json += "\"rack_abierto\":" + String(rackAbierto ? "true" : "false") + ",";
  json += "\"ventilador\":" + String(ventiladorEncendido ? "true" : "false") + ",";
  json += "\"led\":" + String(ledEncendido ? "true" : "false") + ",";
  json += "\"alarma\":" + String(alarmaEncendida ? "true" : "false") + ",";
  json += "\"alerta_activa\":" + String(alertaActiva ? "true" : "false") + ",";
  json += "\"estado_conexion\":\"Conectado\",";
  json += "\"ultima_conexion\":" + String((unsigned long)(timestampMs / 1000)) + "000";
  json += "}";
  return json;
}

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

  Serial.println("=== ServerGuardIoT: Actuadores y comandos v1 listos ===");
  Serial.println("Comandos: FAN_ON, FAN_OFF, LED_ON, LED_OFF, ALARM_ON, ALARM_OFF, DOOR_OPEN, DOOR_CLOSE");
}

void loop() {
  if (Serial.available()) {
    String comando = Serial.readStringUntil('\n');
    procesarComandoV1(comando);
  }

  if (millis() - ultimoReporte >= INTERVALO_REPORTE) {
    ultimoReporte = millis();
    leerSensoresYControlAuto();
    // Timestamp de prueba local; Luigi lo reemplazara por hora NTP real en ms
    Serial.println("TELEMETRIA -> " + construirJsonTelemetria(1795550000000ULL));
  }
}