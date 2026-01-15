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

async function sendPush(tokens: Array<{ platform: string; token: string }>, title: string, body: string) {
  if (tokens.length === 0) return { sent: 0 };

  const fcmKey = Deno.env.get("FCM_SERVER_KEY");
  const apnsKeyId = Deno.env.get("APNS_KEY_ID");
  const apnsTeamId = Deno.env.get("APNS_TEAM_ID");
  const apnsPrivateKey = Deno.env.get("APNS_PRIVATE_KEY");
  const apnsBundleId = Deno.env.get("APNS_BUNDLE_ID");
  const apnsUseSandbox = (Deno.env.get("APNS_USE_SANDBOX") ?? "").toLowerCase() === "true";

  if (!fcmKey && !apnsKeyId) {
    return { sent: 0, skipped: true, reason: "missing push credentials" };
  }

  let sent = 0;

  for (const device of tokens) {
    if (device.platform === "android" && fcmKey) {
      const payload = {
        to: device.token,
        notification: { title, body },
        data: { title, body },
      };

      const response = await fetch("https://fcm.googleapis.com/fcm/send", {
        method: "POST",
        headers: {
          Authorization: `key=${fcmKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify(payload),
      });

      if (response.ok) sent += 1;
      continue;
    }

    if (device.platform === "ios" && apnsKeyId && apnsTeamId && apnsPrivateKey && apnsBundleId) {
      const result = await sendApns(
        device.token,
        title,
        body,
        apnsKeyId,
        apnsTeamId,
        apnsPrivateKey,
        apnsBundleId,
        apnsUseSandbox,
      );
      if (result) sent += 1;
      continue;
    }
  }

  return { sent };
}

function base64UrlEncode(data: Uint8Array): string {
  let str = "";
  for (const byte of data) str += String.fromCharCode(byte);
  return btoa(str).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function base64UrlEncodeJson(payload: Record<string, unknown>): string {
  const json = JSON.stringify(payload);
  const bytes = new TextEncoder().encode(json);
  return base64UrlEncode(bytes);
}

function normalizePem(pem: string): string {
  return pem.replace(/\\n/g, "\n").trim();
}

function pemToArrayBuffer(pem: string): ArrayBuffer {
  const cleaned = pem
    .replace("-----BEGIN PRIVATE KEY-----", "")
    .replace("-----END PRIVATE KEY-----", "")
    .replace(/\s+/g, "");
  const raw = atob(cleaned);
  const bytes = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i += 1) {
    bytes[i] = raw.charCodeAt(i);
  }
  return bytes.buffer;
}

async function createApnsJwt(keyId: string, teamId: string, privateKeyPem: string): Promise<string> {
  const header = { alg: "ES256", kid: keyId, typ: "JWT" };
  const payload = { iss: teamId, iat: Math.floor(Date.now() / 1000) };
  const signingInput = `${base64UrlEncodeJson(header)}.${base64UrlEncodeJson(payload)}`;

  const key = await crypto.subtle.importKey(
    "pkcs8",
    pemToArrayBuffer(normalizePem(privateKeyPem)),
    { name: "ECDSA", namedCurve: "P-256" },
    false,
    ["sign"],
  );

  const signature = await crypto.subtle.sign(
    { name: "ECDSA", hash: "SHA-256" },
    key,
    new TextEncoder().encode(signingInput),
  );

  const signatureEncoded = base64UrlEncode(new Uint8Array(signature));
  return `${signingInput}.${signatureEncoded}`;
}

async function sendApns(
  token: string,
  title: string,
  body: string,
  keyId: string,
  teamId: string,
  privateKeyPem: string,
  bundleId: string,
  useSandbox: boolean,
): Promise<boolean> {
  const jwt = await createApnsJwt(keyId, teamId, privateKeyPem);
  const endpoint = useSandbox
    ? "https://api.sandbox.push.apple.com/3/device/"
    : "https://api.push.apple.com/3/device/";
  const payload = {
    aps: {
      alert: { title, body },
      sound: "default",
    },
  };

  const response = await fetch(`${endpoint}${token}`, {
    method: "POST",
    headers: {
      authorization: `bearer ${jwt}`,
      "apns-topic": bundleId,
      "apns-push-type": "alert",
      "apns-priority": "10",
      "content-type": "application/json",
    },
    body: JSON.stringify(payload),
  });

  return response.ok;
}

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

    const nowIso = new Date().toISOString();
    const { data: tasks, error } = await supabase
      .from("tasks")
      .select("id,title,body,due_at,notified_at")
      .not("due_at", "is", null)
      .eq("is_done", false)
      .is("notified_at", null)
      .lte("due_at", nowIso);

    if (error) return errorResponse(error.message, 500);
    if (!tasks || tasks.length === 0) return jsonResponse({ sent: 0 });

    const { data: devices, error: devicesError } = await supabase
      .from("devices")
      .select("platform,token");

    if (devicesError) return errorResponse(devicesError.message, 500);

    let sentTotal = 0;
    for (const task of tasks) {
      const result = await sendPush(devices ?? [], task.title, task.body ?? "");
      sentTotal += result.sent ?? 0;
      if ((result.sent ?? 0) > 0) {
        await supabase
          .from("tasks")
          .update({ notified_at: nowIso })
          .eq("id", task.id);
      }
    }

    return jsonResponse({ sent: sentTotal, tasks: tasks.length });
  } catch (error) {
    const message = error instanceof Error ? error.message : "unknown error";
    return errorResponse(message, 500);
  }
});
