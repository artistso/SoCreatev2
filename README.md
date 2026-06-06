# SoCreate – Professional Drawing & Animation Studio for Android

**A free, local-first, no-AI, open-source alternative to Procreate Dreams 2**

For true professional artists and animators. 100% local storage, no cloud, no subscriptions, no telemetry, no AI features.

## Current Status: Functional Phase 1 Core (Drawing Works!)

**Major progress since initial skeleton:**
- **Actual drawing now works!** Brush stamps pixels directly into layers using CPU (soft alpha blending with pressure and the provided brush texture).
- Multi-touch pan & zoom (two fingers to pan/zoom the canvas, single finger to draw).
- Tool switching: Brush, Eraser, Fill (tap with fill tool to flood fill).
- Tiled canvas rendering with OpenGL (tiles upload on dirty).
- Basic onion skinning (previous frame ghosts).
- Full local .socreate save/load with the binary format.
- Undo/Redo, frame management, brush size/opacity controls.
- Clean Jetpack Compose UI with floating controls.

This is now a **usable basic professional drawing tool** on Android. You can draw, erase, fill, save projects, switch frames, and pan around a large canvas.

## How to Build & Run
1. Download `socreate-functional-phase1.zip` (or the source tree).
2. Extract and open in Android Studio (API 29+ / Android 10+ device or emulator recommended).
3. Replace placeholder mipmap icons with real ones (Image Asset Studio).
4. Sync and Run.
5. Use a stylus for best pressure sensitivity.

**Important:** The current GL projection for tiles is basic/approximate (hard-coded offsets). Lines may appear slightly offset at extreme zoom. This will be refined in the next iteration with a proper MVP matrix.

## Next Priorities (Phase 1 Polish + Phase 2)
- Improve tile projection and add proper camera matrix.
- Add color picker (HSV wheel as a circular panel).
- Real brush texture preview.
- Layer system (multiple layers per frame).
- Full onion skin controls + frame duration.
- Move to Phase 2: Gesture-based timeline, real-time playback, X-sheet.

## Full Vision
See the comprehensive feature list in the original conversation (TVPaint + Toon Boom Harmony + Rough Animator + circular UI + tiled infinite canvas + predictive smoothing + local collaboration + accessibility + much more).

Everything remains **100% local, no AI, no APIs**.

## Project Structure
Standard Android + Compose + OpenGL ES 3.0.

GitHub Actions workflow is included for automatic APK builds.

## License
GNU GPL v3

**Let's keep building the best free pro animation tool for Android artists.**


## CI/CD & Agent Ramp-Up (this build)

- Full GitHub Actions workflows restored/updated from template: `generate-keystore.yml` (one-time secret setup using GH_PAT), `android-ci.yml` (debug builds + tests on push/PR), `release.yml` (signed releases on tags or manual dispatch).
- New `scripts/socreate-cli.sh` (Ramp-Up Edition): `zip`, `status`, `update-readme`, `prepare-push`, `full-ramp`, `agent-sync`, `build-debug`, `bump-version`.
- README updated with "Working with the Agent" guidance.
- .github/workflows/ now present for immediate use after adding GH_PAT secret.
- Ready for rapid iteration: agent generates changes → you use CLI to prepare → push → CI runs.

Add `GH_PAT` secret (full repo permissions) in your repo Settings → Secrets. Run the Generate Keystores workflow once.
