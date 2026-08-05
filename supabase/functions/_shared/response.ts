import { corsHeaders } from "./cors.ts";

export function jsonResponse(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}

export function errorResponse(message: string, status = 400) {
  return jsonResponse({ error: message }, status);
}

/**
 * 500 response for an unexpected failure (a Postgres/PostgREST error, or anything caught by a
 * handler's top-level try/catch). Always returns a fixed, generic message - never `error`'s own
 * text. Postgres/PostgREST error messages routinely include internal schema details (constraint
 * names, table/column names); forwarding them to the client leaks that detail to anyone who can
 * reach the endpoint, not just to AgendNote's own UI. There is no server-side logging in this
 * project yet - `error` is accepted here so a future logging call has something to pass, but it
 * must never be forwarded in the response body itself.
 */
export function internalErrorResponse(_error: unknown) {
  return jsonResponse({ error: "internal error" }, 500);
}
