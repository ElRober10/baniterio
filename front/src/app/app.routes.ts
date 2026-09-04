import { Routes } from '@angular/router';
import { areaGuard } from './admin/area.guard';
import { AdminBebidas } from './admin/bebidas/bebidas';
import { AdminIndice } from './admin/indice/indice';
import { AdminPermisos } from './admin/permisos/permisos';
import { AdminSolicitudes } from './admin/solicitudes/solicitudes';
import { authGuard, invitadoGuard } from './auth/auth.guard';
import { Home } from './home/home';
import { Login } from './auth/login/login';
import { Panel } from './panel/panel';
import { PanelInicio } from './panel/inicio/inicio';
import { EditorPerfil } from './panel/miembros/editor-perfil/editor-perfil';
import { CuentaDetalleComponent } from './panel/cuentas/cuenta-detalle/cuenta-detalle';
import { Cuentas } from './panel/cuentas/cuentas';
import { EditorEvento } from './panel/eventos/editor-evento/editor-evento';
import { EventoDetalleComponent } from './panel/eventos/evento-detalle/evento-detalle';
import { Eventos } from './panel/eventos/eventos';
import { Miembros } from './panel/miembros/miembros';
import { perfilCompletoGuard } from './panel/miembros/perfil-completo.guard';
import { Responder } from './panel/responder/responder';
import { respuestaPendienteGuard } from './panel/responder/respuesta-pendiente.guard';
import { Registro } from './auth/registro/registro';
import { SolicitarAcceso } from './auth/solicitar-acceso/solicitar-acceso';

export const routes: Routes = [
  // Rutas públicas: si ya hay sesión, el invitadoGuard te lleva a /panel.
  { path: '', component: Home, canActivate: [invitadoGuard] },
  { path: 'login', component: Login, canActivate: [invitadoGuard] },
  { path: 'registro', component: Registro, canActivate: [invitadoGuard] },
  { path: 'solicitar-acceso', component: SolicitarAcceso, canActivate: [invitadoGuard] },
  // Ruta privada: el authGuard te manda a /login si no hay sesión activa.
  {
    path: 'panel',
    component: Panel,
    canActivate: [authGuard],
    children: [
      { path: '', component: PanelInicio, canActivate: [perfilCompletoGuard, respuestaPendienteGuard] },
      // El editor de perfil es la ÚNICA hija sin `perfilCompletoGuard`: es donde
      // ese guard atrapa a quien aún no ha completado el perfil. Tampoco lleva
      // `respuestaPendienteGuard` (el perfil va primero).
      { path: 'miembros/editar', component: EditorPerfil },
      // Pantalla bloqueante de convocatorias: solo `perfilCompletoGuard`; el
      // `respuestaPendienteGuard` la excluye para no rebotar sobre sí misma.
      { path: 'responder', component: Responder, canActivate: [perfilCompletoGuard] },
      { path: 'miembros', component: Miembros, canActivate: [perfilCompletoGuard, respuestaPendienteGuard] },
      { path: 'eventos', component: Eventos, canActivate: [perfilCompletoGuard, respuestaPendienteGuard] },
      // Específicas antes de la comodín `:id`, o `nuevo`/`editar` caerían en el detalle.
      { path: 'eventos/nuevo', component: EditorEvento, canActivate: [perfilCompletoGuard, respuestaPendienteGuard] },
      { path: 'eventos/:id/editar', component: EditorEvento, canActivate: [perfilCompletoGuard, respuestaPendienteGuard] },
      { path: 'eventos/:id', component: EventoDetalleComponent, canActivate: [perfilCompletoGuard, respuestaPendienteGuard] },
      { path: 'cuentas', component: Cuentas, canActivate: [perfilCompletoGuard, respuestaPendienteGuard] },
      { path: 'cuentas/:id', component: CuentaDetalleComponent, canActivate: [perfilCompletoGuard, respuestaPendienteGuard] },
      // Índice de administración: sin areaGuard (cualquier miembro lo abre); solo
      // pinta las secciones para las que tiene área.
      { path: 'administracion', component: AdminIndice, canActivate: [perfilCompletoGuard, respuestaPendienteGuard] },
      // administracion/* → protegidas además por areaGuard (permiso concreto del panel).
      {
        path: 'administracion/solicitudes',
        component: AdminSolicitudes,
        canActivate: [perfilCompletoGuard, respuestaPendienteGuard, areaGuard('ADMIN_SOLICITUDES')],
      },
      {
        path: 'administracion/permisos',
        component: AdminPermisos,
        canActivate: [perfilCompletoGuard, respuestaPendienteGuard, areaGuard('ADMIN_PERMISOS')],
      },
      // Bebidas propuestas: cualquier admin/superadmin (no un área); el propio
      // componente rebota a /panel si no lo eres.
      {
        path: 'administracion/bebidas',
        component: AdminBebidas,
        canActivate: [perfilCompletoGuard, respuestaPendienteGuard],
      },
    ],
  },
];
