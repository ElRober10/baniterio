package com.baniterio.app.data

import com.baniterio.app.data.dto.AvatarResumen
import com.baniterio.app.data.dto.GuardarPerfilRequest
import com.baniterio.app.data.dto.PerfilResponse
import com.baniterio.app.data.dto.TarjetaMiembroResponse

/**
 * Editor de perfil del miembro y sección Miembros (rutas bajo `/api/v1/perfil`
 * y `/api/v1/miembros`). Misma mecánica que [AdminRepository]: Bearer de
 * [SesionHolder], nunca lanza por errores HTTP esperados, relanza
 * `CancellationException`.
 */
interface PerfilRepository {
    suspend fun miPerfil(): ResultadoPerfil<PerfilResponse>
    suspend fun guardar(req: GuardarPerfilRequest): ResultadoPerfil<PerfilResponse>

    /** Sube la foto y devuelve su `imagenRef` (para el `imagenRef` del `PUT`). */
    suspend fun subirFoto(foto: FotoElegida): ResultadoPerfil<String>
    suspend fun avatares(): ResultadoPerfil<List<AvatarResumen>>
    suspend fun aceptarPareja(): ResultadoPerfil<Unit>
    suspend fun rechazarPareja(): ResultadoPerfil<Unit>
    suspend fun romperPareja(): ResultadoPerfil<Unit>
    suspend fun miembros(): ResultadoPerfil<List<TarjetaMiembroResponse>>
}
