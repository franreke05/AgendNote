$ErrorActionPreference = "Stop"

$projectRef = "pdcxxhnybykfbbvnnzki"
$functions = @(
    "api-labels",
    "api-tasks",
    "api-settings",
    "api-task-series"
)

if (-not (Get-Command supabase -ErrorAction SilentlyContinue)) {
    throw "Supabase CLI not found. Install it first: https://supabase.com/docs/guides/cli"
}

supabase link --project-ref $projectRef

foreach ($functionName in $functions) {
    supabase functions deploy $functionName --no-verify-jwt
}

Write-Host "Done."
