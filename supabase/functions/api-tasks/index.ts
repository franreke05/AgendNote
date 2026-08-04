import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders } from "../_shared/cors.ts";
import { errorResponse, internalErrorResponse, jsonResponse } from "../_shared/response.ts";
import { requireAppSecret } from "../_shared/auth.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? Deno.env.get("SB_URL");
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SB_SERVICE_ROLE_KEY");
const TASK_SELECT = "id,title,body,day,due_at,slot_end_at,is_done,order_index,created_at,updated_at,notified_at,source,booking_status,appointment_id,client_name,client_email,client_phone,series_id";
const DEFAULT_LABEL_COLOR = "#8C94A6";

if (!supabaseUrl || !serviceKey) {
  throw new Error("Missing SUPABASE_URL/SB_URL or SUPABASE_SERVICE_ROLE_KEY/SB_SERVICE_ROLE_KEY");
}

const supabase = createClient(supabaseUrl, serviceKey, {
  auth: { persistSession: false },
});

function parseDateParam(value: string | null) {
  if (!value) return null;
  const trimmed = value.trim();
  return /^\d{4}-\d{2}-\d{2}$/.test(trimmed) ? trimmed : null;
}

function hasField(body: unknown, field: string) {
  return typeof body === "object" && body !== null && field in body;
}

function normalizeOptionalString(value: unknown) {
  if (value == null) return null;
  const trimmed = String(value).trim();
  return trimmed.length > 0 ? trimmed : null;
}

function normalizeRequiredString(value: unknown, field: string) {
  const normalized = normalizeOptionalString(value);
  if (!normalized) {
    throw new Error(`${field} is required`);
  }
  return normalized;
}

function normalizeRequiredDay(value: unknown) {
  const day = parseDateParam(String(value ?? "").trim());
  if (!day) {
    throw new Error("day is required (YYYY-MM-DD)");
  }
  return day;
}

function normalizeStringArray(value: unknown) {
  if (!Array.isArray(value)) return null;
  const seen = new Set<string>();
  const normalized: string[] = [];

  for (const item of value) {
    const trimmed = normalizeOptionalString(item);
    if (!trimmed || seen.has(trimmed)) continue;
    seen.add(trimmed);
    normalized.push(trimmed);
  }

  return normalized;
}

function resolveSource(body: Record<string, unknown>, appointmentId: string | null) {
  if (hasField(body, "source")) {
    return normalizeOptionalString(body.source) ?? "manual";
  }
  if (appointmentId) return "portfolio_booking";
  return null;
}

function buildInsertPayload(body: Record<string, unknown>) {
  const appointmentId = normalizeOptionalString(body.appointment_id);
  return {
    title: normalizeRequiredString(body.title, "title"),
    body: normalizeOptionalString(body.body),
    day: normalizeRequiredDay(body.day),
    due_at: normalizeOptionalString(body.due_at),
    slot_end_at: normalizeOptionalString(body.slot_end_at),
    is_done: Boolean(body.is_done ?? false),
    order_index: Number(body.order_index ?? 0),
    source: resolveSource(body, appointmentId) ?? "manual",
    booking_status: normalizeOptionalString(body.booking_status),
    appointment_id: appointmentId,
    client_name: normalizeOptionalString(body.client_name),
    client_email: normalizeOptionalString(body.client_email),
    client_phone: normalizeOptionalString(body.client_phone),
    series_id: normalizeOptionalString(body.series_id),
  };
}

function buildUpdatePayload(body: Record<string, unknown>, options: { inferBookingSource?: boolean } = {}) {
  const updates: Record<string, unknown> = {};
  const appointmentId = normalizeOptionalString(body.appointment_id);

  if (hasField(body, "title")) {
    updates.title = normalizeRequiredString(body.title, "title");
  }
  if (hasField(body, "body")) {
    updates.body = normalizeOptionalString(body.body);
  }
  if (hasField(body, "day")) {
    const parsedDay = parseDateParam(String(body.day ?? "").trim());
    if (!parsedDay) throw new Error("day must be YYYY-MM-DD");
    updates.day = parsedDay;
  }
  if (hasField(body, "due_at")) {
    updates.due_at = normalizeOptionalString(body.due_at);
  }
  if (hasField(body, "slot_end_at")) {
    updates.slot_end_at = normalizeOptionalString(body.slot_end_at);
  }
  if (hasField(body, "is_done")) {
    updates.is_done = Boolean(body.is_done);
  }
  if (hasField(body, "order_index")) {
    updates.order_index = Number(body.order_index);
  }
  if (hasField(body, "source")) {
    updates.source = resolveSource(body, appointmentId) ?? "manual";
  } else if (options.inferBookingSource && appointmentId) {
    updates.source = "portfolio_booking";
  }
  if (hasField(body, "booking_status")) {
    updates.booking_status = normalizeOptionalString(body.booking_status);
  }
  if (hasField(body, "appointment_id")) {
    updates.appointment_id = appointmentId;
  }
  if (hasField(body, "client_name")) {
    updates.client_name = normalizeOptionalString(body.client_name);
  }
  if (hasField(body, "client_email")) {
    updates.client_email = normalizeOptionalString(body.client_email);
  }
  if (hasField(body, "client_phone")) {
    updates.client_phone = normalizeOptionalString(body.client_phone);
  }

  return updates;
}

async function attachLabels(tasks: Array<Record<string, unknown>>) {
  if (tasks.length === 0) return [];
  const ids = tasks.map((task) => String(task.id));

  const { data, error } = await supabase
    .from("task_labels")
    .select("task_id,label_id,labels(id,name,color_hex)")
    .in("task_id", ids);

  if (error) throw new Error(error.message);

  const labelMap = new Map<string, Array<Record<string, unknown>>>();
  for (const row of data ?? []) {
    const taskId = String(row.task_id);
    const label = row.labels ? row.labels : null;
    if (!label) continue;
    if (!labelMap.has(taskId)) labelMap.set(taskId, []);
    labelMap.get(taskId)?.push(label as Record<string, unknown>);
  }

  return tasks.map((task) => ({
    ...task,
    labels: labelMap.get(String(task.id)) ?? [],
  }));
}

async function fetchTaskByAppointmentId(appointmentId: string) {
  const { data, error } = await supabase
    .from("tasks")
    .select(TASK_SELECT)
    .eq("appointment_id", appointmentId)
    .maybeSingle();

  if (error) throw new Error(error.message);
  return data as Record<string, unknown> | null;
}

async function fetchTaskById(taskId: string) {
  const { data, error } = await supabase
    .from("tasks")
    .select(TASK_SELECT)
    .eq("id", taskId)
    .single();

  if (error) throw new Error(error.message);
  return data as Record<string, unknown>;
}

async function resolveLabelIds(labelIds: unknown, labelNames: unknown) {
  const normalizedIds = normalizeStringArray(labelIds) ?? [];
  const normalizedNames: string[] = [];
  const seenNames = new Set<string>();
  for (const name of normalizeStringArray(labelNames) ?? []) {
    const key = name.toLowerCase();
    if (seenNames.has(key)) continue;
    seenNames.add(key);
    normalizedNames.push(name);
  }
  const combinedIds = new Set(normalizedIds);

  if (normalizedNames.length === 0) {
    return Array.from(combinedIds);
  }

  const { data: existingLabels, error: labelsError } = await supabase
    .from("labels")
    .select("id,name");

  if (labelsError) throw new Error(labelsError.message);

  const labelIdByName = new Map<string, string>();
  for (const label of existingLabels ?? []) {
    const name = normalizeOptionalString(label.name);
    const id = normalizeOptionalString(label.id);
    if (!name || !id) continue;
    labelIdByName.set(name.toLowerCase(), id);
  }

  const missingNames = normalizedNames.filter((name) => !labelIdByName.has(name.toLowerCase()));
  if (missingNames.length > 0) {
    const rows = missingNames.map((name) => ({
      name,
      color_hex: DEFAULT_LABEL_COLOR,
    }));
    const { data: createdLabels, error: createError } = await supabase
      .from("labels")
      .insert(rows)
      .select("id,name");

    if (createError) throw new Error(createError.message);

    for (const label of createdLabels ?? []) {
      const name = normalizeOptionalString(label.name);
      const id = normalizeOptionalString(label.id);
      if (!name || !id) continue;
      labelIdByName.set(name.toLowerCase(), id);
    }
  }

  for (const name of normalizedNames) {
    const labelId = labelIdByName.get(name.toLowerCase());
    if (labelId) combinedIds.add(labelId);
  }

  return Array.from(combinedIds);
}

async function syncTaskLabels(taskId: string, labelIds: unknown, labelNames: unknown) {
  const resolvedLabelIds = await resolveLabelIds(labelIds, labelNames);

  const { error: deleteError } = await supabase
    .from("task_labels")
    .delete()
    .eq("task_id", taskId);
  if (deleteError) throw new Error(deleteError.message);

  if (resolvedLabelIds.length === 0) return;

  const rows = resolvedLabelIds.map((labelId) => ({
    task_id: taskId,
    label_id: labelId,
  }));
  const { error: insertError } = await supabase.from("task_labels").insert(rows);
  if (insertError) throw new Error(insertError.message);
}

serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const authError = requireAppSecret(req);
  if (authError) return authError;

  try {
    if (req.method === "GET") {
      const url = new URL(req.url);
      const day = parseDateParam(url.searchParams.get("day"));
      const from = parseDateParam(url.searchParams.get("from"));
      const to = parseDateParam(url.searchParams.get("to"));

      let query = supabase
        .from("tasks")
        .select(TASK_SELECT)
        .order("day", { ascending: true })
        .order("order_index", { ascending: true })
        .order("due_at", { ascending: true });

      if (day) {
        query = query.eq("day", day);
      } else if (from && to) {
        query = query.gte("day", from).lte("day", to);
      }

      const { data, error } = await query;
      if (error) return internalErrorResponse(error);

      const tasksWithLabels = await attachLabels(data ?? []);
      return jsonResponse({ tasks: tasksWithLabels });
    }

    if (req.method === "POST") {
      const body = await req.json();
      normalizeRequiredString(body?.title, "title");
      normalizeRequiredDay(body?.day);
      const appointmentId = normalizeOptionalString(body?.appointment_id);
      const hasLabelSync = hasField(body, "label_ids") || hasField(body, "label_names");

      if (appointmentId) {
        const existingTask = await fetchTaskByAppointmentId(appointmentId);
        if (existingTask) {
          const updates = buildUpdatePayload(body, { inferBookingSource: true });
          const { data, error } = await supabase
            .from("tasks")
            .update(updates)
            .eq("id", String(existingTask.id))
            .select(TASK_SELECT)
            .single();

          if (error) return internalErrorResponse(error);
          if (hasLabelSync) {
            await syncTaskLabels(String(existingTask.id), body?.label_ids, body?.label_names);
          }

          const tasksWithLabels = await attachLabels([data as Record<string, unknown>]);
          return jsonResponse({ task: tasksWithLabels[0] });
        }
      }

      const insertPayload = buildInsertPayload(body);
      const { data, error } = await supabase
        .from("tasks")
        .insert(insertPayload)
        .select(TASK_SELECT)
        .single();

      if (error) return internalErrorResponse(error);
      if (hasLabelSync) {
        await syncTaskLabels(String(data.id), body?.label_ids, body?.label_names);
      }

      const tasksWithLabels = await attachLabels([data as Record<string, unknown>]);
      return jsonResponse({ task: tasksWithLabels[0] }, 201);
    }

    if (req.method === "PATCH") {
      const body = await req.json();
      let taskId = normalizeOptionalString(body?.id);
      if (!taskId) {
        const appointmentId = normalizeOptionalString(body?.appointment_id);
        if (appointmentId) {
          const existingTask = await fetchTaskByAppointmentId(appointmentId);
          taskId = existingTask ? String(existingTask.id) : null;
        }
      }
      if (!taskId) return errorResponse("id is required", 400);

      const updates = buildUpdatePayload(body);
      let task = Object.keys(updates).length > 0
        ? null
        : await fetchTaskById(taskId);

      if (Object.keys(updates).length > 0) {
        const { data, error } = await supabase
          .from("tasks")
          .update(updates)
          .eq("id", taskId)
          .select(TASK_SELECT)
          .single();

        if (error) return internalErrorResponse(error);
        task = data as Record<string, unknown>;
      }

      if (hasField(body, "label_ids") || hasField(body, "label_names")) {
        await syncTaskLabels(taskId, body?.label_ids, body?.label_names);
      }

      const tasksWithLabels = await attachLabels([task as Record<string, unknown>]);
      return jsonResponse({ task: tasksWithLabels[0] });
    }

    if (req.method === "DELETE") {
      const url = new URL(req.url);
      const all = url.searchParams.get("all")?.trim();
      if (all === "true" || all === "1") {
        const { error } = await supabase
          .from("tasks")
          .delete()
          .neq("id", "00000000-0000-0000-0000-000000000000");

        if (error) return internalErrorResponse(error);
        return jsonResponse({ success: true });
      }
      const id = url.searchParams.get("id")?.trim();
      if (!id) return errorResponse("id is required", 400);

      const { error } = await supabase
        .from("tasks")
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
