#!/usr/bin/env bash
set -euo pipefail

PROJECT_REF="pdcxxhnybykfbbvnnzki"

if ! command -v supabase >/dev/null 2>&1; then
  echo "Supabase CLI not found. Install it first: https://supabase.com/docs/guides/cli" >&2
  exit 1
fi

supabase link --project-ref "${PROJECT_REF}"

supabase functions deploy api-labels --no-verify-jwt
supabase functions deploy api-tasks --no-verify-jwt
supabase functions deploy api-settings --no-verify-jwt
supabase functions deploy api-task-series --no-verify-jwt

echo "Done."
