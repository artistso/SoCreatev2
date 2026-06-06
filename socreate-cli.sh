#!/bin/bash
# SoCreate CLI Pipeline - Ramp-Up Edition
# Local helper for development, zipping, status, agent workflow, and preparing GitHub pushes.
# Usage: ./scripts/socreate-cli.sh <command>

set -e

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DATE=$(date +%Y-%m-%d)
ZIP_NAME="socreate-${DATE}-rampup.zip"

show_help() {
  cat << EOF
SoCreate CLI - Ramp-Up Edition

Commands:
  zip             Generate dated source zip for re-upload/import
  status          Show current project status from README
  update-readme   Interactively add new milestone to README
  prepare-push    Print exact git/gh commands to commit & push
  build-debug     Try local debug build (if gradle available)
  agent-sync      Prepare for incorporating agent-generated changes
  full-ramp       Run zip + status + prepare (one-command ramp prep)
  bump-version    Guidance for version bump + tag
  help            This help

Workflow with Agent (Arena.ai):
1. Tell the agent the next feature or "your choice".
2. Agent edits files here, updates README, produces zip.
3. You run: ./scripts/socreate-cli.sh full-ramp
4. Copy changes/zip into your GitHub clone.
5. Add GH_PAT secret in repo Settings (if not done).
6. Push using the printed commands. CI/CD runs automatically.

Never paste your GH_PAT token here.
EOF
}

do_zip() {
  echo "Generating ramp-up zip: $ZIP_NAME"
  cd "$PROJECT_ROOT"
  zip -r "../$ZIP_NAME" . -x "*.zip" -x "build/*" -x ".gradle/*" -x "*/build/*" -x "local.properties" -x ".git/*" -x "*/.git/*" --quiet || true
  echo "Created ../$ZIP_NAME"
  ls -lh "../$ZIP_NAME"
}

show_status() {
  echo "=== Current SoCreate Status ==="
  sed -n '/## Current Status/,/## Next Steps/p' "$PROJECT_ROOT/README.md" | head -40
}

update_readme() {
  echo "New milestone description:"
  read -r DESC
  echo "Build name (e.g. 'CI/CD Ramp-Up & Agent Integration'):"
  read -r NAME
  DATE_STR=$(date +%Y-%m-%d)
  ENTRY="- **${NAME} (${DATE_STR})**: ${DESC}"
  sed -i "/- Layers & Compositing with blends/i ${ENTRY}" "$PROJECT_ROOT/README.md" || echo "Edit README manually if sed fails."
  echo "Added. Review and commit."
}

prepare_push() {
  echo "=== Git Commands for Your Repo ==="
  echo "cd /path/to/your/cloned/socreate"
  echo "git status"
  echo "git add ."
  echo "git commit -m \"chore: ramp-up CI/CD + agent prep $(date +%Y-%m-%d) - SoCreate\""
  echo "git push origin main"
  echo ""
  echo "For release: git tag v0.2.0 && git push origin v0.2.0"
  echo ""
  echo "After push, GitHub Actions will build using your GH_PAT secret."
}

build_debug() {
  if [ -f "$PROJECT_ROOT/gradlew" ]; then
    cd "$PROJECT_ROOT"
    chmod +x gradlew
    ./gradlew assembleDebug --stacktrace || echo "Needs Android SDK."
  else
    echo "gradlew missing in this workspace (source snapshot). Build after cloning full repo."
  fi
}

agent_sync() {
  echo "=== Agent Change Sync ==="
  echo "Agent made changes in this /home/user/socreate workspace."
  echo "Options:"
  echo "1. Use the latest zip from 'zip' command."
  echo "2. Manually copy modified files to your clone."
  echo "3. Run 'full-ramp' then follow prepare-push instructions."
  echo "Once in your clone, commit and push as above."
}

full_ramp() {
  echo "=== Full Ramp-Up Prep ==="
  do_zip
  show_status
  echo ""
  agent_sync
  prepare_push
  echo "✅ Ready for re-upload. Add GH_PAT secret if missing, then push."
}

bump_version() {
  echo "Update versionCode/versionName in app/build.gradle.kts"
  echo "Then: git add . && git commit -m 'bump version' && git tag vX.Y.Z && git push origin vX.Y.Z"
  echo "This will trigger the Release workflow."
}

case "${1:-help}" in
  zip) do_zip ;;
  status) show_status ;;
  update-readme) update_readme ;;
  prepare-push) prepare_push ;;
  build-debug) build_debug ;;
  agent-sync) agent_sync ;;
  full-ramp) full_ramp ;;
  bump-version) bump_version ;;
  help|*) show_help ;;
esac
