import { corsHeaders } from "./cors.ts";

export function requireAppSecret(req: Request) {
  const expected = Deno.env.get("APP_SECRET");
  const provided = req.headers.get("x-app-secret");
  if (!expected || provided !== expected) {
    return new Response("Unauthorized", { status: 401, headers: corsHeaders });
  }
  return null;
}
