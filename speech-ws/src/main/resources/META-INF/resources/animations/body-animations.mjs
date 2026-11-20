import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";

const ANIMATION_GLB = "animations/m2m-animations.glb";
const CROSSFADE_DURATION = 0.4;
const GESTURE_DUCK_DURATION = 0.3;

const CYCLE_INTERVALS = {
  idle:      { min: 5000, max: 15000 },
  listening: { min: 6000, max: 12000 },
  thinking:  { min: 4000, max:  8000 },
  speaking:  { min: 4000, max: 10000 },
};

export const FloorState = Object.freeze({
  IDLE:      'idle',
  LISTENING: 'listening',
  THINKING:  'thinking',
  SPEAKING:  'speaking',
});

const HEAD_BONES = new Set([
  "Head", "Neck", "LeftEye", "RightEye",
  "HeadTop_End", "Neck1",
]);

const EXPRESSION_ALLOW = /^(brow|mouthFrown|mouthSmile|jawOpen|jawForward|noseSneer|cheekPuff|cheekSquint)/;

function sampleCurve(times, values, t) {
  if (times.length === 0) return 0;
  if (t <= times[0]) return values[0];
  if (t >= times[times.length - 1]) return values[values.length - 1];
  for (let i = 1; i < times.length; i++) {
    if (t <= times[i]) {
      const f = (t - times[i - 1]) / (times[i] - times[i - 1]);
      return values[i - 1] + f * (values[i] - values[i - 1]);
    }
  }
  return values[values.length - 1];
}

function resolveSubcategory(taxonomy, path) {
  const [cat, sub] = path.split(".");
  const category = taxonomy[cat];
  return category ? category[sub] || null : null;
}

function pickWeighted(entries) {
  let total = 0;
  for (const e of entries) total += e.weight;
  let r = Math.random() * total;
  for (const e of entries) {
    r -= e.weight;
    if (r <= 0) return e;
  }
  return entries[0];
}

function pickFromPool(taxonomy, poolRefs) {
  const ref = pickWeighted(poolRefs);
  const clips = resolveSubcategory(taxonomy, ref.subcategory);
  if (!clips || clips.length === 0) return null;
  return pickWeighted(clips).clip;
}

export class BodyAnimationSystem {

  constructor(avatarRoot, config, clips) {
    this._config = config;
    this._clips = clips;
    this._mixer = new THREE.AnimationMixer(avatarRoot);

    this._gestureMap = new Map();
    for (const g of config.gestures) {
      this._gestureMap.set(g.tag, g.target);
    }

    this._floorState = FloorState.IDLE;
    this._active = true;

    this._baseAction = null;
    this._baseWeight = 1.0;

    this._gestureAction = null;
    this._gestureWeight = 0.0;
    this._gestureDucking = false;
    this._duckProgress = 0;

    this._cycleTimer = null;
    this._onGestureFinished = null;
    this._expressionCurves = new Map();
    this._activeExpressions = null;
  }

  static async create(avatarRoot, config) {
    const gltf = await new GLTFLoader().loadAsync(ANIMATION_GLB);

    const morphNameMap = new Map();
    gltf.scene.traverse(obj => {
      if (obj.morphTargetDictionary) {
        const reverse = new Map();
        for (const [name, idx] of Object.entries(obj.morphTargetDictionary)) {
          reverse.set(idx, name);
        }
        morphNameMap.set(obj.name, reverse);
      }
    });

    const clips = new Map();
    const expressionCurves = new Map();
    for (const clip of gltf.animations) {
      const morphTracks = [];
      clip.tracks = clip.tracks.filter(track => {
        if (track.name.includes('morphTargetInfluences')) {
          morphTracks.push(track);
          return false;
        }
        const dot = track.name.indexOf(".");
        if (dot === -1) return true;
        return !HEAD_BONES.has(track.name.slice(0, dot));
      });
      clips.set(clip.name, clip);

      if (morphTracks.length > 0) {
        const clipExprs = new Map();
        for (const track of morphTracks) {
          const match = track.name.match(/^(.+)\.morphTargetInfluences\[(\d+|[a-zA-Z_]\w*)]$/);
          if (!match) continue;
          const key = match[2];
          let targetName;
          if (/^\d+$/.test(key)) {
            const reverse = morphNameMap.get(match[1]);
            if (!reverse) continue;
            targetName = reverse.get(parseInt(key));
          } else {
            targetName = key;
          }
          if (!targetName || !EXPRESSION_ALLOW.test(targetName)) continue;
          clipExprs.set(targetName, { times: track.times, values: track.values });
        }
        if (clipExprs.size > 0) expressionCurves.set(clip.name, clipExprs);
      }
    }
    console.log(`[BodyAnim] Loaded ${clips.size} clips, ${expressionCurves.size} with expressions`);
    const system = new BodyAnimationSystem(avatarRoot, config, clips);
    system._expressionCurves = expressionCurves;
    system._playFromPool();
    system._startCycle();
    return system;
  }

  get mixer() { return this._mixer; }
  get floorState() { return this._floorState; }
  get isPlaying() { return this._baseAction !== null && this._baseAction.isRunning(); }

  update(dt) {
    if (!this._active) return;

    if (this._gestureDucking && this._duckProgress < 1) {
      this._duckProgress = Math.min(1, this._duckProgress + dt / GESTURE_DUCK_DURATION);
      this._baseWeight = 1 - this._duckProgress;
      this._gestureWeight = this._duckProgress;
      this._applyWeights();
    } else if (!this._gestureDucking && this._duckProgress > 0) {
      this._duckProgress = Math.max(0, this._duckProgress - dt / GESTURE_DUCK_DURATION);
      this._baseWeight = 1 - this._duckProgress;
      this._gestureWeight = this._duckProgress;
      this._applyWeights();
      if (this._duckProgress === 0 && this._gestureAction) {
        this._gestureAction.stop();
        this._gestureAction = null;
      }
    }

    this._mixer.update(dt);
  }

  setFloorState(state) {
    if (this._floorState === state) return;
    this._floorState = state;
    this._playFromPool();
    this._startCycle();
  }

  triggerGesture(tag) {
    const target = this._gestureMap.get(tag);
    if (!target) {
      console.warn(`[BodyAnim] Unknown gesture tag: ${tag}`);
      return false;
    }

    let clipName;
    if (target.clip) {
      clipName = target.clip;
    } else if (target.subcategory) {
      const clips = resolveSubcategory(this._config.taxonomy, target.subcategory);
      if (!clips || clips.length === 0) return false;
      clipName = pickWeighted(clips).clip;
    } else {
      return false;
    }

    return this._playGesture(clipName);
  }

  strokeMs(tag) {
    const target = this._gestureMap.get(tag);
    if (!target || !target.clip) return 0;
    const clipCfg = this._config.clips[target.clip];
    return clipCfg ? clipCfg.strokeMs || 0 : 0;
  }

  sampleExpressions() {
    if (!this._activeExpressions || !this._gestureAction || this._gestureWeight <= 0) return null;
    const time = this._gestureAction.time;
    const weight = this._gestureWeight;
    const result = {};
    for (const [name, curve] of this._activeExpressions) {
      result[name] = sampleCurve(curve.times, curve.values, time) * weight;
    }
    return result;
  }

  dispose() {
    this._stopCycleTimer();
    this._removeGestureListener();
    if (this._mixer) this._mixer.stopAllAction();
    this._baseAction = null;
    this._gestureAction = null;
    this._active = false;
    this._mixer = null;
    this._clips = null;
  }

  _playFromPool() {
    const poolRefs = this._config.pools[this._floorState];
    if (!poolRefs || poolRefs.length === 0) return;
    const name = pickFromPool(this._config.taxonomy, poolRefs);
    if (name) this._playBase(name);
  }

  _playBase(name) {
    const clip = this._clips.get(name);
    if (!clip) {
      console.warn(`[BodyAnim] Unknown base clip: ${name}`);
      return;
    }

    const action = this._mixer.clipAction(clip);
    action.setLoop(THREE.LoopRepeat, Infinity);

    if (this._baseAction && this._baseAction !== action) {
      action.reset();
      action.setEffectiveWeight(this._baseWeight);
      action.play();
      this._baseAction.crossFadeTo(action, CROSSFADE_DURATION, true);
    } else if (!this._baseAction) {
      action.reset();
      action.setEffectiveWeight(this._baseWeight);
      action.play();
    }
    this._baseAction = action;
  }

  _playGesture(name) {
    const clip = this._clips.get(name);
    if (!clip) {
      console.warn(`[BodyAnim] Unknown gesture clip: ${name}`);
      return false;
    }

    this._removeGestureListener();
    if (this._gestureAction) {
      this._gestureAction.stop();
    }

    const action = this._mixer.clipAction(clip);
    action.setLoop(THREE.LoopOnce, 1);
    action.clampWhenFinished = true;
    action.reset();
    action.setEffectiveWeight(0);
    action.play();

    this._gestureAction = action;
    this._gestureDucking = true;
    this._duckProgress = 0;
    this._activeExpressions = this._expressionCurves.get(name) || null;

    this._onGestureFinished = (e) => {
      if (e.action === action) {
        this._gestureDucking = false;
        this._removeGestureListener();
      }
    };
    this._mixer.addEventListener("finished", this._onGestureFinished);

    return true;
  }

  _removeGestureListener() {
    if (this._onGestureFinished && this._mixer) {
      this._mixer.removeEventListener("finished", this._onGestureFinished);
      this._onGestureFinished = null;
    }
  }

  _applyWeights() {
    if (this._baseAction) this._baseAction.setEffectiveWeight(this._baseWeight);
    if (this._gestureAction) this._gestureAction.setEffectiveWeight(this._gestureWeight);
  }

  _startCycle() {
    this._stopCycleTimer();
    const intervals = CYCLE_INTERVALS[this._floorState] || CYCLE_INTERVALS.idle;
    const delay = intervals.min + Math.random() * (intervals.max - intervals.min);
    this._cycleTimer = setTimeout(() => {
      if (this._active) {
        this._playFromPool();
        this._startCycle();
      }
    }, delay);
  }

  _stopCycleTimer() {
    clearTimeout(this._cycleTimer);
    this._cycleTimer = null;
  }
}
