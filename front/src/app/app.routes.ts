import { Routes } from '@angular/router';
import { areaGuard } from './admin/area.guard';
import { AdminBebidas } from './admin/bebidas/bebidas';
import { AdminPagos } from './admin/pagos/pagos';
import { AdminIndice } from './admin/indice/indice';
import { AdminPermisos } from './admin/permisos/permisos';
import { AdminSolicitudes } from './admin/solicitudes/solicitudes';
import { ListaCompraAdmin } from './admin/lista-compra/lista-compra-admin';
import { ListaCompraAdminEvento } from './admin/lista-compra/lista-compra-admin-evento';
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
import { EventosOcultos } from './panel/eventos/eventos-ocultos/eventos-ocultos';
import { Inventario } from './panel/inventario/inventario';
import { InventarioCategoria } from './panel/inventario/inventario-categoria';
import { InventarioFiesta } from './panel/eventos/inventario-fiesta/inventario-fiesta';
import { ListaCompra } from './panel/eventos/lista-compra/lista-compra';
import { Miembros } from './panel/miembros/miembros';
import { PrecioBebidasAnios } from './panel/precio-bebidas/anios/precio-bebidas-anios';
import { PrecioBebidasEventos } from './panel/precio-bebidas/eventos/precio-bebidas-eventos';
import { PrecioBebidaEvento } from './panel/precio-bebidas/evento/precio-bebida-evento';
import { PrecioBebidaAlcohol } from './panel/precio-bebidas/alcohol/precio-bebida-alcohol';
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
      { path: 'eventos/ocultos', component: EventosOcultos, canActivate: [perfilCompletoGuard] },
      { path: 'eventos/:id/editar', component: EditorEvento, canActivate: [perfilCompletoGuard] },
      { path: 'eventos/:id/inventario', component: InventarioFiesta, canActivate: [perfilCompletoGuard] },
      { path: 'eventos/:id/lista-compra', component: ListaCompra, canActivate: [perfilCompletoGuard] },
      { path: 'eventos/:id', component: EventoDetalleComponent, canActivate: [perfilCompletoGuard] },
      { path: 'cuentas', component: Cuentas, canActivate: [perfilCompletoGuard] },
      { path: 'cuentas/:id', component: CuentaDetalleComponent, canActivate: [perfilCompletoGuard] },
      // Inventario: cualquier peñista lo ve; el permiso de área INVENTARIO (que
      // comprueba el backend) solo controla el botón "Editar". Sin areaGuard.
      { path: 'inventario', component: Inventario, canActivate: [perfilCompletoGuard] },
      {
        path: 'inventario/:categoria',
        component: InventarioCategoria,
        canActivate: [perfilCompletoGuard],
      },
      // Precio bebidas: lo ve cualquier miembro; sin areaGuard.
      { path: 'precio-bebidas', component: PrecioBebidasAnios, canActivate: [perfilCompletoGuard] },
      {
        path: 'precio-bebidas/:anio',
        component: PrecioBebidasEventos,
        canActivate: [perfilCompletoGuard],
      },
      {
        path: 'precio-bebidas/:anio/:id',
        component: PrecioBebidaEvento,
        canActivate: [perfilCompletoGuard],
      },
      {
        path: 'precio-bebidas/:anio/:id/alcohol',
        component: PrecioBebidaAlcohol,
        canActivate: [perfilCompletoGuard],
      },
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
      {
        path: 'administracion/lista-compra',
        component: ListaCompraAdmin,
        canActivate: [perfilCompletoGuard, areaGuard('INVENTARIO')],
      },
      {
        path: 'administracion/lista-compra/:id',
        component: ListaCompraAdminEvento,
        canActivate: [perfilCompletoGuard, areaGuard('INVENTARIO')],
      },
      // Bebidas propuestas: cualquier admin/superadmin (no un área); el propio
      // componente rebota a /panel si no lo eres.
      {
        path: 'administracion/bebidas',
        component: AdminBebidas,
        canActivate: [perfilCompletoGuard],
      },
      // Confirmar pagos: cualquier admin/superadmin (no un área); el componente
      // rebota a /panel si no lo eres.
      {
        path: 'administracion/pagos',
        component: AdminPagos,
        canActivate: [perfilCompletoGuard],
      },
    ],
  },
];
