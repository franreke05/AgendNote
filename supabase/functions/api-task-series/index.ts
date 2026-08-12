import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders } from "../_shared/cors.ts";
import { errorResponse, internalErrorResponse, jsonResponse } from "../_shared/response.ts";
import { requireAppSecret } from "../_shared/auth.ts";
import { LIMITS, ValidationError, normalizeOptionalText, normalizeRequiredText, normalizeStringArray, readJsonBody } from "../_shared/validation.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? Deno.env.get("SB_URL");
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SB_SERVICE_ROLE_KEY");
const SERIES_SELECT = "id,title,body,time,recurrence_type,days_of_week,day_of_month,label_ids,start_date,is_active,materialized_until,created_at,end_type,end_date,end_occurrences";

if (!supabaseUrl || !serviceKey) {
  throw new Error("Missing SUPABASE_URL/SB_URL or SUPABASE_SERVICE_ROLE_KEY/SB_SERVICE_ROLE_KEY");
}

const supabase = createClient(supabaseUrl, serviceKey, {
  auth: { persistSession: false },
});

function normalizeDate(value: unknown, field: string) {
  const trimmed = String(value ?? "").trim();
  if (!/^\d{4}-\d{2}-\d{2}$/.test(trimmed)) throw new Error(`${field} must be YYYY-MM-DD`);
  return trimmed;
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

function normalizeWeekdays(value: unknown, recurrenceType: string) {
  if (value == null) return null;
  if (!Array.isArray(value) || value.length > 7) {
    throw new ValidationError("days_of_week must contain at most 7 items");
  }
  const days = value.map(Number);
  if (days.some((day) => !Number.isInteger(day) || day < 1 || day > 7)) {
    throw new ValidationError("days_of_week values must be between 1 and 7");
  }
  if (recurrenceType === "weekly_days" && days.length === 0) {
    throw new ValidationError("weekly_days requires at least one weekday");
  }
  return Array.from(new Set(days)).sort((a, b) => a - b);
}

function normalizeDayOfMonth(value: unknown, recurrenceType: string) {
  if (value == null) return null;
  const day = Number(value);
  if (!Number.isInteger(day) || day < 1 || day > 31) {
    throw new ValidationError("day_of_month must be between 1 and 31");
  }
  if (recurrenceType === "monthly") return day;
  return null;
}

function buildEndFields(body: Record<string, unknown>, startDate: string) {
  const endType = normalizeOptionalText(body.end_type, "end_type", 32) ?? "never";
  if (!["never", "on_date", "after_occurrences"].includes(endType)) {
    throw new Error("end_type must be never, on_date, or after_occurrences");
  }
  if (endType === "on_date") {
    const endDate = normalizeDate(body.end_date, "end_date");
    if (endDate < startDate) throw new Error("end_date must be on or after start_date");
    return { end_type: endType, end_date: endDate, end_occurrences: null };
  }
  if (endType === "after_occurrences") {
    const count = Number(body.end_occurrences);
    if (!Number.isInteger(count) || count < 1) {
      throw new Error("end_occurrences must be a positive integer");
    }
    return { end_type: endType, end_date: null, end_occurrences: count };
  }
  return { end_type: "never", end_date: null, end_occurrences: null };
}

function buildInsertPayload(body: Record<string, unknown>) {
  const recurrenceType = normalizeRequiredText(body.recurrence_type, "recurrence_type", 32);
  if (!["daily", "weekly_days", "monthly"].includes(recurrenceType)) {
    throw new Error("recurrence_type must be daily, weekly_days, or monthly");
  }
  const startDate = normalizeDate(body.start_date, "start_date");
  return {
    title: normalizeRequiredText(body.title, "title", LIMITS.seriesTitle),
    body: normalizeOptionalText(body.body, "body", LIMITS.seriesBody),
    time: normalizeOptionalText(body.time, "time", LIMITS.reminderLength),
    recurrence_type: recurrenceType,
    days_of_week: normalizeWeekdays(body.days_of_week, recurrenceType),
    day_of_month: normalizeDayOfMonth(body.day_of_month, recurrenceType),
    label_ids: normalizeStringArray(body.label_ids, "label_ids", LIMITS.seriesLabelCount, LIMITS.reminderLength),
    start_date: startDate,
    is_active: true,
    materialized_until: dayBefore(startDate),
    ...buildEndFields(body, startDate),
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
      const body = await readJsonBody(req) as Record<string, unknown>;
      const insertPayload = buildInsertPayload(body) as Record<string, unknown>;

      const { data, error } = await supabase
        .from("task_series")
        .insert(insertPayload)
        .select(SERIES_SELECT)
        .single();

      if (error) return internalErrorResponse(error);
      return jsonResponse({ series: data }, 201);
    }

    if (req.method === "PATCH") {
      const body = await readJsonBody(req) as Record<string, unknown>;
      const id = normalizeRequiredText(body?.id, "id", LIMITS.reminderLength);
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
    if (error instanceof ValidationError) return errorResponse(error.message, 400);
    return internalErrorResponse(error);
  }
});
