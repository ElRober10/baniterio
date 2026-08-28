// Configuración de DESARROLLO (la que usa `ng serve` / `ng build --configuration development`).
// El backend Spring Boot corre en el 8080 y el front en el 4200: por eso la URL
// es absoluta y por eso el backend necesita permitir CORS desde localhost:4200
// (ver app.cors.allowed-origins en application.yml y SecurityConfig).
export const environment = {
  apiBaseUrl: 'http://localhost:8080/api/v1',
};
