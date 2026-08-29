package com.baniterio.app.data

/** URL base de la API, incluido el prefijo /api/v1. Distinta por plataforma:
 *  el emulador de Android ve el localhost del PC en 10.0.2.2; el simulador de
 *  iOS lo ve en localhost. Un dispositivo físico necesita la IP LAN del PC. */
expect val API_BASE_URL: String
