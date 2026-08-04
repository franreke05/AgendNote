import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders } from "../_shared/cors.ts";
import { errorResponse, internalErrorResponse, jsonResponse } from "../_shared/response.ts";
import { requireAppSecret } from "../_shared/auth.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? Deno.env.get("SB_URL");
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SB_SERVICE_ROLE_KEY");
const SERIES_SELECT = "id,title,body,time,recurrence_type,days_of_week,day_of_month,label_ids,start_date,is_active,materialized_until,created_at";

if (!supabaseUrl || !serviceKey) {
  throw new Error("Missing SUPABASE_URL/SB_URL or SUPABASE_SERVICE_ROLE_KEY/SB_SERVICE_ROLE_KEY");
}

const supabase = createClient(supabaseUrl, serviceKey, {
  auth: { persistSession: false },
});

function normalizeOptionalString(value: unknown) {
  if (value == null) return null;
  const trimmed = String(value).trim();
  return trimmed.length > 0 ? trimmed : null;
}

function normalizeRequiredString(value: unknown, field: string) {
  const normalized = normalizeOptionalString(value);
  if (!normalized) throw new Error(`${field} is required`);
  return normalized;
}

function normalizeDate(value: unknown, field: string) {
  const trimmed = String(value ?? "").trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) throw new Error(`${field} must be YYYY-MM-DD`);
  return trimmed;
}

function normalizeStringArray(value: unknown) {
  if (!Array.isArray(value)) return [];
  return value.map((item) => String(item).trim()).filter((item) => item.length > 0);
}

/**
 * El cliente calcula ocurrencias desde `materialized_until + 1 dia`. Si inicializaramos
 * `materialized_until` en el propio `start_date`, la primera materializacion saltearia
 * el start_date aunque coincida con la regla. Por eso arranca un dia antes.
 */
function dayBefore(dateStr: string): string {
  const date = new Date(`${dateStr}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() - 1);
  return date.toISOString().slice(0, 10);
}

function buildInsertPayload(body: Record<string, unknown>) {
  const recurrenceType = normalizeRequiredString(body.recurrence_type, "recurrence_type");
  if (!["daily", "weekly_days", "monthly"].includes(recurrenceType)) {
    throw new Error("recurrence_type must be daily, weekly_days, or monthly");
  }
  const startDate = normalizeDate(body.start_date, "start_date");
  return {
    title: normalizeRequiredString(body.title, "title"),
    body: normalizeOptionalString(body.body),
    time: normalizeOptionalString(body.time),
    recurrence_type: recurrenceType,
    days_of_week: Array.isArray(body.days_of_week) ? body.days_of_week.map(Number) : null,
    day_of_month: body.day_of_month != null ? Number(body.day_of_month) : null,
    label_ids: normalizeStringArray(body.label_ids),
    start_date: startDate,
    is_active: true,
    materialized_until: dayBefore(startDate),
  };
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const authError = requireAppSecret(req);
  if (authError) return authError;

  try {
    if (req.method === "GET") {
      const { data, error } = await supabase
        .from("task_series")
        .select(SERIES_SELECT)
        .eq("is_active", true)
        .order("created_at", { ascending: true });

      if (error) return internalErrorResponse(error);
      return jsonResponse({ series: data ?? [] });
    }

    if (req.method === "POST") {
      const body = await req.json();
      const insertPayload = buildInsertPayload(body);

      const { data, error } = await supabase
        .from("task_series")
        .insert(insertPayload)
        .select(SERIES_SELECT)
        .single();

      if (error) return internalErrorResponse(error);
      return jsonResponse({ series: data }, 201);
    }

    if (req.method === "PATCH") {
      const body = await req.json();
      const id = normalizeRequiredString(body?.id, "id");
      const updates: Record<string, unknown> = {};

      if (body?.materialized_until != null) {
        updates.materialized_until = normalizeDate(body.materialized_until, "materialized_until");
      }
      if (body?.is_active != null) {
        updates.is_active = Boolean(body.is_active);
      }

      const { data, error } = await supabase
        .from("task_series")
        .update(updates)
        .eq("id", id)
        .select(SERIES_SELECT)
        .single();

      if (error) return internalErrorResponse(error);
      return jsonResponse({ series: data });
    }

    if (req.method === "DELETE") {
      const url = new URL(req.url);
      const id = url.searchParams.get("id")?.trim();
      if (!id) return errorResponse("id is required", 400);

      const today = new Date().toISOString().slice(0, 10);

      const { error: deleteTasksError } = await supabase
        .from("tasks")
        .delete()
        .eq("series_id", id)
        .eq("is_done", false)
        .gte("day", today);

      if (deleteTasksError) return internalErrorResponse(deleteTasksError);

      const { error } = await supabase
        .from("task_series")
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
