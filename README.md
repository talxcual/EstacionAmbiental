# 🌡️ Estación Ambiental IoT - Plantilla Base (Telemetry & Control)

![Android](https://img.shields.io/badge/Android-Native-3DDC84?style=flat&logo=android)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat&logo=kotlin)
![Firebase](https://img.shields.io/badge/Backend-Firebase-FFCA28?style=flat&logo=firebase)

## 📄 Descripción del Proyecto

Este proyecto es una **plantilla base ("esqueleto")** para una aplicación móvil Android nativa en **Kotlin** orientada al **Internet de las Cosas (IoT)**, específicamente para la visualización de telemetría y control remoto de dispositivos de monitoreo ambiental.

Ha sido refactorizada a partir de un proyecto previo para limpiar todo el dominio antiguo y establecer una arquitectura limpia y modular de paquetes listos para producción.

## 🚀 Funcionalidades Preservadas & Nuevas

### 1. Arquitectura de Autenticación (Core Preservado)
* **Acceso Multi-plataforma:** Autenticación robusta a través de **Correo/Contraseña** y **Google Sign-In**.
* **Persistencia de Sesión:** Carga el estado del usuario logueado al iniciar la aplicación.
* **Perfiles en Nube:** Creación automática de documentos de usuario en **Firebase Firestore** vinculados al UID único.

### 2. Estructura y Flujo de IoT (Nuevos)
* **Dashboard Principal:** Se creó la nueva pantalla `DashboardActivity` vacía como destino posterior a un inicio de sesión exitoso.
* **Estructura Modular de Paquetes:**
  * `models/`: Clases de datos para mapeo de base de datos.
  * `network/`: Conectores para comunicación HTTP, MQTT o Firebase Realtime Database.
  * `ui/`: Vistas, fragmentos, adaptadores y actividades.
  * `utils/`: Utilidades y helpers.

### 3. Modelos de Datos Iniciales (Firebase Realtime Database)
* **`TelemetryData`:** Mapeo de datos recibidos desde el hardware en el nodo `estacion/sensores`:
  * `temperatura` (Double)
  * `humedad` (Double)
  * `co2` (Int)
  * `tvoc` (Int)
  * `bridge_ts` (Long/Unix Timestamp)
* **`StationControl`:** Datos para control bidireccional (App -> Hardware):
  * `alarmaActiva` (Boolean) - Estado de la alarma.
  * `configuracionPomodoro` (Int) - Minutos/Configuración.
  * `extractorAutomatico` (Boolean) / `extractorManual` (Boolean) - Modos de activación del Extractor de Aire.

## 🛠️ Tecnologías Utilizadas

* **Lenguaje principal:** Kotlin.
* **Componentes de UI:** Android AppCompat, XML Layouts, Material Design.
* **Firebase Services:**
  * **Firebase Authentication:** Registro e Inicio de sesión.
  * **Cloud Firestore:** Gestión de perfiles y marcas de tiempo de usuario.
  * **Firebase Realtime Database (BOM):** Listo para telemetría en tiempo real.

## 🔧 Configuración e Instalación

1. **Configurar Firebase:**
   * Registra la aplicación en tu consola de Firebase con el paquete: `com.ktasoporte.estacionambiental`.
   * Descarga el archivo `google-services.json` y colócalo en la carpeta `/app` del proyecto.
   * Habilita **Authentication** y **Cloud Firestore** en la consola de Firebase.

2. **Compilar y Ejecutar:**
   * Abre el proyecto en **Android Studio**.
   * Espera la sincronización de Gradle y ejecuta la app.
