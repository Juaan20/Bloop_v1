/**
 * Configuracion por entorno. La API se sirve en el mismo origen bajo /api:
 * - En desarrollo, ng serve redirige /api a http://localhost:8080 (proxy.conf.json).
 * - En produccion, un proxy inverso (nginx...) sirve Angular y la API juntos.
 */
export const environment = {
  production: true,
  apiUrl: '/api',
};
