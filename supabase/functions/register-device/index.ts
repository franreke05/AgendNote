import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders } from "../_shared/cors.ts";
import { errorResponse, internalErrorResponse, jsonResponse } from "../_shared/response.ts";
import { requireAppSecret } from "../_shared/auth.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? Deno.env.get("SB_URL");
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SB_SERVICE_ROLE_KEY");

if (!supabaseUrl || !serviceKey) {
  throw new Error("Missing SUPABASE_URL/SB_URL or SUPABASE_SERVICE_ROLE_KEY/SB_SERVICE_ROLE_KEY");
}

const supabase = createClient(supabaseUrl, serviceKey, {
  auth: { persistSession: false },
});

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const authError = requireAppSecret(req);
  if (authError) return authError;

  try {
    if (req.method !== "POST") {
      return errorResponse("method not allowed", 405);
    }

    const body = await req.json();
    const platform = String(body?.platform ?? "").trim();
    const token = String(body?.token ?? "").trim();

    if (!platform) return errorResponse("platform is required", 400);
    if (!token) return errorResponse("token is required", 400);

    const { data, error } = await supabase
      .from("devices")
      .upsert({ platform, token, last_seen_at: new Date().toISOString() }, { onConflict: "token" })
      .select("id,platform,token,last_seen_at")
      .single();

    if (error) return internalErrorResponse(error);
    return jsonResponse({ device: data }, 201);
  } catch (error) {
    return internalErrorResponse(error);
  }
});
