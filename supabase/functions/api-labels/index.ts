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
    if (req.method === "GET") {
      const { data, error } = await supabase
        .from("labels")
        .select("id,name,color_hex,created_at,updated_at")
        .order("created_at", { ascending: true });

      if (error) return internalErrorResponse(error);
      return jsonResponse({ labels: data ?? [] });
    }

    if (req.method === "POST") {
      const body = await req.json();
      const name = String(body?.name ?? "").trim();
      const colorHex = String(body?.color_hex ?? "").trim();

      if (!name) return errorResponse("name is required", 400);
      if (!colorHex) return errorResponse("color_hex is required", 400);

      const { data, error } = await supabase
        .from("labels")
        .insert({ name, color_hex: colorHex })
        .select("id,name,color_hex,created_at,updated_at")
        .single();

      if (error) return internalErrorResponse(error);
      return jsonResponse({ label: data }, 201);
    }

    if (req.method === "PATCH") {
      const body = await req.json();
      const id = String(body?.id ?? "").trim();
      const updates: Record<string, unknown> = {};

      if (!id) return errorResponse("id is required", 400);
      if (body?.name != null) updates.name = String(body.name).trim();
      if (body?.color_hex != null) updates.color_hex = String(body.color_hex).trim();

      const { data, error } = await supabase
        .from("labels")
        .update(updates)
        .eq("id", id)
        .select("id,name,color_hex,created_at,updated_at")
        .single();

      if (error) return internalErrorResponse(error);
      return jsonResponse({ label: data });
    }

    if (req.method === "DELETE") {
      const url = new URL(req.url);
      const all = url.searchParams.get("all")?.trim();
      if (all === "true" || all === "1") {
        const { error } = await supabase
          .from("labels")
          .delete()
          .neq("id", "00000000-0000-0000-0000-000000000000");

        if (error) return internalErrorResponse(error);
        return jsonResponse({ success: true });
      }
      const id = url.searchParams.get("id")?.trim();
      if (!id) return errorResponse("id is required", 400);

      const { error } = await supabase
        .from("labels")
        .delete()
        .eq("id", id);

      if (error) return internalErrorResponse(error);
      return jsonResponse({ success: true });
    }

    return errorResponse("method not allowed", 405);
  } catch (error) {
    return internalErrorResponse(error);
  }
});
