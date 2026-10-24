# Shared agent skills

Copied unmodified from [mattpocock/skills](https://github.com/mattpocock/skills) (MIT, see `LICENSE`) at the commit pinned in `skills.lock`. Codex, Cursor, Gemini CLI and Copilot discover skills in this folder. Claude Code gets the same skills from the `mattpocock-skills` plugin enabled in `.claude/settings.json`.

They're vendored so every agent gets the same reviewed version without extra installs.

## Keeping up to date

- **Automatic**: `.github/workflows/sync-skills.yml` runs weekly. When upstream changed any of the vendored skills, it opens a PR. Review the diff before merging. This needs the repo setting *Settings → Actions → General → Allow GitHub Actions to create and approve pull requests*.
- **Manual**:
  - `scripts/sync-skills.sh latest` bumps to upstream HEAD.
  - `scripts/sync-skills.sh <sha>` pins a specific commit.
  - Running it with no argument re-syncs at the pinned commit.
  - Review the result with `git diff` and commit it.
- **Add or remove a skill**: edit `paths` in `skills.lock` (delete the folder too when removing one), then run the script.
- **Claude Code plugin**: updates separately through its marketplace (`/plugin update`), so the plugin and this folder can briefly differ.
