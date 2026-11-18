import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { Permission, PermissionCode } from '../auth/permissions';

/** Exige sesion iniciada; si no, lleva al login y vuelve despues a la pagina pedida. */
export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  return auth.isAuthenticated()
    ? true
    : inject(Router).createUrlTree(['/login'], { queryParams: { returnUrl: state.url } });
};

/** Solo para el backoffice: exige al menos un permiso de empleado. */
export const staffGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isStaff() ? true : inject(Router).createUrlTree(['/']);
};

/**
 * Exige el permiso indicado en la ruta: { data: { permission: 'EVENT_VIEW' } }.
 * Es solo una ayuda de navegacion: el backend vuelve a comprobarlo en cada peticion.
 */
export const permissionGuard: CanActivateFn = (route) => {
  const auth = inject(AuthService);
  const required = route.data['permission'] as PermissionCode | undefined;
  return !required || auth.hasPermission(required) ? true : inject(Router).createUrlTree(['/admin']);
};

/**
 * Zona publica (web de clientes): un trabajador con sesion no la ve; se le lleva a su panel.
 * Los visitantes sin sesion y los clientes pasan. El backend tambien lo impide: los endpoints
 * de cliente exigen el rol CUSTOMER.
 */
export const customerAreaGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() && auth.isStaff() ? inject(Router).createUrlTree(['/admin']) : true;
};

/** Paginas de login/registro: si ya hay sesion, no tiene sentido mostrarlas. */
export const guestGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  return auth.isAuthenticated() ? inject(Router).createUrlTree([auth.isStaff() ? '/admin' : '/cuenta']) : true;
};

/** Primera pantalla del backoffice segun los permisos del usuario. */
export const adminHomeRedirect: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const candidates: [PermissionCode, string][] = [
    [Permission.DASHBOARD_VIEW, '/admin/dashboard'],
    [Permission.PLATFORM_ADMIN, '/admin/empresas'],
    [Permission.PROMOTER_PANEL, '/admin/mi-panel'],
    [Permission.TICKET_VALIDATE, '/admin/acceso'],
    [Permission.POS_SELL, '/tpv'],
    [Permission.EVENT_VIEW, '/admin/eventos'],
  ];
  const target = candidates.find(([permission]) => auth.hasPermission(permission));
  return router.createUrlTree([target ? target[1] : '/']);
};
