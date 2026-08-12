import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders } from "../_shared/cors.ts";
import { errorResponse, internalErrorResponse, jsonResponse } from "../_shared/response.ts";
import { requireAppSecret } from "../_shared/auth.ts";
import { LIMITS, ValidationError, normalizeOptionalText, normalizeRequiredText, readJsonBody } from "../_shared/validation.ts";

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
    if (req.method === "GET") {
      const url = new URL(req.url);
      const key = url.searchParams.get("key")?.trim();

      if (key) {
        const { data, error } = await supabase
          .from("settings")
          .select("key,value")
          .eq("key", key)
          .maybeSingle();

        if (error) return internalErrorResponse(error);
        return jsonResponse({ setting: data ?? null });
      }

      const { data, error } = await supabase
        .from("settings")
        .select("key,value")
        .order("key", { ascending: true });

      if (error) return internalErrorResponse(error);
      return jsonResponse({ settings: data ?? [] });
    }

    if (req.method === "POST") {
      const body = await readJsonBody(req) as Record<string, unknown>;
      const key = normalizeRequiredText(body?.key, "key", LIMITS.settingsKey);
      const value = normalizeOptionalText(body?.value, "value", LIMITS.settingsValue) ?? "";

      const { data, error } = await supabase
        .from("settings")
        .upsert({ key, value })
        .select("key,value")
        .single();

      if (error) return internalErrorResponse(error);
      return jsonResponse({ setting: data });
    }

    return errorResponse("method not allowed", 405);
  } catch (error) {
    if (error instanceof ValidationError) return errorResponse(error.message, 400);
    return internalErrorResponse(error);
  }
});
