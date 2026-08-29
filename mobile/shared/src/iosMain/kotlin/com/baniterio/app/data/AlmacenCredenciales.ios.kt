package com.baniterio.app.data

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.value
import platform.CoreFoundation.CFDictionaryCreate
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Foundation.CFBridgingRelease
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSData
import platform.Foundation.NSString
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleWhenUnlockedThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/**
 * Implementación iOS con Keychain (Security framework, vía cinterop).
 * Servicio "com.baniterio.app.credenciales", dos items generic-password
 * (cuentas "telefono" y "password"), valor como NSData UTF-8.
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual class AlmacenCredenciales {

    actual fun guardar(telefono: String, password: String) {
        escribir(CUENTA_TELEFONO, telefono)
        escribir(CUENTA_PASSWORD, password)
    }

    actual fun leer(): Credenciales? {
        val t = leerCuenta(CUENTA_TELEFONO) ?: return null
        val p = leerCuenta(CUENTA_PASSWORD) ?: return null
        return Credenciales(t, p)
    }

    actual fun borrar() {
        borrarCuenta(CUENTA_TELEFONO)
        borrarCuenta(CUENTA_PASSWORD)
    }

    actual val hayCredenciales: Boolean
        get() = leerCuenta(CUENTA_TELEFONO) != null

    // guardar = upsert: se borra el item previo y se vuelve a añadir.
    private fun escribir(cuenta: String, valor: String) {
        borrarCuenta(cuenta)
        val datosCf = CFBridgingRetain(valor.aNSData())
        try {
            conQuery(
                cuenta,
                listOf(
                    kSecValueData to datosCf,
                    // Sin esto el item usa kSecAttrAccessibleWhenUnlocked, que SÍ entra
                    // en los backups de iCloud/iTunes: la contraseña saldría del
                    // dispositivo. ThisDeviceOnly lo deja fuera del backup.
                    kSecAttrAccessible to kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
                ),
            ) { query ->
                SecItemAdd(query, null)
            }
        } finally {
            CFRelease(datosCf)
        }
    }

    private fun leerCuenta(cuenta: String): String? = conQuery(
        cuenta,
        listOf(
            kSecReturnData to kCFBooleanTrue,
            kSecMatchLimit to kSecMatchLimitOne,
        ),
    ) { query ->
        memScoped {
            val resultado = alloc<CFTypeRefVar>()
            val estado = SecItemCopyMatching(query, resultado.ptr)
            if (estado != 0) {
                null
            } else {
                val cfData = resultado.value
                (CFBridgingRelease(cfData) as? NSData)?.aKString()
            }
        }
    }

    private fun borrarCuenta(cuenta: String) {
        conQuery(cuenta, emptyList()) { query -> SecItemDelete(query) }
    }

    private inline fun <T> conQuery(
        cuenta: String,
        extra: List<Pair<CFStringRef?, CFTypeRef?>>,
        bloque: (CFDictionaryRef?) -> T,
    ): T {
        val servicioCf = cfString(SERVICIO)
        val cuentaCf = cfString(cuenta)
        val pares = buildList<Pair<CFStringRef?, CFTypeRef?>> {
            add(kSecClass to kSecClassGenericPassword)
            add(kSecAttrService to servicioCf)
            add(kSecAttrAccount to cuentaCf)
            addAll(extra)
        }
        val dict = cfDictionary(pares)
        try {
            return bloque(dict)
        } finally {
            CFRelease(dict)
            CFRelease(servicioCf)
            CFRelease(cuentaCf)
        }
    }

    private fun cfDictionary(pares: List<Pair<CFStringRef?, CFTypeRef?>>): CFDictionaryRef? =
        memScoped {
            val claves = allocArrayOf(pares.map { it.first })
            val valores = allocArrayOf(pares.map { it.second })
            CFDictionaryCreate(
                kCFAllocatorDefault,
                claves.reinterpret(),
                valores.reinterpret(),
                pares.size.convert(),
                kCFTypeDictionaryKeyCallBacks.ptr,
                kCFTypeDictionaryValueCallBacks.ptr,
            )
        }

    private fun cfString(texto: String): CFStringRef? =
        CFStringCreateWithCString(kCFAllocatorDefault, texto, kCFStringEncodingUTF8)

    private fun String.aNSData(): NSData {
        val bytes = encodeToByteArray()
        return memScoped {
            NSData.create(bytes = allocArrayOf(bytes), length = bytes.size.convert())
        }
    }

    private fun NSData.aKString(): String? =
        NSString.create(this, NSUTF8StringEncoding)?.toString()

    private companion object {
        const val SERVICIO = "com.baniterio.app.credenciales"
        const val CUENTA_TELEFONO = "telefono"
        const val CUENTA_PASSWORD = "password"
    }
}
