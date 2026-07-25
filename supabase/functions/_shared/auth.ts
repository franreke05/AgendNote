import { timingSafeEqual } from "https://deno.land/std@0.224.0/crypto/timing_safe_equal.ts";
import { corsHeaders } from "./cors.ts";

export function requireAppSecret(req: Request) {
  const expected = Deno.env.get("APP_SECRET");
  const provided = req.headers.get("x-app-secret");
  if (!expected || !provided) {
    return new Response("Unauthorized", { status: 401, headers: corsHeaders });
  }

  // Comparacion en tiempo constante: una comparacion de string comun (!==) filtra,
  // via el tiempo de respuesta, en que posicion empieza a diferir el secreto provisto.
  const expectedBytes = new TextEncoder().encode(expected);
  const providedBytes = new TextEncoder().encode(provided);
  const isValid = expectedBytes.length === providedBytes.length
    && timingSafeEqual(expectedBytes, providedBytes);

  if (!isValid) {
    return new Response("Unauthorized", { status: 401, headers: corsHeaders });
  }
  return null;
}
