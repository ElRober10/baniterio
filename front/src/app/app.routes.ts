import { Routes } from '@angular/router';
import { Home } from './home/home';
import { Login } from './login/login';
import { Panel } from './panel/panel';
import { Registro } from './registro/registro';

export const routes: Routes = [
  { path: '', component: Home },
  { path: 'login', component: Login },
  { path: 'registro', component: Registro },
  { path: 'panel', component: Panel },
];
