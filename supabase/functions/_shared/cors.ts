// El cliente real es la app movil (Ktor), que no aplica CORS. Este header solo importa
// si algun dia un caller desde navegador necesita acceso: por defecto queda vacio (bloquea
// a todo origen de navegador) y se habilita seteando el secret CORS_ALLOWED_ORIGIN.
const allowedOrigin = Deno.env.get("CORS_ALLOWED_ORIGIN") ?? "";

export const corsHeaders = {
  "Access-Control-Allow-Origin": allowedOrigin,
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-app-secret",
  "Access-Control-Allow-Methods": "GET,POST,PATCH,DELETE,OPTIONS",
};
