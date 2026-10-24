#!/bin/sh
# beforeShellExecution guardrail for Cursor (editor agent and CLI).
# Mirrors .claude/settings.json and .codex/rules/guardrails.rules:
#   deny: git commit --no-verify/-n, any --no-verify, git -c core.hooksPath=..,
#         changing core.hooksPath, force push, git reset --hard, git clean
#   ask:  git push
# Input (stdin): {"command": "...", "cwd": "...", "sandbox": false}
# Output (stdout): {"permission": "allow"|"deny"|"ask", "user_message", "agent_message"}

input=$(cat)

if ! command -v jq >/dev/null 2>&1; then
  printf '%s\n' '{"permission":"ask","user_message":"Cursor guard hook needs jq to inspect git commands; approve manually.","agent_message":"The guard hook could not inspect this command (jq missing). Wait for the user to approve."}'
  exit 0
fi

cmd=$(printf '%s' "$input" | jq -r '.command // empty')

verdict=$(printf '%s\n' "$cmd" | awk '
function strip(t) { gsub(/["'"'"'`()]/, "", t); return t }
BEGIN { out = "allow" }
{
  line = $0
  gsub(/&&|\|\||[;|&]/, "\n", line)
  nseg = split(line, segs, "\n")
  for (s = 1; s <= nseg; s++) {
    n = split(segs[s], tok, /[ \t]+/)
    for (k = 1; k <= n; k++) tok[k] = strip(tok[k])
    g = 0
    for (k = 1; k <= n; k++) if (tok[k] == "git" || tok[k] ~ /\/git$/) { g = k; break }
    if (!g) continue
    sub_ = ""; j = g + 1
    while (j <= n) {
      t = tok[j]
      if (t == "") { j++; continue }
      if (t == "-c") { if (tolower(tok[j+1]) ~ /^core\.hookspath/) { print "deny:G2"; exit } j += 2; continue }
      if (t ~ /^-c/ && tolower(t) ~ /core\.hookspath/) { print "deny:G2"; exit }
      if (t == "-C" || t == "--git-dir" || t == "--work-tree" || t == "--namespace") { j += 2; continue }
      if (t ~ /^-/) { j++; continue }
      sub_ = t; break
    }
    hooks_read = 0; mentions_hooks = 0
    for (a = j + 1; a <= n; a++) {
      t = tok[a]
      if (t == "--no-verify") { print "deny:G2"; exit }
      if (sub_ == "commit" && t ~ /^-[A-Za-z]*n[A-Za-z]*$/) { print "deny:G2"; exit }
      if (sub_ == "push" && (t ~ /^--force/ || t == "--mirror" || t ~ /^-[A-Za-z]*f[A-Za-z]*$/ || t ~ /^\+/)){ print "deny:G3"; exit }
      if (sub_ == "reset" && t == "--hard") { print "deny:G3"; exit }
      if (sub_ == "config") {
        if (tolower(t) ~ /core\.hookspath/) mentions_hooks = 1
        if (t == "--get" || t == "--get-all" || t == "get" || t == "--list" || t == "-l") hooks_read = 1
      }
    }
    if (sub_ == "config" && mentions_hooks && !hooks_read) { print "deny:G2"; exit }
    if (sub_ == "clean") { print "deny:G3"; exit }
    if (sub_ == "push") out = "ask"
  }
}
END { print out }
' | head -n 1)
# awk "exit" still runs END, so the first printed line is the verdict.

case "$verdict" in
  deny:G2)
    printf '%s\n' '{"permission":"deny","user_message":"Blocked by .cursor/hooks/guard-shell.sh: the pre-commit hook must not be bypassed (--no-verify, -n, core.hooksPath).","agent_message":"Pre-commit guardrails must run. Commit without --no-verify/-n, leave core.hooksPath alone, and fix what the hook reports."}'
    ;;
  deny:G3)
    printf '%s\n' '{"permission":"deny","user_message":"Blocked by .cursor/hooks/guard-shell.sh: force push, git reset --hard and git clean are off-limits.","agent_message":"Destructive git commands are forbidden. Push normally, use git restore on specific paths, or remove specific files explicitly."}'
    ;;
  ask)
    printf '%s\n' '{"permission":"ask","user_message":"git push publishes to GitHub. Approve only if you meant to push.","agent_message":"git push needs explicit user approval."}'
    ;;
  *)
    printf '%s\n' '{"permission":"allow"}'
    ;;
esac
exit 0
