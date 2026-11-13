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
      { name: "hands-clasped-front", weight: 8 },
      { name: "hands-behind-back", weight: 6 },
      { name: "hands-on-hips", weight: 5 },
    ],
    "hand-gesture": [
      { name: "idle-talking", weight: 12 },
      { name: "hands-expand", weight: 8 },
      { name: "hands-converge", weight: 7 },
    ],
    agreement: [
      { name: "head-nod", weight: 8 },
      { name: "clapping", weight: 3 },
      { name: "thumbs-up", weight: 8 },
    ],
    disagreement: [
      { name: "reject", weight: 8 },
      { name: "head-shake", weight: 7 },
      { name: "head-shake-casual", weight: 5 },
      { name: "finger-wag", weight: 4 },
    ],
    emphasis: [
      { name: "finger-pointing-up", weight: 5 },
      { name: "finger-pointing-forward", weight: 5 },
      { name: "one-hand-raised", weight: 5 },
      { name: "knock", weight: 4 },
    ],
    uncertainty: [
      { name: "confused", weight: 8 },
      { name: "shrug", weight: 7 },
      { name: "shrug-casual", weight: 5 },
      { name: "dizzy", weight: 4 },
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
    "self-amusing": [
      { name: "guitar-playing", weight: 3 },
      { name: "drumming", weight: 3 },
      { name: "singing", weight: 3 },
      { name: "meditate", weight: 4 },
      { name: "pray", weight: 3 },
      { name: "moonwalk", weight: 1 },
      { name: "tightrope-walk", weight: 1 },
      { name: "ninjajump", weight: 2 },
      { name: "backflip", weight: 1 },
      { name: "dab", weight: 2 },
    ],
    dance: [
      { name: "dance-charleston", weight: 3 },
      { name: "dance-arm-pump", weight: 3 },
      { name: "dance-body-roll", weight: 3 },
      { name: "dance-reach-hip", weight: 2 },
      { name: "dance-simple", weight: 4 },
    ],
    attention: [
      { name: "knock", weight: 8 },
      { name: "interact", weight: 6 },
    ],
  },
  reactive: {
    positive: [
      { name: "victory", weight: 6 },
      { name: "victory-fist-pump", weight: 5 },
      { name: "dab", weight: 3 },
      { name: "arms-wide-open", weight: 5 },
      { name: "power-burst", weight: 3 },
      { name: "clapping", weight: 4 },
      { name: "dance-simple", weight: 2 },
      { name: "dance-arm-pump", weight: 2 },
    ],
    negative: [
      { name: "angry", weight: 6 },
      { name: "self-hug", weight: 5 },
      { name: "facepalm", weight: 4 },
      { name: "double-facepalm", weight: 3 },
    ],
    greeting: [
      { name: "greeting", weight: 10 },
    ],
    distress: [
      { name: "hands-over-mouth", weight: 6 },
      { name: "hand-over-mouth", weight: 5 },
      { name: "hands-on-head", weight: 5 },
      { name: "shiver", weight: 4 },
    ],
  },
  dramatic: {
    death: [
      { name: "death-a", weight: 3 },
      { name: "death-b", weight: 3 },
      { name: "death-c", weight: 3 },
    ],
    combat: [
      { name: "hit-knockback", weight: 4 },
    ],
    theatrical: [
      { name: "zombie-idle", weight: 4 },
      { name: "drunk-walk", weight: 4 },
      { name: "power-up", weight: 3 },
      { name: "chest-open", weight: 3 },
      { name: "push", weight: 3 },
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
  { path: "conversational.hand-gesture", weight: 16 },
  { path: "conversational.stance", weight: 14 },
  { path: "conversational.agreement", weight: 4 },
  { path: "conversational.uncertainty", weight: 4 },
  { path: "conversational.emphasis", weight: 4 },
  { path: "idle.dance", weight: 1 },
];

const IDLE_POOL_REFS = [
  { path: "idle.relaxed", weight: 14 },
  { path: "idle.self-amusing", weight: 4 },
  { path: "idle.restless", weight: 4 },
  { path: "idle.dance", weight: 2 },
  { path: "conversational.stance", weight: 4 },
];

// ── Gesture tag → taxonomy resolution ─────────────────────────────────
// Maps gesture tag names to either a specific clip or a subcategory path.
// Subcategory paths pick randomly within that subcategory.
const GESTURE_MAP = {
  // Direct clip mappings — conversational
  nod: { clip: "head-nod" },
  bow: { clip: "head-nod" },
  no: { clip: "head-shake" },
  naughty: { clip: "finger-wag" },
  shrug: { clip: "shrug" },
  wait: { clip: "one-hand-raised" },
  aha: { clip: "finger-pointing-up" },
  point: { clip: "finger-pointing-forward" },
  thumbsup: { clip: "thumbs-up" },
  yes: { clip: "thumbs-up" },
  reject: { clip: "reject" },
  knock: { clip: "knock" },
  thinking: { clip: "hands-converge" },
  confused: { clip: "confused" },
  dizzy: { clip: "dizzy" },
  clapping: { clip: "clapping" },

  // Direct clip mappings — reactive
  angry: { clip: "angry" },
  greeting: { clip: "greeting" },
  victory: { clip: "victory" },
  fistpump: { clip: "victory-fist-pump" },
  dab: { clip: "dab" },
  sad: { clip: "self-hug" },
  joyful: { clip: "arms-wide-open" },
  facepalm: { clip: "facepalm" },
  doublefacepalm: { clip: "double-facepalm" },
  shocked: { clip: "hands-on-head" },
  overwhelmed: { clip: "hands-on-head" },
  horror: { clip: "hands-over-mouth" },
  oops: { clip: "hand-over-mouth" },
  nervous: { clip: "hands-clasped-front" },
  shiver: { clip: "shiver" },
  careful: { clip: "tightrope-walk" },
  sneaky: { clip: "ninjajump" },
  rewind: { clip: "moonwalk" },
  meditate: { clip: "meditate" },
  pray: { clip: "pray" },
  guitar: { clip: "guitar-playing" },
  drumroll: { clip: "drumming" },
  sing: { clip: "singing" },

  // Direct clip mappings — dramatic
  death: { clip: "death-a" },
  hit: { clip: "hit-knockback" },
  zombie: { clip: "zombie-idle" },
  drunk: { clip: "drunk-walk" },
  backflip: { clip: "backflip" },
  powerup: { clip: "power-up" },

  // Subcategory picks (random from pool)
  agreement: { subcategory: "conversational.agreement" },
  disagreement: { subcategory: "conversational.disagreement" },
  emphasis: { subcategory: "conversational.emphasis" },
  uncertainty: { subcategory: "conversational.uncertainty" },
  celebrate: { subcategory: "reactive.positive" },
  surprise: { subcategory: "reactive.positive" },
  distress: { subcategory: "reactive.distress" },
  dance: { subcategory: "idle.dance" },
  attention: { subcategory: "idle.attention" },

  // Mood-driven fallbacks
  happy: { subcategory: "reactive.positive" },
  explaining: { subcategory: "conversational.hand-gesture" },
};

// ── Keyword fallback (responses without gesture tags) ─────────────────
const KEYWORD_GESTURES = [
  { keywords: ["hello", "hi ", "hey ", "welcome"], gesture: "greeting" },
  { keywords: ["yes", "exactly", "agree", "correct", "absolutely"], gesture: "agreement" },
  { keywords: ["no ", "don't", "won't", "can't", "shouldn't"], gesture: "no" },
  { keywords: ["wow", "amazing", "incredible"], gesture: "surprise" },
  { keywords: ["great", "wonderful", "fantastic", "haha"], gesture: "celebrate" },
  { keywords: ["not sure", "unclear", "hard to say", "hmm"], gesture: "shrug" },
  { keywords: ["sorry", "unfortunately", "sadly"], gesture: "sad" },
  { keywords: ["wait", "hold on", "one moment"], gesture: "wait" },
  { keywords: ["oh no", "terrible", "horrible"], gesture: "distress" },
  { keywords: ["think about", "consider", "let me think"], gesture: "thinking" },
  { keywords: ["let's go", "let's do", "come on", "ready", "energi"], gesture: "dance" },
  { keywords: ["careful", "risky", "tricky", "fine line", "treading"], gesture: "careful" },
  { keywords: ["go back", "rewind", "step back", "backtrack", "reverse"], gesture: "rewind" },
  { keywords: ["sneak", "stealth", "ninja", "under the radar", "quietly"], gesture: "sneaky" },
  { keywords: ["calm down", "breathe", "relax", "zen", "peace"], gesture: "meditate" },
  { keywords: ["hope", "pray", "fingers crossed", "please"], gesture: "pray" },
  { keywords: ["drum roll", "drumroll", "suspense", "announcement"], gesture: "drumroll" },
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
    this._finishedListener = null;
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

    if (this._finishedListener) {
      this._mixer.removeEventListener("finished", this._finishedListener);
      this._finishedListener = null;
    }

    if (!loop) {
      const onFinished = (e) => {
        if (e.action === action) {
          this._mixer.removeEventListener("finished", onFinished);
          this._finishedListener = null;
          this._playFromActivePool();
        }
      };
      this._finishedListener = onFinished;
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
