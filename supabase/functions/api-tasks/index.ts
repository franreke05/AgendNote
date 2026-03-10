import { serve } from "https://deno.land/std@0.224.0/http/server.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";
import { corsHeaders } from "../_shared/cors.ts";
import { errorResponse, jsonResponse } from "../_shared/response.ts";
import { requireAppSecret } from "../_shared/auth.ts";

const supabaseUrl = Deno.env.get("SUPABASE_URL") ?? Deno.env.get("SB_URL");
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") ?? Deno.env.get("SB_SERVICE_ROLE_KEY");
const TASK_SELECT = "id,title,body,day,due_at,is_done,order_index,created_at,updated_at,notified_at,source,booking_status,appointment_id,client_name,client_email,client_phone";

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

      // The app reads existing tasks per day through GET /api-tasks?day=YYYY-MM-DD,
      // including tasks mirrored from the portfolio into the same agenda day.
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
      if (error) return errorResponse(error.message, 500);

      const tasksWithLabels = await attachLabels(data ?? []);
      return jsonResponse({ tasks: tasksWithLabels });
    }

    if (req.method === "POST") {
      const body = await req.json();
      const title = String(body?.title ?? "").trim();
      const day = String(body?.day ?? "").trim();

      if (!title) return errorResponse("title is required", 400);
      if (!parseDateParam(day)) return errorResponse("day is required (YYYY-MM-DD)", 400);

      // Portfolio-created tasks send title/body/day. The insert generates id in Postgres
      // and the response returns task.id/title/body/day for mirrored_task_id persistence.
      const insertPayload = {
        title,
        body: body?.body ?? null,
        day,
        due_at: body?.due_at ?? null,
        is_done: Boolean(body?.is_done ?? false),
        order_index: Number(body?.order_index ?? 0),
      };

      const { data, error } = await supabase
        .from("tasks")
        .insert(insertPayload)
        .select(TASK_SELECT)
        .single();

      if (error) return errorResponse(error.message, 500);

      const labelIds: string[] = Array.isArray(body?.label_ids) ? body.label_ids : [];
      if (labelIds.length > 0) {
        const rows = labelIds.map((labelId) => ({
          task_id: data.id,
          label_id: labelId,
        }));
        const { error: labelError } = await supabase.from("task_labels").insert(rows);
        if (labelError) return errorResponse(labelError.message, 500);
      }

      const tasksWithLabels = await attachLabels([data as Record<string, unknown>]);
      return jsonResponse({ task: tasksWithLabels[0] }, 201);
    }

    if (req.method === "PATCH") {
      const body = await req.json();
      const id = String(body?.id ?? "").trim();
      if (!id) return errorResponse("id is required", 400);

      const updates: Record<string, unknown> = {};
      if (body?.title != null) updates.title = String(body.title).trim();
      if (body?.body != null) updates.body = body.body;
      if (body?.day != null) {
        const parsedDay = parseDateParam(String(body.day));
        if (!parsedDay) return errorResponse("day must be YYYY-MM-DD", 400);
        updates.day = parsedDay;
      }
      if (body?.due_at !== undefined) updates.due_at = body.due_at;
      if (body?.is_done !== undefined) updates.is_done = Boolean(body.is_done);
      if (body?.order_index !== undefined) updates.order_index = Number(body.order_index);

      const { data, error } = await supabase
        .from("tasks")
        .update(updates)
        .eq("id", id)
        .select(TASK_SELECT)
        .single();

      if (error) return errorResponse(error.message, 500);

      if (Array.isArray(body?.label_ids)) {
        const labelIds: string[] = body.label_ids;
        const { error: deleteError } = await supabase
          .from("task_labels")
          .delete()
          .eq("task_id", id);
        if (deleteError) return errorResponse(deleteError.message, 500);

        if (labelIds.length > 0) {
          const rows = labelIds.map((labelId) => ({
            task_id: id,
            label_id: labelId,
          }));
          const { error: labelError } = await supabase.from("task_labels").insert(rows);
          if (labelError) return errorResponse(labelError.message, 500);
        }
      }

      const tasksWithLabels = await attachLabels([data as Record<string, unknown>]);
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

        if (error) return errorResponse(error.message, 500);
        return jsonResponse({ success: true });
      }
      const id = url.searchParams.get("id")?.trim();
      if (!id) return errorResponse("id is required", 400);

      const { error } = await supabase
        .from("tasks")
        .delete()
        .eq("id", id);

      if (error) return errorResponse(error.message, 500);
      return jsonResponse({ success: true });
    }

    return errorResponse("method not allowed", 405);
  } catch (error) {
    const message = error instanceof Error ? error.message : "unknown error";
    return errorResponse(message, 500);
  }
});
