#!/bin/sh
# PreToolUse guardrails for GitHub Copilot in VS Code (Local and Copilot harnesses).
# Mirrors the deny/ask lists in .claude/settings.json and .codex/rules/guardrails.rules.
# Reads the VS Code-compatible payload (tool_name, tool_input) on stdin.
# Deny: print the reason, exit 2. Ask: print a decision JSON, exit 0. Allow: exit 0, no output.
# Never exit non-zero by accident: the Copilot harness treats a crashed hook as a deny.

input=$(cat)

if ! command -v jq >/dev/null 2>&1; then
  echo "guardrails: jq not found, hook skipped (pre-commit hook still applies)" >&2
  exit 0
fi

tool=$(printf '%s' "$input" | jq -r '.tool_name // .toolName // empty' 2>/dev/null)
args=$(printf '%s' "$input" | jq -c '.tool_input // (.toolArgs | if type == "string" then (try fromjson catch {}) else . end) // {}' 2>/dev/null)
[ -n "$args" ] || args='{}'

decide() { # $1 = deny|ask, $2 = reason
  jq -cn --arg d "$1" --arg r "$2" \
    '{permissionDecision: $d, permissionDecisionReason: $r,
      hookSpecificOutput: {hookEventName: "PreToolUse", permissionDecision: $d, permissionDecisionReason: $r}}'
  if [ "$1" = deny ]; then
    echo "guardrails: $2" >&2
    exit 2
  fi
  exit 0
}

# --- G1: root README.md is read-only (edit-type tools only; reads stay allowed) ---
if printf '%s' "$tool" | grep -Eqi 'edit|write|create|replace|patch|insert|delete|rename|move'; then
  root=$(git rev-parse --show-toplevel 2>/dev/null || pwd)
  printf '%s' "$args" | jq -r '
      [.. | strings] | .[]
      | (capture("\\*\\*\\* (Update|Add|Delete) File: (?<p>[^\n]+)"; "g") | .p), .' 2>/dev/null |
    while IFS= read -r p; do
      p=${p#./}
      case "$p" in "$root"/*) p=${p#"$root"/} ;; esac
      if [ "$(printf '%s' "$p" | tr '[:upper:]' '[:lower:]')" = "readme.md" ]; then
        echo hit
        break
      fi
    done | grep -q hit &&
    decide deny "README.md holds the assignment and is read-only for agents (AGENTS.md Hard rules). Put docs in kotlin/README.md or docs/."
fi

# --- Terminal commands: any tool whose input carries a command string ---
cmd=$(printf '%s' "$args" | jq -r 'if (.command | type) == "string" then .command else empty end' 2>/dev/null)
[ -n "$cmd" ] || exit 0

# Segment start (env assignments, command/env/exec wrappers), git, global options, then the subcommand
gp='^[ (]*([A-Za-z_][A-Za-z0-9_]*=[^ ]* +)*((command|env|exec|nohup|time) +)*([^ ]*/)?git( +-[Cc] +[^ ]+| +--?[A-Za-z][^ ]*)* +'
ask=""
segments=$(printf '%s\n' "$cmd" | tr ';&|' '\n\n\n')
while IFS= read -r seg; do
  seg=" $seg "
  case "$seg" in *git*) ;; *) continue ;; esac

  # G2: no hook bypass
  if printf '%s' "$seg" | grep -Eqi 'git .*-c *core\.hookspath'; then
    decide deny "git -c core.hooksPath bypasses the pre-commit guardrails. Commit normally."
  fi
  if printf '%s' "$seg" | grep -Eqi "${gp}"'config.*core\.hookspath' &&
     ! printf '%s' "$seg" | grep -Eqi 'git +config +(--local +)?core\.hookspath +\.githooks +$|--get|--list|-l '; then
    decide deny "Changing core.hooksPath disables the guardrails. The only allowed value is .githooks."
  fi
  if printf '%s' "$seg" | grep -Eq "${gp}"'commit\b' &&
     printf '%s' "$seg" | grep -Eq ' (--no-verify|-[A-Za-z]*n[A-Za-z]*)( |$)'; then
    decide deny "Pre-commit guardrails must run. Commit without --no-verify/-n and fix what the hook reports."
  fi

  # G3: no destructive git
  if printf '%s' "$seg" | grep -Eq "${gp}"'push\b' &&
     printf '%s' "$seg" | grep -Eq ' (--force[a-z-]*|-[A-Za-z]*f[A-Za-z]*|\+[^ ]+)(=| |$)'; then
    decide deny "History rewrites on the shared fork are off-limits. Push normally."
  fi
  if printf '%s' "$seg" | grep -Eq "${gp}"'reset\b.* --hard'; then
    decide deny "git reset --hard discards work. Use git restore on specific paths instead."
  fi
  if printf '%s' "$seg" | grep -Eq "${gp}"'clean\b'; then
    decide deny "git clean deletes untracked files. Remove specific files explicitly instead."
  fi

  # G4: git push asks first
  if printf '%s' "$seg" | grep -Eq "${gp}"'push\b'; then
    ask=1
  fi
done <<EOF
$segments
EOF

[ -n "$ask" ] && decide ask "Pushing publishes to GitHub; confirm with the user first."
exit 0
