// Body animation system — loads pre-retargeted M2M animations and provides
// speaking-phase rotation, idle cycling, gesture tag parsing, and crossfade playback.
// Designed to layer on top of TalkingHead's facial expression system.
import * as THREE from "three";
import { GLTFLoader } from "three/addons/loaders/GLTFLoader.js";

const ANIMATION_GLB = "animations/m2m-animations.glb";
const CROSSFADE_DURATION = 0.4;
const IDLE_MIN_INTERVAL = 5000;
const IDLE_MAX_INTERVAL = 15000;
const SPEAKING_MIN_INTERVAL = 4000;
const SPEAKING_MAX_INTERVAL = 10000;

const SPEAKING_POOL = [
  { name: "idle-talking", weight: 12 },
  { name: "idle-listening", weight: 10 },
  { name: "idle-subtle", weight: 8 },
  { name: "idle-foldarms", weight: 5 },
  { name: "head-nod", weight: 3 },
  { name: "confused", weight: 2 },
  { name: "chest-open", weight: 2 },
  { name: "push", weight: 1 },
  { name: "knock", weight: 1 },
  { name: "yes", weight: 2 },
];

const IDLE_POOL = [
  { name: "idle-a", weight: 10 },
  { name: "idle-subtle", weight: 10 },
  { name: "idle-foldarms", weight: 6 },
  { name: "idle-listening", weight: 6 },
  { name: "idle-talking", weight: 4 },
  { name: "idle-hurt", weight: 2 },
  { name: "idle-shakeoff", weight: 3 },
  { name: "guitar-playing", weight: 2 },
  { name: "drumming", weight: 2 },
  { name: "singing", weight: 2 },
  { name: "meditate", weight: 3 },
  { name: "pray", weight: 2 },
  { name: "confused", weight: 3 },
  { name: "ninjajump-start", weight: 1 },
  { name: "moonwalk", weight: 1 },
  { name: "jumping-jacks", weight: 1 },
  { name: "dance-charleston", weight: 1 },
  { name: "dance-simple", weight: 1 },
  { name: "tightrope-walk", weight: 1 },
];

const HEAD_BONES = new Set([
  "Head", "Neck", "LeftEye", "RightEye",
  "HeadTop_End", "Neck1",
]);

function totalWeight(pool) {
  return pool.reduce((s, e) => s + e.weight, 0);
}

function pickWeighted(pool, total) {
  let r = Math.random() * total;
  for (const entry of pool) {
    r -= entry.weight;
    if (r <= 0) return entry.name;
  }
  return pool[0].name;
}

export class BodyAnimationSystem {
  constructor() {
    this._clips = new Map();
    this._mixer = null;
    this._currentAction = null;
    this._cycleTimer = null;
    this._active = false;
    this._model = null;
    this._speaking = false;
    this._idleTotalWeight = totalWeight(IDLE_POOL);
    this._speakingTotalWeight = totalWeight(SPEAKING_POOL);
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

  playGesture(name) {
    this._stopCycleTimer();
    const played = this.play(name, { loop: false });
    if (!played) this._startCycle();
    return played;
  }

  setSpeaking(speaking) {
    if (this._speaking === speaking) return;
    this._speaking = speaking;
    this._playFromActivePool();
    this._startCycle();
  }

  _playFromActivePool() {
    if (this._speaking) {
      this.play(pickWeighted(SPEAKING_POOL, this._speakingTotalWeight), { loop: true });
    } else {
      this.play(pickWeighted(IDLE_POOL, this._idleTotalWeight), { loop: true });
    }
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
}

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
