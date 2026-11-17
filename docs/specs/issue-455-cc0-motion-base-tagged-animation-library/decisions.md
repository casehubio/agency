## D1: Retarget fix approach — bind correction replaces swing-twist

**Choice:** Replace M2M's swing-twist bake path with per-bone L/R bind correction formula
**Alternatives:**
- Add clavicle chains + fix twist axes — patches Mixamo specifically, other rigs with different bone rolls still break
- Post-bake bind correction on top of swing-twist — double-corrects, architecturally messy
**Rationale:** The bind correction formula (`L = Rt × inv(Wt) × Ws × inv(Rs)`, `R = inv(Ws) × Wt`) handles arbitrary rest-pose differences without swing-twist decomposition. Already proven in casehub-agency's retarget.mjs. Simpler implementation (per-track, no frame-by-frame bake), handles all target skeletons.
**Trade-offs:** Swing-twist can produce more "natural" motion for very different skeleton proportions (it matches directions). Bind correction assumes bone topology matches — which is true for humanoid rigs sharing the same chain structure.
**Sources:** retarget.mjs (casehub-agency), AnimationRetargetService.ts, Retargeter.ts, HumanClipRetargeter.ts (mesh2motion-app)
**Exploration:** quick
**Status:** captured
