import { Routes } from '@angular/router';
import { authGuard, invitadoGuard } from './auth/auth.guard';
import { Home } from './home/home';
import { Login } from './auth/login/login';
import { Panel } from './panel/panel';
import { Registro } from './auth/registro/registro';
import { SolicitarAcceso } from './auth/solicitar-acceso/solicitar-acceso';

export const routes: Routes = [
  // Rutas públicas: si ya hay sesión, el invitadoGuard te lleva a /panel.
  { path: '', component: Home, canActivate: [invitadoGuard] },
  { path: 'login', component: Login, canActivate: [invitadoGuard] },
  { path: 'registro', component: Registro, canActivate: [invitadoGuard] },
  { path: 'solicitar-acceso', component: SolicitarAcceso, canActivate: [invitadoGuard] },
  // Ruta privada: el authGuard te manda a /login si no hay sesión activa.
  { path: 'panel', component: Panel, canActivate: [authGuard] },
];
