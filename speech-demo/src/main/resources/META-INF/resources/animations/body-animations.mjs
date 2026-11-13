// Body animation system — loads pre-retargeted M2M animations and provides
// speaking-phase rotation, idle cycling, gesture tag parsing, and crossfade playback.
// Designed to layer on top of TalkingHead's facial expression system.
//
// Taxonomy: category → subcategory → clips (with weights).
// Pools (speaking, idle) reference subcategories. Gesture tags resolve to
// subcategories or specific clips. Adding variants is just data.
import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";

const ANIMATION_GLB = "animations/m2m-animations.glb";
const CROSSFADE_DURATION = 0.4;
const IDLE_MIN_INTERVAL = 5000;
const IDLE_MAX_INTERVAL = 15000;
const SPEAKING_MIN_INTERVAL = 4000;
const SPEAKING_MAX_INTERVAL = 10000;

// ── Taxonomy: category → subcategory → clips ──────────────────────────
// Each clip: { name, weight }. Weight is relative within the subcategory.
// name must match a clip name in the GLB.
const TAXONOMY = {
  conversational: {
    stance: [
      { name: "idle-subtle", weight: 10 },
      { name: "idle-foldarms", weight: 6 },
      { name: "idle-listening", weight: 8 },
    ],
    "hand-gesture": [
      { name: "idle-talking", weight: 12 },
    ],
    agreement: [
      { name: "head-nod", weight: 8 },
      { name: "yes", weight: 6 },
      { name: "clapping", weight: 3 },
    ],
    disagreement: [
      { name: "reject", weight: 8 },
      { name: "push", weight: 5 },
    ],
    emphasis: [
      { name: "chest-open", weight: 6 },
      { name: "knock", weight: 4 },
      { name: "power-burst", weight: 2 },
    ],
    uncertainty: [
      { name: "confused", weight: 8 },
    ],
  },
  idle: {
    relaxed: [
      { name: "idle-a", weight: 10 },
      { name: "idle-subtle", weight: 10 },
      { name: "idle-foldarms", weight: 6 },
      { name: "idle-listening", weight: 6 },
    ],
    restless: [
      { name: "idle-hurt", weight: 3 },
      { name: "idle-shakeoff", weight: 4 },
      { name: "jumping-jacks", weight: 2 },
    ],
    personality: [
      { name: "guitar-playing", weight: 3 },
      { name: "drumming", weight: 3 },
      { name: "singing", weight: 3 },
      { name: "meditate", weight: 4 },
      { name: "pray", weight: 3 },
      { name: "moonwalk", weight: 1 },
      { name: "tightrope-walk", weight: 1 },
      { name: "ninjajump-start", weight: 1 },
    ],
    dance: [
      { name: "dance-charleston", weight: 3 },
      { name: "dance-arm-pump", weight: 3 },
      { name: "dance-body-roll", weight: 3 },
      { name: "dance-reach-hip", weight: 2 },
      { name: "dance-simple", weight: 4 },
    ],
  },
  reactive: {
    positive: [
      { name: "victory", weight: 6 },
      { name: "victory-fist-pump", weight: 5 },
      { name: "dab", weight: 3 },
    ],
    negative: [
      { name: "angry", weight: 6 },
      { name: "shiver", weight: 4 },
    ],
    surprise: [
      { name: "dizzy", weight: 6 },
      { name: "power-up", weight: 4 },
    ],
    greeting: [
      { name: "greeting", weight: 10 },
    ],
  },
  dramatic: {
    joke: [
      { name: "death-a", weight: 3 },
      { name: "death-b", weight: 3 },
      { name: "death-c", weight: 3 },
      { name: "zombie-idle", weight: 4 },
      { name: "drunk-walk", weight: 4 },
      { name: "backflip", weight: 2 },
    ],
    combat: [
      { name: "hit-chest", weight: 4 },
      { name: "hit-head", weight: 4 },
      { name: "hit-knockback", weight: 4 },
    ],
    misc: [
      { name: "interact", weight: 5 },
    ],
  },
  seated: {
    idle: [
      { name: "sitting-idle", weight: 8 },
    ],
    talking: [
      { name: "sitting-talking", weight: 8 },
    ],
  },
};

// ── Pool definitions: which subcategories to draw from ────────────────
// { path: "category.subcategory", weight } — weight is for subcategory selection
const SPEAKING_POOL_REFS = [
  { path: "conversational.hand-gesture", weight: 14 },
  { path: "conversational.stance", weight: 12 },
  { path: "conversational.agreement", weight: 4 },
  { path: "conversational.uncertainty", weight: 3 },
  { path: "conversational.emphasis", weight: 3 },
];

const IDLE_POOL_REFS = [
  { path: "idle.relaxed", weight: 14 },
  { path: "idle.personality", weight: 6 },
  { path: "idle.restless", weight: 4 },
  { path: "idle.dance", weight: 2 },
  { path: "conversational.stance", weight: 4 },
];

// ── Gesture tag → taxonomy resolution ─────────────────────────────────
// Maps gesture tag names to either a specific clip or a subcategory path.
// Subcategory paths pick randomly within that subcategory.
const GESTURE_MAP = {
  // Specific clips
  angry: { clip: "angry" },
  bow: { clip: "head-nod" },
  clapping: { clip: "clapping" },
  confused: { clip: "confused" },
  dab: { clip: "dab" },
  greeting: { clip: "greeting" },
  nod: { clip: "head-nod" },
  reject: { clip: "reject" },
  shiver: { clip: "shiver" },
  victory: { clip: "victory" },
  fistpump: { clip: "victory-fist-pump" },
  yes: { clip: "yes" },
  dizzy: { clip: "dizzy" },
  powerup: { clip: "power-up" },
  powerburst: { clip: "power-burst" },
  chestopen: { clip: "chest-open" },
  push: { clip: "push" },
  knock: { clip: "knock" },
  backflip: { clip: "backflip" },
  zombie: { clip: "zombie-idle" },
  drunk: { clip: "drunk-walk" },
  interact: { clip: "interact" },
  death: { clip: "death-a" },
  hit: { clip: "hit-chest" },

  // Subcategory picks (random from variants)
  agreement: { subcategory: "conversational.agreement" },
  disagreement: { subcategory: "conversational.disagreement" },
  emphasis: { subcategory: "conversational.emphasis" },
  surprise: { subcategory: "reactive.surprise" },
  celebrate: { subcategory: "reactive.positive" },
  dance: { subcategory: "idle.dance" },

  // Mood-driven fallbacks
  happy: { subcategory: "reactive.positive" },
  sad: { clip: "reject" },
  thinking: { clip: "idle-talking" },
  explaining: { subcategory: "conversational.hand-gesture" },
};

// ── Keyword fallback (responses without gesture tags) ─────────────────
const KEYWORD_GESTURES = [
  { keywords: ["hello", "hi ", "hey ", "welcome"], gesture: "greeting" },
  { keywords: ["yes", "exactly", "agree", "correct", "absolutely"], gesture: "agreement" },
  { keywords: ["wow", "amazing", "incredible"], gesture: "surprise" },
  { keywords: ["great", "wonderful", "fantastic", "haha"], gesture: "celebrate" },
  { keywords: ["not sure", "unclear", "hard to say"], gesture: "confused" },
  { keywords: ["sorry", "unfortunately", "sadly"], gesture: "sad" },
];

// ── Internals ─────────────────────────────────────────────────────────

const HEAD_BONES = new Set([
  "Head", "Neck", "LeftEye", "RightEye",
  "HeadTop_End", "Neck1",
]);

function resolveSubcategory(path) {
  const [cat, sub] = path.split(".");
  const category = TAXONOMY[cat];
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

function pickFromPool(poolRefs) {
  const ref = pickWeighted(poolRefs);
  const clips = resolveSubcategory(ref.path);
  if (!clips || clips.length === 0) return null;
  return pickWeighted(clips).name;
}

// ── Public API ────────────────────────────────────────────────────────

export class BodyAnimationSystem {
  constructor() {
    this._clips = new Map();
    this._mixer = null;
    this._currentAction = null;
    this._cycleTimer = null;
    this._active = false;
    this._model = null;
    this._speaking = false;
  }

  async load(avatarRoot) {
    this._model = avatarRoot;
    const gltf = await new GLTFLoader().loadAsync(ANIMATION_GLB);
    for (const clip of gltf.animations) {
      this._maskHeadBones(clip);
      this._clips.set(clip.name, clip);
    }
    this._mixer = new THREE.AnimationMixer(avatarRoot);
    this._active = true;
    console.log(`[BodyAnim] Loaded ${this._clips.size} clips`);
  }

  _maskHeadBones(clip) {
    clip.tracks = clip.tracks.filter(track => {
      const dot = track.name.indexOf(".");
      if (dot === -1) return true;
      return !HEAD_BONES.has(track.name.slice(0, dot));
    });
  }

  update(dt) {
    if (this._mixer) this._mixer.update(dt);
  }

  play(name, options = {}) {
    if (!this._mixer || !this._active) return false;
    const clip = this._clips.get(name);
    if (!clip) {
      console.warn(`[BodyAnim] Unknown clip: ${name}`);
      return false;
    }

    const action = this._mixer.clipAction(clip);
    const loop = options.loop !== false;
    action.setLoop(loop ? THREE.LoopRepeat : THREE.LoopOnce, loop ? Infinity : 1);
    action.clampWhenFinished = !loop;

    if (this._currentAction && this._currentAction !== action) {
      action.reset();
      action.play();
      this._currentAction.crossFadeTo(action, options.fadeDuration || CROSSFADE_DURATION, true);
    } else {
      action.reset();
      action.play();
    }

    this._currentAction = action;

    if (!loop) {
      const onFinished = (e) => {
        if (e.action === action) {
          this._mixer.removeEventListener("finished", onFinished);
          this._playFromActivePool();
        }
      };
      this._mixer.addEventListener("finished", onFinished);
    }

    return true;
  }

  playGesture(gestureName) {
    this._stopCycleTimer();
    const mapping = GESTURE_MAP[gestureName];
    let clipName;
    if (mapping) {
      clipName = mapping.clip || this._pickFromSubcategory(mapping.subcategory);
    } else {
      clipName = gestureName;
    }
    const played = this.play(clipName, { loop: false });
    if (!played) this._startCycle();
    return played;
  }

  _pickFromSubcategory(path) {
    if (!path) return null;
    const clips = resolveSubcategory(path);
    if (!clips || clips.length === 0) return null;
    return pickWeighted(clips).name;
  }

  setSpeaking(speaking) {
    if (this._speaking === speaking) return;
    this._speaking = speaking;
    this._playFromActivePool();
    this._startCycle();
  }

  _playFromActivePool() {
    const pool = this._speaking ? SPEAKING_POOL_REFS : IDLE_POOL_REFS;
    const name = pickFromPool(pool);
    if (name) this.play(name, { loop: true });
  }

  startIdleCycle() {
    this._speaking = false;
    this._playFromActivePool();
    this._startCycle();
  }

  startSpeakingCycle() {
    this._speaking = true;
    this._playFromActivePool();
    this._startCycle();
  }

  _startCycle() {
    this._stopCycleTimer();
    const [min, max] = this._speaking
      ? [SPEAKING_MIN_INTERVAL, SPEAKING_MAX_INTERVAL]
      : [IDLE_MIN_INTERVAL, IDLE_MAX_INTERVAL];
    const delay = min + Math.random() * (max - min);
    this._cycleTimer = setTimeout(() => {
      if (this._active) {
        this._playFromActivePool();
        this._startCycle();
      }
    }, delay);
  }

  _stopCycleTimer() {
    clearTimeout(this._cycleTimer);
    this._cycleTimer = null;
  }

  stop() {
    this._stopCycleTimer();
    if (this._mixer) this._mixer.stopAllAction();
    this._currentAction = null;
  }

  dispose() {
    this.stop();
    this._active = false;
    this._mixer = null;
    this._clips.clear();
  }

  get clipNames() {
    return Array.from(this._clips.keys());
  }

  get isPlaying() {
    return this._currentAction !== null && this._currentAction.isRunning();
  }

  get isSpeaking() {
    return this._speaking;
  }

  get taxonomy() {
    return TAXONOMY;
  }
}

// ── Tag parsing ───────────────────────────────────────────────────────

const GESTURE_TAG_RE = /\[gesture:([a-z0-9_-]+)\]/gi;

export function parseGestureTags(text) {
  const tags = [];
  let match;
  while ((match = GESTURE_TAG_RE.exec(text)) !== null) {
    tags.push(match[1]);
  }
  const cleaned = text.replace(GESTURE_TAG_RE, "").trim();
  return { tags, cleaned };
}

export function classifyResponse(text) {
  const lower = text.toLowerCase();
  for (const entry of KEYWORD_GESTURES) {
    for (const kw of entry.keywords) {
      if (lower.includes(kw)) return entry.gesture;
    }
  }
  return null;
}
