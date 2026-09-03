import { Routes } from '@angular/router';
import { areaGuard } from './admin/area.guard';
import { AdminIndice } from './admin/indice/indice';
import { AdminPermisos } from './admin/permisos/permisos';
import { AdminSolicitudes } from './admin/solicitudes/solicitudes';
import { authGuard, invitadoGuard } from './auth/auth.guard';
import { Home } from './home/home';
import { Login } from './auth/login/login';
import { Panel } from './panel/panel';
import { PanelInicio } from './panel/inicio/inicio';
import { EditorPerfil } from './panel/miembros/editor-perfil/editor-perfil';
import { EditorEvento } from './panel/eventos/editor-evento/editor-evento';
import { EventoDetalleComponent } from './panel/eventos/evento-detalle/evento-detalle';
import { Eventos } from './panel/eventos/eventos';
import { Miembros } from './panel/miembros/miembros';
import { perfilCompletoGuard } from './panel/miembros/perfil-completo.guard';
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
      { path: '', component: PanelInicio, canActivate: [perfilCompletoGuard] },
      // El editor de perfil es la ÚNICA hija sin `perfilCompletoGuard`: es donde
      // ese guard atrapa a quien aún no ha completado el perfil.
      { path: 'miembros/editar', component: EditorPerfil },
      { path: 'miembros', component: Miembros, canActivate: [perfilCompletoGuard] },
      { path: 'eventos', component: Eventos, canActivate: [perfilCompletoGuard] },
      // Específicas antes de la comodín `:id`, o `nuevo`/`editar` caerían en el detalle.
      { path: 'eventos/nuevo', component: EditorEvento, canActivate: [perfilCompletoGuard] },
      { path: 'eventos/:id/editar', component: EditorEvento, canActivate: [perfilCompletoGuard] },
      { path: 'eventos/:id', component: EventoDetalleComponent, canActivate: [perfilCompletoGuard] },
      // Índice de administración: sin areaGuard (cualquier miembro lo abre); solo
      // pinta las secciones para las que tiene área.
      { path: 'administracion', component: AdminIndice, canActivate: [perfilCompletoGuard] },
      // administracion/* → protegidas además por areaGuard (permiso concreto del panel).
      {
        path: 'administracion/solicitudes',
        component: AdminSolicitudes,
        canActivate: [perfilCompletoGuard, areaGuard('ADMIN_SOLICITUDES')],
      },
      {
        path: 'administracion/permisos',
        component: AdminPermisos,
        canActivate: [perfilCompletoGuard, areaGuard('ADMIN_PERMISOS')],
      },
    ],
  },
];
