import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders } from "../_shared/cors.ts";
import { errorResponse, internalErrorResponse, jsonResponse } from "../_shared/response.ts";
import { requireAppSecret } from "../_shared/auth.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? Deno.env.get("SB_URL");
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SB_SERVICE_ROLE_KEY");
const TASK_SELECT = "id,title,body,day,due_at,slot_end_at,deadline_at,is_done,order_index,created_at,updated_at,notified_at,series_id";
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

function buildInsertPayload(body: Record<string, unknown>) {
  return {
    title: normalizeRequiredString(body.title, "title"),
    body: normalizeOptionalString(body.body),
    day: normalizeRequiredDay(body.day),
    due_at: normalizeOptionalString(body.due_at),
    slot_end_at: normalizeOptionalString(body.slot_end_at),
    deadline_at: normalizeOptionalString(body.deadline_at),
    is_done: Boolean(body.is_done ?? false),
    order_index: Number(body.order_index ?? 0),
    series_id: normalizeOptionalString(body.series_id),
  };
}

function buildUpdatePayload(body: Record<string, unknown>) {
  const updates: Record<string, unknown> = {};

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
  if (hasField(body, "deadline_at")) {
    updates.deadline_at = normalizeOptionalString(body.deadline_at);
  }
  if (hasField(body, "is_done")) {
    updates.is_done = Boolean(body.is_done);
  }
  if (hasField(body, "order_index")) {
    updates.order_index = Number(body.order_index);
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

async function attachReminders(tasks: Array<Record<string, unknown>>) {
  if (tasks.length === 0) return [];
  const ids = tasks.map((task) => String(task.id));

  const { data, error } = await supabase
    .from("task_reminders")
    .select("task_id,remind_at")
    .in("task_id", ids)
    .order("remind_at", { ascending: true });

  if (error) throw new Error(error.message);

  const reminderMap = new Map<string, string[]>();
  for (const row of data ?? []) {
    const taskId = String(row.task_id);
    if (!reminderMap.has(taskId)) reminderMap.set(taskId, []);
    reminderMap.get(taskId)?.push(String(row.remind_at));
  }

  return tasks.map((task) => ({
    ...task,
    reminders: reminderMap.get(String(task.id)) ?? [],
  }));
}

async function attachSubtasks(tasks: Array<Record<string, unknown>>) {
  if (tasks.length === 0) return [];
  const ids = tasks.map((task) => String(task.id));

  const { data, error } = await supabase
    .from("task_subtasks")
    .select("id,task_id,title,is_done,order_index")
    .in("task_id", ids)
    .order("order_index", { ascending: true });

  if (error) throw new Error(error.message);

  const subtaskMap = new Map<string, Array<Record<string, unknown>>>();
  for (const row of data ?? []) {
    const taskId = String(row.task_id);
    if (!subtaskMap.has(taskId)) subtaskMap.set(taskId, []);
    subtaskMap.get(taskId)?.push({
      id: row.id,
      title: row.title,
      is_done: row.is_done,
      order_index: row.order_index,
    });
  }

  return tasks.map((task) => ({
    ...task,
    subtasks: subtaskMap.get(String(task.id)) ?? [],
  }));
}

/** Attaches labels, reminders and subtasks in one call - the shape every response needs. */
async function attachTaskExtras(tasks: Array<Record<string, unknown>>) {
  const withLabels = await attachLabels(tasks);
  const withReminders = await attachReminders(withLabels);
  return attachSubtasks(withReminders);
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

/** Replaces all reminders for a task with the given list of ISO instants (deduplicated). */
async function syncTaskReminders(taskId: string, reminders: unknown) {
  const normalized = normalizeStringArray(reminders) ?? [];

  const { error: deleteError } = await supabase
    .from("task_reminders")
    .delete()
    .eq("task_id", taskId);
  if (deleteError) throw new Error(deleteError.message);

  if (normalized.length === 0) return;

  const rows = normalized.map((remindAt) => ({
    task_id: taskId,
    remind_at: remindAt,
  }));
  const { error: insertError } = await supabase.from("task_reminders").insert(rows);
  if (insertError) throw new Error(insertError.message);
}

interface SubtaskInput {
  title: string;
  is_done: boolean;
  order_index: number;
}

function normalizeSubtaskArray(value: unknown): SubtaskInput[] {
  if (!Array.isArray(value)) return [];
  const normalized: SubtaskInput[] = [];
  value.forEach((item, index) => {
    if (typeof item !== "object" || item === null) return;
    const record = item as Record<string, unknown>;
    const title = normalizeOptionalString(record.title);
    if (!title) return;
    normalized.push({
      title,
      is_done: Boolean(record.is_done ?? false),
      order_index: Number(record.order_index ?? index),
    });
  });
  return normalized;
}

/**
 * Replaces all subtasks for a task with the given list. NOTE: like syncTaskLabels/
 * syncTaskReminders, this deletes and reinserts rather than upserting by id - every synced
 * subtask gets a new id. Acceptable because this only runs when the client explicitly sends an
 * updated "subtasks" array (see hasField(body, "subtasks") below), not on unrelated task
 * edits; still, client-side list keys should not assume a subtask's id survives a save that
 * touches the subtasks array.
 */
async function syncTaskSubtasks(taskId: string, subtasks: unknown) {
  const normalized = normalizeSubtaskArray(subtasks);

  const { error: deleteError } = await supabase
    .from("task_subtasks")
    .delete()
    .eq("task_id", taskId);
  if (deleteError) throw new Error(deleteError.message);

  if (normalized.length === 0) return;

  const rows = normalized.map((subtask) => ({
    task_id: taskId,
    title: subtask.title,
    is_done: subtask.is_done,
    order_index: subtask.order_index,
  }));
  const { error: insertError } = await supabase.from("task_subtasks").insert(rows);
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

      const tasksWithExtras = await attachTaskExtras(data ?? []);
      return jsonResponse({ tasks: tasksWithExtras });
    }

    if (req.method === "POST") {
      const body = await req.json();
      normalizeRequiredString(body?.title, "title");
      normalizeRequiredDay(body?.day);
      const hasLabelSync = hasField(body, "label_ids") || hasField(body, "label_names");
      const hasReminderSync = hasField(body, "reminders");
      const hasSubtaskSync = hasField(body, "subtasks");

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
      if (hasReminderSync) {
        await syncTaskReminders(String(data.id), body?.reminders);
      }
      if (hasSubtaskSync) {
        await syncTaskSubtasks(String(data.id), body?.subtasks);
      }

      const tasksWithExtras = await attachTaskExtras([data as Record<string, unknown>]);
      return jsonResponse({ task: tasksWithExtras[0] }, 201);
    }

    if (req.method === "PATCH") {
      const body = await req.json();
      const taskId = normalizeOptionalString(body?.id);
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
      if (hasField(body, "reminders")) {
        await syncTaskReminders(taskId, body?.reminders);
      }
      if (hasField(body, "subtasks")) {
        await syncTaskSubtasks(taskId, body?.subtasks);
      }

      const tasksWithExtras = await attachTaskExtras([task as Record<string, unknown>]);
      return jsonResponse({ task: tasksWithExtras[0] });
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
