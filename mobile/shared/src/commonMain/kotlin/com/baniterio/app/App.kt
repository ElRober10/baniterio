package com.baniterio.app

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import coil3.network.ktor3.KtorNetworkFetcherFactory
import com.baniterio.app.data.Dependencias
import com.baniterio.app.nav.Screen
import com.baniterio.app.nav.SolicitudPrecarga
import com.baniterio.app.theme.BaniterioTheme
import com.baniterio.app.ui.admin.AdminBebidasScreen
import com.baniterio.app.ui.admin.AdminPagosScreen
import com.baniterio.app.ui.admin.AdminIndexScreen
import com.baniterio.app.ui.admin.AdminPermisosScreen
import com.baniterio.app.ui.admin.AdminSolicitudesScreen
import com.baniterio.app.ui.cuentas.CuentaDetalleScreen
import com.baniterio.app.ui.cuentas.CuentasScreen
import com.baniterio.app.ui.cuentas.VisorReciboScreen
import com.baniterio.app.ui.eventos.EditorEventoScreen
import com.baniterio.app.ui.eventos.EventoDetalleScreen
import com.baniterio.app.ui.eventos.EventosOcultosScreen
import com.baniterio.app.ui.eventos.EventosScreen
import com.baniterio.app.ui.eventos.ResponderEventoScreen
import com.baniterio.app.ui.historia.HistoriaScreen
import com.baniterio.app.ui.inventario.CategoriaInventario
import com.baniterio.app.ui.inventario.InventarioCategoriaScreen
import com.baniterio.app.ui.inventario.InventarioFiestaScreen
import com.baniterio.app.ui.inventario.InventarioScreen
import com.baniterio.app.ui.compra.ListaCompraAdminEventoScreen
import com.baniterio.app.ui.compra.ListaCompraAdminScreen
import com.baniterio.app.ui.compra.ListaCompraScreen
import com.baniterio.app.ui.auth.desbloqueo.DesbloqueoScreen
import com.baniterio.app.ui.auth.login.LoginScreen
import com.baniterio.app.ui.miembros.CargandoSesionScreen
import com.baniterio.app.ui.miembros.EditorPerfilScreen
import com.baniterio.app.ui.miembros.MiembrosScreen
import com.baniterio.app.ui.panel.PanelScreen
import com.baniterio.app.ui.auth.registro.RegistroScreen
import com.baniterio.app.ui.auth.solicitaracceso.SolicitarAccesoScreen
import com.baniterio.app.ui.preciobebida.PrecioBebidaAlcoholScreen
import com.baniterio.app.ui.preciobebida.PrecioBebidaEventosScreen

private const val CLAVE_DESBLOQUEO = "Desbloqueo"
private const val CLAVE_LOGIN = "Login"
private const val CLAVE_REGISTRO = "Registro"
private const val CLAVE_SOLICITAR = "SolicitarAcceso"
private const val CLAVE_CARGANDO_SESION = "CargandoSesion"
private const val CLAVE_PANEL = "Panel"
private const val CLAVE_HISTORIA = "Historia"
private const val CLAVE_MIEMBROS = "Miembros"
private const val CLAVE_EDITOR_PERFIL = "EditorPerfil"
private const val CLAVE_EVENTOS = "Eventos"
private const val CLAVE_RESPONDER_EVENTO = "ResponderEvento"
private const val CLAVE_EVENTO_DETALLE = "EventoDetalle"
private const val CLAVE_EDITOR_EVENTO = "EditorEvento"
private const val CLAVE_EVENTOS_OCULTOS = "EventosOcultos"
private const val CLAVE_CUENTAS = "Cuentas"
private const val CLAVE_CUENTA_DETALLE = "CuentaDetalle"
private const val CLAVE_VISOR_RECIBO = "VisorRecibo"
private const val CLAVE_INVENTARIO = "Inventario"
private const val CLAVE_INVENTARIO_CATEGORIA = "InventarioCategoria"
private const val CLAVE_INVENTARIO_FIESTA = "InventarioFiesta"
private const val CLAVE_LISTA_COMPRA = "ListaCompra"
private const val CLAVE_LISTA_COMPRA_ADMIN = "ListaCompraAdmin"
private const val CLAVE_LISTA_COMPRA_ADMIN_EVENTO = "ListaCompraAdminEvento"
private const val CLAVE_PRECIO_BEBIDA_EVENTOS = "PrecioBebidaEventos"
private const val CLAVE_PRECIO_BEBIDA_ALCOHOL = "PrecioBebidaAlcohol"
private const val CLAVE_ADMIN_INDEX = "AdminIndex"
private const val CLAVE_ADMIN_SOLICITUDES = "AdminSolicitudes"
private const val CLAVE_ADMIN_PERMISOS = "AdminPermisos"
private const val CLAVE_ADMIN_BEBIDAS = "AdminBebidas"
private const val CLAVE_ADMIN_PAGOS = "AdminPagos"

private fun Screen.aClave(): String = when (this) {
    Screen.Desbloqueo -> CLAVE_DESBLOQUEO
    Screen.Login -> CLAVE_LOGIN
    Screen.Registro -> CLAVE_REGISTRO
    Screen.SolicitarAcceso -> CLAVE_SOLICITAR
    Screen.CargandoSesion -> CLAVE_CARGANDO_SESION
    Screen.Panel -> CLAVE_PANEL
    Screen.Historia -> CLAVE_HISTORIA
    Screen.Miembros -> CLAVE_MIEMBROS
    Screen.EditorPerfil -> CLAVE_EDITOR_PERFIL
    Screen.Eventos -> CLAVE_EVENTOS
    Screen.ResponderEvento -> CLAVE_RESPONDER_EVENTO
    Screen.EventoDetalle -> CLAVE_EVENTO_DETALLE
    Screen.EditorEvento -> CLAVE_EDITOR_EVENTO
    Screen.EventosOcultos -> CLAVE_EVENTOS_OCULTOS
    Screen.Cuentas -> CLAVE_CUENTAS
    Screen.CuentaDetalle -> CLAVE_CUENTA_DETALLE
    Screen.VisorRecibo -> CLAVE_VISOR_RECIBO
    Screen.Inventario -> CLAVE_INVENTARIO
    Screen.InventarioCategoria -> CLAVE_INVENTARIO_CATEGORIA
    Screen.InventarioFiesta -> CLAVE_INVENTARIO_FIESTA
    Screen.ListaCompra -> CLAVE_LISTA_COMPRA
    Screen.ListaCompraAdmin -> CLAVE_LISTA_COMPRA_ADMIN
    Screen.ListaCompraAdminEvento -> CLAVE_LISTA_COMPRA_ADMIN_EVENTO
    Screen.PrecioBebidaEventos -> CLAVE_PRECIO_BEBIDA_EVENTOS
    Screen.PrecioBebidaAlcohol -> CLAVE_PRECIO_BEBIDA_ALCOHOL
    Screen.AdminIndex -> CLAVE_ADMIN_INDEX
    Screen.AdminSolicitudes -> CLAVE_ADMIN_SOLICITUDES
    Screen.AdminPermisos -> CLAVE_ADMIN_PERMISOS
    Screen.AdminBebidas -> CLAVE_ADMIN_BEBIDAS
    Screen.AdminPagos -> CLAVE_ADMIN_PAGOS
}

private fun claveAScreen(clave: String): Screen = when (clave) {
    CLAVE_DESBLOQUEO -> Screen.Desbloqueo
    CLAVE_REGISTRO -> Screen.Registro
    CLAVE_SOLICITAR -> Screen.SolicitarAcceso
    CLAVE_CARGANDO_SESION -> Screen.CargandoSesion
    CLAVE_PANEL -> Screen.Panel
    CLAVE_HISTORIA -> Screen.Historia
    CLAVE_MIEMBROS -> Screen.Miembros
    CLAVE_EDITOR_PERFIL -> Screen.EditorPerfil
    CLAVE_EVENTOS -> Screen.Eventos
    CLAVE_RESPONDER_EVENTO -> Screen.ResponderEvento
    CLAVE_EVENTO_DETALLE -> Screen.EventoDetalle
    CLAVE_EDITOR_EVENTO -> Screen.EditorEvento
    CLAVE_EVENTOS_OCULTOS -> Screen.EventosOcultos
    CLAVE_CUENTAS -> Screen.Cuentas
    CLAVE_CUENTA_DETALLE -> Screen.CuentaDetalle
    CLAVE_VISOR_RECIBO -> Screen.VisorRecibo
    CLAVE_INVENTARIO -> Screen.Inventario
    CLAVE_INVENTARIO_CATEGORIA -> Screen.InventarioCategoria
    CLAVE_INVENTARIO_FIESTA -> Screen.InventarioFiesta
    CLAVE_LISTA_COMPRA -> Screen.ListaCompra
    CLAVE_LISTA_COMPRA_ADMIN -> Screen.ListaCompraAdmin
    CLAVE_LISTA_COMPRA_ADMIN_EVENTO -> Screen.ListaCompraAdminEvento
    CLAVE_PRECIO_BEBIDA_EVENTOS -> Screen.PrecioBebidaEventos
    CLAVE_PRECIO_BEBIDA_ALCOHOL -> Screen.PrecioBebidaAlcohol
    CLAVE_ADMIN_INDEX -> Screen.AdminIndex
    CLAVE_ADMIN_SOLICITUDES -> Screen.AdminSolicitudes
    CLAVE_ADMIN_PERMISOS -> Screen.AdminPermisos
    CLAVE_ADMIN_BEBIDAS -> Screen.AdminBebidas
    CLAVE_ADMIN_PAGOS -> Screen.AdminPagos
    else -> Screen.Login
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App(
    deps: Dependencias,
    alIniciarSesion: () -> Unit = {},
    alCerrarSesion: () -> Unit = {},
) {
    // Se guarda solo la clave (String), no el Screen en sí, porque un sealed class llano
    // no es directamente Saveable en todas las plataformas. Así sobrevive a un cambio de
    // configuración (p. ej. rotar el dispositivo) sin volver a Login.
    var screenKey by rememberSaveable {
        mutableStateOf(if (deps.almacen.hayCredenciales) CLAVE_DESBLOQUEO else CLAVE_LOGIN)
    }
    val screen: Screen = claveAScreen(screenKey)

    // Guardia de arranque en frío: tras morir el proceso, `screenKey` puede restaurar
    // "Panel"/"Historia" pero la sesión (token + usuarioActual) vive solo en memoria y
    // se ha perdido. Se redirige a Desbloqueo/Login. En una recomposición normal dentro
    // de la sesión `usuarioActual` no es null, así que esto no hace nada.
    LaunchedEffect(Unit) {
        if (deps.repo.usuarioActual == null &&
            (screen == Screen.CargandoSesion || screen == Screen.Panel || screen == Screen.Historia ||
                screen == Screen.Miembros || screen == Screen.EditorPerfil ||
                screen == Screen.Eventos || screen == Screen.ResponderEvento ||
                screen == Screen.EventoDetalle || screen == Screen.EditorEvento ||
                screen == Screen.EventosOcultos ||
                screen == Screen.Cuentas || screen == Screen.CuentaDetalle || screen == Screen.VisorRecibo ||
                screen == Screen.Inventario || screen == Screen.InventarioCategoria ||
                screen == Screen.InventarioFiesta ||
                screen == Screen.ListaCompra || screen == Screen.ListaCompraAdmin ||
                screen == Screen.ListaCompraAdminEvento ||
                screen == Screen.PrecioBebidaEventos || screen == Screen.PrecioBebidaAlcohol ||
                screen == Screen.AdminIndex || screen == Screen.AdminSolicitudes ||
                screen == Screen.AdminPermisos || screen == Screen.AdminBebidas ||
                screen == Screen.AdminPagos)
        ) {
            screenKey = if (deps.almacen.hayCredenciales) CLAVE_DESBLOQUEO else CLAVE_LOGIN
        }
    }

    // Datos que Registro precarga en SolicitarAcceso cuando el teléfono no está autorizado.
    // rememberSaveable con Saver propio para que sobreviva a una rotación.
    var datosSolicitud by rememberSaveable(
        stateSaver = Saver<SolicitudPrecarga?, List<String>>(
            save = { valor ->
                valor?.let { p -> listOf(p.nombre, p.apellidos, p.telefono, p.email, p.password) }
            },
            restore = { l -> SolicitudPrecarga(l[0], l[1], l[2], l[3], l[4]) },
        ),
    ) { mutableStateOf<SolicitudPrecarga?>(null) }

    // El editor de perfil sirve para dos casos: el alta obligatoria del primer
    // login (sin "atrás", trae aquí `CargandoSesion`) y "Editar" desde la propia
    // tarjeta. Este flag distingue a dónde volver y si se pinta el botón de volver.
    var editorObligatorio by rememberSaveable { mutableStateOf(false) }

    // Sección Eventos: el id del evento que se está viendo y el que se está
    // editando (null = crear uno nuevo). Fuera del Screen, como editorObligatorio.
    var eventoSeleccionado by rememberSaveable { mutableStateOf<Long?>(null) }
    var editorEventoId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Sección Cuentas: el id de la cuenta que se está viendo.
    var cuentaSeleccionada by rememberSaveable { mutableStateOf<Long?>(null) }

    // Visor de recibos: el nombre del archivo (`recibo_archivo`) que se está viendo.
    var reciboSeleccionado by rememberSaveable { mutableStateOf<String?>(null) }

    // Sección Inventario: la categoría cuyo listado se está viendo (se guarda la clave).
    var categoriaInventarioClave by rememberSaveable { mutableStateOf<String?>(null) }

    // Inventario de la fiesta: el evento cuyo inventario enviado se está viendo.
    var inventarioFiestaEventoId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Lista de la compra: el evento cuya lista (lectura o editor de admin) se está viendo.
    var listaCompraEventoId by rememberSaveable { mutableStateOf<Long?>(null) }

    // Precio compras: el evento cuya rejilla de precios se está viendo.
    var precioBebidaEventoId by rememberSaveable { mutableStateOf<Long?>(null) }

    fun ir(destino: Screen) {
        screenKey = destino.aClave()
    }

    // Coil 3 no trae fetcher de red por defecto: se registra uno de Ktor para
    // poder cargar avatares y fotos de perfil por URL.
    setSingletonImageLoaderFactory { ctx ->
        ImageLoader.Builder(ctx)
            .components { add(KtorNetworkFetcherFactory()) }
            .build()
    }

    BaniterioTheme {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            // Animación de cambio de pantalla: la vieja se desvanece subiendo, la
            // nueva entra desde abajo con fundido. Mismo efecto que en la web
            // (styles.css, View Transitions API).
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    (fadeIn(tween(220)) + slideInVertically(tween(220)) { alto -> alto / 12 })
                        .togetherWith(fadeOut(tween(150)) + slideOutVertically(tween(150)) { alto -> -alto / 12 })
                },
                label = "pantalla",
            ) { pantalla ->
            when (pantalla) {
                is Screen.Desbloqueo -> DesbloqueoScreen(
                    repo = deps.repo,
                    almacen = deps.almacen,
                    onDesbloqueado = { alIniciarSesion(); ir(Screen.CargandoSesion) },
                    onUsarOtraCuenta = { ir(Screen.Login) },
                )
                is Screen.Login -> LoginScreen(
                    repo = deps.repo,
                    almacen = deps.almacen,
                    onLoginSuccess = { alIniciarSesion(); ir(Screen.CargandoSesion) },
                    onIrARegistro = { ir(Screen.Registro) },
                )
                is Screen.CargandoSesion -> CargandoSesionScreen(
                    perfilRepo = deps.perfilRepo,
                    // Tras el perfil, la pantalla de convocatorias: si no hay
                    // ninguna pendiente, ella misma llama onTerminado() → Panel.
                    onPerfilCompleto = { ir(Screen.ResponderEvento) },
                    onPerfilIncompleto = { editorObligatorio = true; ir(Screen.EditorPerfil) },
                )
                is Screen.Registro -> {
                    BackHandler { ir(Screen.Login) }
                    RegistroScreen(
                        repo = deps.repo,
                        onRegistroCompletado = { ir(Screen.Login) },
                        onVolverALogin = { ir(Screen.Login) },
                        onSolicitarAcceso = { datos ->
                            datosSolicitud = datos
                            ir(Screen.SolicitarAcceso)
                        },
                    )
                }
                is Screen.SolicitarAcceso -> {
                    BackHandler { ir(Screen.Registro) }
                    SolicitarAccesoScreen(
                        repo = deps.repo,
                        precarga = datosSolicitud,
                        // Se limpia `datosSolicitud` (lleva la contraseña) del estado
                        // guardado en cuanto se sale de esta pantalla.
                        onEnviada = { datosSolicitud = null; ir(Screen.Login) },
                        onVolver = { datosSolicitud = null; ir(Screen.Registro) },
                    )
                }
                is Screen.Panel -> PanelScreen(
                    onAbrirSeccion = { destino -> ir(destino) },
                    onCerrarSesion = {
                        alCerrarSesion()
                        deps.repo.logout()
                        deps.almacen.borrar()
                        ir(Screen.Login)
                    },
                    tieneAdmin = deps.repo.usuarioActual?.areas?.isNotEmpty() == true,
                    adminRepo = deps.adminRepo,
                )
                is Screen.Historia -> {
                    BackHandler { ir(Screen.Panel) }
                    HistoriaScreen(
                        onVolver = { ir(Screen.Panel) },
                    )
                }
                is Screen.Miembros -> {
                    BackHandler { ir(Screen.Panel) }
                    MiembrosScreen(
                        perfilRepo = deps.perfilRepo,
                        miId = deps.repo.usuarioActual?.id,
                        onEditar = { editorObligatorio = false; ir(Screen.EditorPerfil) },
                        onVolver = { ir(Screen.Panel) },
                    )
                }
                is Screen.EditorPerfil -> {
                    // En modo obligatorio no hay "atrás": hay que completar el perfil.
                    if (!editorObligatorio) BackHandler { ir(Screen.Miembros) }
                    EditorPerfilScreen(
                        perfilRepo = deps.perfilRepo,
                        obligatorio = editorObligatorio,
                        onGuardado = {
                            val iba = editorObligatorio
                            editorObligatorio = false
                            ir(if (iba) Screen.Panel else Screen.Miembros)
                        },
                        onVolver = { editorObligatorio = false; ir(Screen.Miembros) },
                    )
                }
                is Screen.Eventos -> {
                    BackHandler { ir(Screen.Panel) }
                    EventosScreen(
                        eventosRepo = deps.eventosRepo,
                        esAdmin = deps.repo.usuarioActual?.let { it.rol == "ADMIN" || it.esSuperadmin } == true,
                        onAbrirEvento = { id -> eventoSeleccionado = id; ir(Screen.EventoDetalle) },
                        onCrear = { editorEventoId = null; ir(Screen.EditorEvento) },
                        onVerOcultos = { ir(Screen.EventosOcultos) },
                        onVolver = { ir(Screen.Panel) },
                    )
                }
                is Screen.EventosOcultos -> {
                    BackHandler { ir(Screen.Eventos) }
                    EventosOcultosScreen(
                        eventosRepo = deps.eventosRepo,
                        onAbrirEvento = { id -> eventoSeleccionado = id; ir(Screen.EventoDetalle) },
                        onVolver = { ir(Screen.Eventos) },
                    )
                }
                is Screen.ResponderEvento -> {
                    // Sin BackHandler: es bloqueante. Cuando no queda ninguna
                    // convocatoria por contestar, va al panel.
                    ResponderEventoScreen(
                        asistenciaRepo = deps.asistenciaRepo,
                        bebidaRepo = deps.bebidaRepo,
                        miUsuarioId = deps.repo.usuarioActual?.id,
                        onTerminado = { ir(Screen.Panel) },
                    )
                }
                is Screen.EventoDetalle -> {
                    BackHandler { ir(Screen.Eventos) }
                    val id = eventoSeleccionado
                    if (id == null) {
                        LaunchedEffect(Unit) { ir(Screen.Eventos) }
                    } else {
                        EventoDetalleScreen(
                            eventosRepo = deps.eventosRepo,
                            asistenciaRepo = deps.asistenciaRepo,
                            bebidaRepo = deps.bebidaRepo,
                            eventoId = id,
                            onEditar = { editorEventoId = id; ir(Screen.EditorEvento) },
                            onBorrado = { ir(Screen.Eventos) },
                            onInventarioFiesta = {
                                inventarioFiestaEventoId = id
                                ir(Screen.InventarioFiesta)
                            },
                            onListaCompra = {
                                listaCompraEventoId = id
                                ir(Screen.ListaCompra)
                            },
                            onVolver = { ir(Screen.Eventos) },
                        )
                    }
                }
                is Screen.EditorEvento -> {
                    val volverA = if (editorEventoId != null) Screen.EventoDetalle else Screen.Eventos
                    BackHandler { ir(volverA) }
                    EditorEventoScreen(
                        eventosRepo = deps.eventosRepo,
                        cuentasRepo = deps.cuentasRepo,
                        eventoId = editorEventoId,
                        esAdmin = deps.repo.usuarioActual?.let { it.rol == "ADMIN" || it.esSuperadmin } == true,
                        onGuardado = { nuevoId -> eventoSeleccionado = nuevoId; ir(Screen.EventoDetalle) },
                        onVolver = { ir(volverA) },
                    )
                }
                is Screen.Cuentas -> {
                    BackHandler { ir(Screen.Panel) }
                    CuentasScreen(
                        cuentasRepo = deps.cuentasRepo,
                        onAbrirCuenta = { id -> cuentaSeleccionada = id; ir(Screen.CuentaDetalle) },
                        onVolver = { ir(Screen.Panel) },
                    )
                }
                is Screen.CuentaDetalle -> {
                    BackHandler { ir(Screen.Cuentas) }
                    val id = cuentaSeleccionada
                    if (id == null) {
                        LaunchedEffect(Unit) { ir(Screen.Cuentas) }
                    } else {
                        CuentaDetalleScreen(
                            cuentasRepo = deps.cuentasRepo,
                            cuentaId = id,
                            onVolver = { ir(Screen.Cuentas) },
                            onVerRecibo = { archivo -> reciboSeleccionado = archivo; ir(Screen.VisorRecibo) },
                        )
                    }
                }
                is Screen.VisorRecibo -> {
                    BackHandler { ir(Screen.CuentaDetalle) }
                    val archivo = reciboSeleccionado
                    if (archivo == null) {
                        LaunchedEffect(Unit) { ir(Screen.CuentaDetalle) }
                    } else {
                        VisorReciboScreen(
                            mediaRepo = deps.mediaRepo,
                            archivo = archivo,
                            onVolver = { ir(Screen.CuentaDetalle) },
                        )
                    }
                }
                is Screen.Inventario -> {
                    BackHandler { ir(Screen.Panel) }
                    InventarioScreen(
                        onAbrirCategoria = { cat ->
                            categoriaInventarioClave = cat.clave
                            ir(Screen.InventarioCategoria)
                        },
                        onVolver = { ir(Screen.Panel) },
                    )
                }
                is Screen.InventarioCategoria -> {
                    BackHandler { ir(Screen.Inventario) }
                    val cat = categoriaInventarioClave?.let { clave ->
                        CategoriaInventario.entries.firstOrNull { it.clave == clave }
                    }
                    if (cat == null) {
                        LaunchedEffect(Unit) { ir(Screen.Inventario) }
                    } else {
                        InventarioCategoriaScreen(
                            inventarioRepo = deps.inventarioRepo,
                            categoria = cat,
                            onVolver = { ir(Screen.Inventario) },
                        )
                    }
                }
                is Screen.InventarioFiesta -> {
                    BackHandler { ir(Screen.EventoDetalle) }
                    val id = inventarioFiestaEventoId
                    if (id == null) {
                        LaunchedEffect(Unit) { ir(Screen.Eventos) }
                    } else {
                        InventarioFiestaScreen(
                            inventarioRepo = deps.inventarioRepo,
                            eventoId = id,
                            onVolver = { ir(Screen.EventoDetalle) },
                        )
                    }
                }
                is Screen.ListaCompra -> {
                    BackHandler { ir(Screen.EventoDetalle) }
                    val id = listaCompraEventoId
                    if (id == null) {
                        LaunchedEffect(Unit) { ir(Screen.Eventos) }
                    } else {
                        ListaCompraScreen(
                            listaCompraRepo = deps.listaCompraRepo,
                            eventoId = id,
                            onVolver = { ir(Screen.EventoDetalle) },
                        )
                    }
                }
                is Screen.ListaCompraAdmin -> {
                    BackHandler { ir(Screen.AdminIndex) }
                    ListaCompraAdminScreen(
                        listaCompraRepo = deps.listaCompraRepo,
                        onAbrirEvento = { evId ->
                            listaCompraEventoId = evId
                            ir(Screen.ListaCompraAdminEvento)
                        },
                        onVolver = { ir(Screen.AdminIndex) },
                    )
                }
                is Screen.ListaCompraAdminEvento -> {
                    BackHandler { ir(Screen.ListaCompraAdmin) }
                    val id = listaCompraEventoId
                    if (id == null) {
                        LaunchedEffect(Unit) { ir(Screen.ListaCompraAdmin) }
                    } else {
                        ListaCompraAdminEventoScreen(
                            listaCompraRepo = deps.listaCompraRepo,
                            eventoId = id,
                            onVolver = { ir(Screen.ListaCompraAdmin) },
                        )
                    }
                }
                is Screen.PrecioBebidaEventos -> {
                    BackHandler { ir(Screen.Panel) }
                    PrecioBebidaEventosScreen(
                        precioBebidaRepo = deps.precioBebidaRepo,
                        onAbrirEvento = { id -> precioBebidaEventoId = id; ir(Screen.PrecioBebidaAlcohol) },
                        onVolver = { ir(Screen.Panel) },
                    )
                }
                is Screen.PrecioBebidaAlcohol -> {
                    BackHandler { ir(Screen.PrecioBebidaEventos) }
                    val id = precioBebidaEventoId
                    if (id == null) {
                        LaunchedEffect(Unit) { ir(Screen.PrecioBebidaEventos) }
                    } else {
                        PrecioBebidaAlcoholScreen(
                            precioBebidaRepo = deps.precioBebidaRepo,
                            eventoId = id,
                            onVolver = { ir(Screen.PrecioBebidaEventos) },
                        )
                    }
                }
                is Screen.AdminIndex -> {
                    BackHandler { ir(Screen.Panel) }
                    AdminIndexScreen(
                        areas = deps.repo.usuarioActual?.areas ?: emptyList(),
                        esAdmin = deps.repo.usuarioActual?.let { it.rol == "ADMIN" || it.esSuperadmin } == true,
                        adminRepo = deps.adminRepo,
                        onAbrir = { ir(it) },
                        onVolver = { ir(Screen.Panel) },
                    )
                }
                is Screen.AdminSolicitudes -> {
                    BackHandler { ir(Screen.AdminIndex) }
                    AdminSolicitudesScreen(
                        adminRepo = deps.adminRepo,
                        // Admin de verdad = rol ADMIN o superadmin; un área concedida no cuenta.
                        esAdmin = deps.repo.usuarioActual?.let { it.rol == "ADMIN" || it.esSuperadmin } == true,
                        onVolver = { ir(Screen.AdminIndex) },
                    )
                }
                is Screen.AdminPermisos -> {
                    BackHandler { ir(Screen.AdminIndex) }
                    AdminPermisosScreen(
                        adminRepo = deps.adminRepo,
                        miId = deps.repo.usuarioActual?.id,
                        onVolver = { ir(Screen.AdminIndex) },
                    )
                }
                is Screen.AdminBebidas -> {
                    BackHandler { ir(Screen.AdminIndex) }
                    AdminBebidasScreen(
                        bebidaRepo = deps.bebidaRepo,
                        onVolver = { ir(Screen.AdminIndex) },
                    )
                }
                is Screen.AdminPagos -> {
                    BackHandler { ir(Screen.AdminIndex) }
                    AdminPagosScreen(
                        eventosRepo = deps.eventosRepo,
                        onVolver = { ir(Screen.AdminIndex) },
                    )
                }
            }
            }
        }
    }
}
