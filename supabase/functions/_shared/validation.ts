export const MAX_REQUEST_BYTES = 128 * 1024;

export const LIMITS = {
  taskTitle: 200,
  taskBody: 8_000,
  labelName: 64,
  labelColor: 7,
  labelCount: 50,
  reminderCount: 8,
  reminderLength: 64,
  subtaskCount: 100,
  subtaskTitle: 200,
  seriesTitle: 200,
  seriesBody: 8_000,
  seriesLabelCount: 50,
  settingsKey: 64,
  settingsValue: 32_768,
  devicePlatform: 16,
  deviceToken: 4_096,
} as const;

export class ValidationError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "ValidationError";
  }
}

export async function readJsonBody(req: Request): Promise<unknown> {
  const contentLength = Number(req.headers.get("content-length") ?? 0);
  if (Number.isFinite(contentLength) && contentLength > MAX_REQUEST_BYTES) {
    throw new ValidationError("request body is too large");
  }

  const raw = await req.text();
  if (new TextEncoder().encode(raw).byteLength > MAX_REQUEST_BYTES) {
    throw new ValidationError("request body is too large");
  }

  try {
    return JSON.parse(raw);
  } catch {
    throw new ValidationError("invalid JSON body");
  }
}

export function normalizeOptionalText(value: unknown, field: string, maxLength: number) {
  if (value == null) return null;
  const trimmed = String(value).trim();
  if (trimmed.length > maxLength) {
    throw new ValidationError(`${field} exceeds the maximum length of ${maxLength}`);
  }
  return trimmed.length > 0 ? trimmed : null;
}

export function normalizeRequiredText(value: unknown, field: string, maxLength: number) {
  const normalized = normalizeOptionalText(value, field, maxLength);
  if (!normalized) throw new ValidationError(`${field} is required`);
  return normalized;
}

export function normalizeStringArray(
  value: unknown,
  field: string,
  maxItems: number,
  maxItemLength: number,
) {
  if (value == null) return [] as string[];
  if (!Array.isArray(value)) throw new ValidationError(`${field} must be an array`);
  if (value.length > maxItems) {
    throw new ValidationError(`${field} cannot contain more than ${maxItems} items`);
  }

  const seen = new Set<string>();
  const normalized: string[] = [];
  for (const item of value) {
    const text = normalizeOptionalText(item, field, maxItemLength);
    if (!text || seen.has(text)) continue;
    seen.add(text);
    normalized.push(text);
  }
  return normalized;
}

export function requireHexColor(value: unknown, field: string) {
  const color = normalizeRequiredText(value, field, LIMITS.labelColor);
  if (!/^#[0-9a-f]{6}$/i.test(color)) {
    throw new ValidationError(`${field} must be a six-digit hex color`);
  }
  return color.toUpperCase();
}
