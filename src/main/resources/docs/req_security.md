## Autenticación con Supabase Auth

### Contexto

- La API Spring Boot se despliega en Render y el frontend Next.js en Vercel.
- El login lo realiza Supabase Auth en el frontend. Esta API no inicia sesión ni emite tokens: valida los access tokens JWT de Supabase.
- Las llamadas a la API salen del servidor de Next.js y envían el access token en el encabezado Authorization. No se requiere CORS para este flujo.
- Por ahora hay un solo administrador. No implementar roles ni tablas de usuarios.
- Supabase provee Auth y PostgreSQL. Storage queda fuera de esta etapa.

### Configuración

1. Añadir `spring-boot-starter-oauth2-resource-server` y `spring-boot-starter-security`.
2. Leer la configuración únicamente desde variables de entorno, sin guardar secretos en el repositorio:

   ```yaml
   app:
     supabase:
       url: ${SUPABASE_URL}
   spring:
     security:
       oauth2:
         resourceserver:
           jwt:
             jwk-set-uri: ${SUPABASE_JWKS_URL}
             issuer-uri: ${SUPABASE_URL}/auth/v1
   ```

3. Configurar en Render:
   - `SUPABASE_URL`: URL base del proyecto Supabase.
   - `SUPABASE_JWKS_URL`: `${SUPABASE_URL}/auth/v1/.well-known/jwks.json`.
4. Verificar firmas usando el JWKS público. No usar ni solicitar el JWT secret ni la service role key para validar tokens.
5. El decoder debe aceptar el algoritmo de firma `ES256` usado por los access tokens actuales de Supabase, además de validar el issuer, expiración y audiencia `authenticated`.

### Autorización HTTP

- Mantener la API sin estado (`STATELESS`), desactivar CSRF y no habilitar sesión, formulario de login, HTTP Basic ni logout de Spring Security.
- Permitir sin autenticación únicamente:
  - `GET /actuator/health` y `GET /actuator/health/**`.
  - `GET /api/v1/products` y sus rutas de detalle.
  - `GET /api/v1/categories` y sus rutas de detalle.
  - `GET /api/v1/testimonials` y sus rutas de detalle.
  - `GET /api/v1/faqs` y sus rutas de detalle.
- Cualquier otro método HTTP o ruta, incluido `GET /api/me`, requiere un JWT válido.
- El backend valida cada petición protegida; la protección de rutas del frontend no sustituye esta validación.
- No habilitar CORS para el flujo descrito: el navegador no llama directamente a esta API.

### Endpoint de verificación

`GET /api/me` requiere autenticación y devuelve los claims del JWT validado:

```json
{
  "id": "<claim sub>",
  "email": "<claim email>"
}
```

### Errores y Actuator

- Las peticiones protegidas sin token o con un token inválido responden `401` con JSON genérico, sin detalles criptográficos ni internos.
- Exponer solo el endpoint `health` de Actuator y configurar `management.endpoint.health.show-details=never`. Ningún otro endpoint de Actuator debe ser accesible.

### Verificación

- `GET /api/me` sin token: `401` y JSON genérico.
- `GET /api/me` con un access token vigente de Supabase: `200`, con `id` y `email` correspondientes a `sub` y `email`.
- GET públicos del catálogo: no deben requerir token.
- No incluir tokens reales en comandos compartidos, documentación, logs ni archivos versionados.
