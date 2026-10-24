#!/bin/sh
# preToolUse guardrail (matcher: Write|Delete) for Cursor (editor agent and CLI).
# Root README.md (the assignment), the characterization tests and ArchitectureTest are read-only for agents
# (AGENTS.md, Hard rules).
# Project hooks run from the project root, so $PWD is the repo root.
# Input (stdin): {"tool_name": "Write", "tool_input": {...}, "cwd": "...", ...}
# Output (stdout): {"permission": "allow"|"deny", "user_message", "agent_message"}

input=$(cat)

if ! command -v jq >/dev/null 2>&1; then
  printf '%s\n' '{"permission":"allow"}'
  exit 0
fi

path=$(printf '%s' "$input" | jq -r '.tool_input | if type == "object" then (.file_path // .path // .target_file // .filePath // "") else "" end')
root=$(pwd -P)
chartest=kotlin/src/test/kotlin/org/ikigaidigital/TimeDepositCalculatorTest.kt
archtest=kotlin/src/test/kotlin/org/ikigaidigital/ArchitectureTest.kt

case "$path" in
  README.md | ./README.md | "$root/README.md" | "$PWD/README.md")
    printf '%s\n' '{"permission":"deny","user_message":"Blocked by .cursor/hooks/guard-readme.sh: root README.md is read-only for agents.","agent_message":"README.md is read-only (AGENTS.md, Hard rules). Put new docs in kotlin/README.md or docs/."}'
    ;;
  "$chartest" | "./$chartest" | "$root/$chartest" | "$PWD/$chartest")
    printf '%s\n' '{"permission":"deny","user_message":"Blocked by .cursor/hooks/guard-readme.sh: the characterization tests are read-only for agents.","agent_message":"TimeDepositCalculatorTest.kt is read-only (AGENTS.md, Hard rules). A failing test there means behaviour changed: fix the code, and put new tests in new files."}'
    ;;
  "$archtest" | "./$archtest" | "$root/$archtest" | "$PWD/$archtest")
    printf '%s\n' '{"permission":"deny","user_message":"Blocked by .cursor/hooks/guard-readme.sh: ArchitectureTest is read-only for agents.","agent_message":"ArchitectureTest.kt is read-only (AGENTS.md, Hard rules). A failing rule there means the code crossed a hexagonal boundary: fix the code, and put new tests in new files."}'
    ;;
  *)
    printf '%s\n' '{"permission":"allow"}'
    ;;
esac
exit 0
