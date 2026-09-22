package com.baniterio.app.data

// Apunta al backend de producción (bañiterio.es), detrás del proxy /api/ del
// frontend (ver front/nginx.conf). Para desarrollo local contra el backend en
// marcha en el PC (el simulador de iOS ve el localhost del Mac): comenta esta
// línea y descomenta la de abajo.
actual val API_BASE_URL: String = "https://xn--baiterio-e3a.es/api/v1"
// actual val API_BASE_URL: String = "http://localhost:8080/api/v1"
