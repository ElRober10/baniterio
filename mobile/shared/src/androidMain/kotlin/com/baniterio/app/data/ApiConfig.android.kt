package com.baniterio.app.data

// Apunta al backend de producción (bañiterio.es), detrás del proxy /api/ del
// frontend (ver front/nginx.conf). Para desarrollo local contra el backend en
// marcha en el PC, comenta esta línea y descomenta una de las de abajo.
actual val API_BASE_URL: String = "https://xn--baiterio-e3a.es/api/v1"

// Emulador de Android Studio contra backend local: "http://10.0.2.2:8080/api/v1"
// (10.0.2.2 = el localhost del PC visto desde el emulador).
// Móvil físico contra backend local: la IP LAN del PC (mismo Wi-Fi).
// actual val API_BASE_URL: String = "http://192.168.1.138:8080/api/v1"
