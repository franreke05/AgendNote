import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders } from "../_shared/cors.ts";
import { errorResponse, jsonResponse } from "../_shared/response.ts";
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
    if (req.method === "GET") {
      const url = new URL(req.url);
      const key = url.searchParams.get("key")?.trim();

      if (key) {
        const { data, error } = await supabase
          .from("settings")
          .select("key,value")
          .eq("key", key)
          .maybeSingle();

        if (error) return errorResponse(error.message, 500);
        return jsonResponse({ setting: data ?? null });
      }

      const { data, error } = await supabase
        .from("settings")
        .select("key,value")
        .order("key", { ascending: true });

      if (error) return errorResponse(error.message, 500);
      return jsonResponse({ settings: data ?? [] });
    }

    if (req.method === "POST") {
      const body = await req.json();
      const key = String(body?.key ?? "").trim();
      const value = String(body?.value ?? "");

      if (!key) return errorResponse("key is required", 400);

      const { data, error } = await supabase
        .from("settings")
        .upsert({ key, value })
        .select("key,value")
        .single();

      if (error) return errorResponse(error.message, 500);
      return jsonResponse({ setting: data });
    }

    return errorResponse("method not allowed", 405);
  } catch (error) {
    const message = error instanceof Error ? error.message : "unknown error";
    return errorResponse(message, 500);
  }
});
