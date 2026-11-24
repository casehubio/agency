import { TalkingHead } from "talkinghead";
import { BodyAnimationSystem, FloorState } from "./animations/body-animations.mjs";

export { FloorState };

export class AvatarEngine {

  constructor(container, opts = {}) {
    this._container = container;
    this._avatarModel = opts.avatarModel || 'avatar.glb';
    this._cameraView = opts.cameraView || 'upper';
    this._ws = null;
    this._audioCtx = null;
    this._head = null;
    this._bodyAnim = null;
    this._activeTurnSeq = -1;
    this._pendingSegments = new Map();
    this._turnAudioPlayed = new Set();
    this._turnCuesAttached = new Set();
    this._turnComplete = false;
    this._turnSafetyTimer = null;
    this._safetyTimeoutMs = opts.safetyTimeoutMs || 10000;
    this._baselineMood = 'neutral';
    this._turnMood = null;
    this._turnMoodTimer = null;
    this._turnMoodDecayMs = opts.turnMoodDecayMs || 3000;
    this._playQueue = [];
    this._isPlaying = false;
    this._playbackRate = opts.playbackRate || 1.0;
    this._onMessage = opts.onMessage || null;
    this._onStatus = opts.onStatus || null;
    this._onConnected = opts.onConnected || null;
    this._onDisconnected = opts.onDisconnected || null;
  }

  connect() {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    this._ws = new WebSocket(proto + '//' + location.host + '/ws/avatar');
    this._ws.onopen = () => {
      this._emit('status', 'Connected');
      this._emit('connected');
    };
    this._ws.onmessage = (evt) => {
      if (typeof evt.data === 'string') {
        this._handleTextMessage(JSON.parse(evt.data));
      } else {
        this._handleAudioFrame(evt.data);
      }
    };
    this._ws.onclose = () => {
      this._emit('status', 'Disconnected — reconnecting...');
      this._emit('disconnected');
      setTimeout(() => this.connect(), 2000);
    };
    this._ws.onerror = () => this._emit('status', 'Connection error');
  }

  sendText(text, opts = {}) {
    if (!text || !this._ws || this._ws.readyState !== WebSocket.OPEN) return;
    this._ws.send(JSON.stringify({
      type: 'text', text,
      llmModel: opts.llmModel || '',
      ttsModel: opts.ttsModel || ''
    }));
    this._enterFloorState(FloorState.THINKING);
  }

  startRecording(sampleRate, opts = {}) {
    this._ws.send(JSON.stringify({
      type: 'start', sampleRate,
      llmModel: opts.llmModel || '',
      ttsModel: opts.ttsModel || ''
    }));
    this._enterFloorState(FloorState.LISTENING);
  }

  stopRecording() {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._ws.send(JSON.stringify({ type: 'stop' }));
    }
    this._enterFloorState(FloorState.THINKING);
  }

  sendBinary(buffer) {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._ws.send(buffer);
    }
  }

  sendJson(obj) {
    if (this._ws && this._ws.readyState === WebSocket.OPEN) {
      this._ws.send(JSON.stringify(obj));
    }
  }

  async initAvatar() {
    if (this._head) return;
    this._head = new TalkingHead(this._container, {
      ttsEndpoint: null,
      lipsyncModules: ['en'], lipsyncLang: 'en',
      cameraView: this._cameraView,
      cameraRotateEnable: true, cameraZoomEnable: true, cameraPanEnable: true,
      avatarIdleEyeContact: 0.4, avatarIdleHeadMove: 0.6,
      avatarSpeakingEyeContact: 0.7, avatarSpeakingHeadMove: 0.7,
      modelMovementFactor: 0.8
    });
    await this._head.showAvatar({
      url: './' + this._avatarModel, body: 'F',
      avatarMood: 'neutral', lipsyncLang: 'en'
    });
    this._head.lipsyncLang = 'en';
    this._head.setMood('neutral');
    await this._initBodyAnimations();
  }

  async switchAvatar(model) {
    if (!this._head) return;
    await this._head.showAvatar({
      url: './' + model, body: 'F',
      avatarMood: 'neutral', lipsyncLang: 'en'
    });
  }

  setView(view) {
    this._cameraView = view;
    if (!this._head) return;
    this._head.setView(view);
    const presets = {
      upper:  { cam: { x: -0.007, y: 0.801, z: 2.179 }, tgt: { x: -0.007, y: 0.801, z: 0 } },
      full:   { cam: { x: -0.673, y: 0.722, z: 6.542 }, tgt: { x: 0.046, y: 0.485, z: 0.013 } },
      head:   { cam: { x: -0.216, y: 0.911, z: 1.663 }, tgt: { x: 0.011, y: 0.827, z: -0.018 } }
    };
    const p = presets[view];
    if (p && this._head.cameraEnd && this._head.controlsEnd) {
      const h = this._head.avatarHeight || 1.6;
      let scale = 1.0;
      if (view === 'full') {
        const refAspect = 0.899;
        const curAspect = this._container.clientWidth / this._container.clientHeight;
        scale = refAspect / Math.max(curAspect, 0.5);
      }
      this._head.cameraEnd.set(p.cam.x * h * scale, p.cam.y * h, p.cam.z * h * scale);
      this._head.controlsEnd.set(p.tgt.x * h, p.tgt.y * h, p.tgt.z * h);
    }
  }

  resetCamera() {
    this.setView(this._cameraView);
  }

  triggerGesture(name) {
    if (this._bodyAnim) this._bodyAnim.triggerGesture(name);
  }

  get isConnected() {
    return this._ws && this._ws.readyState === WebSocket.OPEN;
  }

  set playbackRate(rate) { this._playbackRate = rate; }
  get playbackRate() { return this._playbackRate; }

  _emit(event, data) {
    const handler = this['_on' + event.charAt(0).toUpperCase() + event.slice(1)];
    if (handler) handler(data);
  }

  _applyMood() {
    const effective = this._turnMood || this._baselineMood;
    if (this._head) this._head.setMood(effective);
  }

  _setTurnMood(mood) {
    clearTimeout(this._turnMoodTimer);
    this._turnMood = mood;
    this._applyMood();
    this._turnMoodTimer = setTimeout(() => {
      this._turnMood = null; this._applyMood();
    }, this._turnMoodDecayMs);
  }

  _enterFloorState(state) {
    if (this._bodyAnim) this._bodyAnim.setFloorState(state);
  }

  _resetTurnState() {
    this._pendingSegments.clear();
    this._turnAudioPlayed.clear();
    this._turnCuesAttached.clear();
    this._turnComplete = false;
    clearTimeout(this._turnSafetyTimer);
    this._turnSafetyTimer = null;
  }

  _checkTurnFinished() {
    if (!this._turnComplete) return;
    for (const [, seg] of this._pendingSegments) {
      if (seg.hasAudio && !this._turnAudioPlayed.has(seg.index)) return;
    }
    this._enterFloorState(FloorState.IDLE);
    this.sendJson({ type: 'playbackComplete', turnSeq: this._activeTurnSeq });
    this._resetTurnState();
  }

  _handleTextMessage(msg) {
    switch (msg.type) {
      case 'transcript':
        this._emit('message', { text: msg.text, role: 'user' });
        break;
      case 'segment':
        this._handleSegment(msg);
        break;
      case 'turnComplete':
        this._handleTurnComplete(msg);
        break;
      case 'gesture':
        if (this._bodyAnim) this._bodyAnim.triggerGesture(msg.tag);
        break;
      case 'mood':
        this._baselineMood = msg.state;
        this._applyMood();
        break;
      case 'error':
        this._emit('message', { text: 'Error: ' + msg.message, role: 'system' });
        break;
      case 'partial':
        this._emit('status', 'Hearing: ' + msg.text);
        break;
    }
  }

  _handleSegment(msg) {
    if (msg.turnSeq !== this._activeTurnSeq) {
      this._resetTurnState();
      this._activeTurnSeq = msg.turnSeq;
    }
    const key = `${msg.turnSeq}:${msg.index}`;
    this._pendingSegments.set(key, {
      index: msg.index, text: msg.text,
      hasAudio: msg.hasAudio,
      visemes: msg.visemes || [], cues: msg.cues || []
    });
    if (msg.text) this._emit('message', { text: msg.text, role: 'avatar' });
    if (!this._head || !msg.hasAudio) {
      for (const cue of (msg.cues || [])) {
        if (cue.type === 'gesture' && this._bodyAnim) this._bodyAnim.triggerGesture(cue.tag);
        if (cue.type === 'mood') this._setTurnMood(cue.mood);
      }
    }
    if (!msg.hasAudio) {
      this._turnAudioPlayed.add(msg.index);
      this._checkTurnFinished();
    }
  }

  _handleTurnComplete(msg) {
    this._turnComplete = true;
    if (msg.segmentCount === 0) {
      this.sendJson({ type: 'playbackComplete', turnSeq: msg.turnSeq });
      this._enterFloorState(FloorState.IDLE);
      this._resetTurnState();
    } else {
      this._checkTurnFinished();
      clearTimeout(this._turnSafetyTimer);
      this._turnSafetyTimer = setTimeout(() => {
        this._enterFloorState(FloorState.IDLE);
        this.sendJson({ type: 'playbackComplete', turnSeq: this._activeTurnSeq });
        this._resetTurnState();
      }, this._safetyTimeoutMs);
    }
  }

  async _handleAudioFrame(blob) {
    try {
      const arrayBuf = await blob.arrayBuffer();
      const view = new DataView(arrayBuf);
      const header = {
        version: view.getUint8(0),
        isFinal: view.getUint8(1) !== 0,
        segmentIndex: view.getUint16(2, true),
        turnSeq: view.getUint32(4, true),
      };
      const audioBytes = arrayBuf.slice(8);
      if (!this._audioCtx) this._audioCtx = new AudioContext();
      const audioBuf = await this._audioCtx.decodeAudioData(audioBytes);

      if (this._head) {
        this._handleAudioWithHead(header, audioBuf);
      } else {
        this._handleAudioWithoutHead(header, audioBuf);
      }
    } catch (e) {
      console.error('[AvatarEngine] Audio frame error:', e);
    }
  }

  _handleAudioWithHead(header, audioBuf) {
    if (header.turnSeq === this._activeTurnSeq) this._enterFloorState(FloorState.SPEAKING);
    const key = `${header.turnSeq}:${header.segmentIndex}`;
    const seg = this._pendingSegments.get(key);
    const r = { audio: audioBuf };
    const firstChunk = !this._turnCuesAttached.has(key);
    if (firstChunk && seg && seg.visemes && seg.visemes.length > 0) {
      this._turnCuesAttached.add(key);
      r.words = [seg.text || '']; r.wtimes = [0]; r.wdurations = [audioBuf.duration * 1000];
      r.visemes = []; r.vtimes = []; r.vdurations = [];
      for (const v of seg.visemes) {
        r.visemes.push(v.viseme); r.vtimes.push(v.startMs);
        r.vdurations.push(v.endMs - v.startMs);
      }
    }
    const markers = []; const mtimes = [];
    for (const cue of (firstChunk ? (seg?.cues || []) : [])) {
      if (cue.type === 'gesture' && this._bodyAnim) {
        const tag = cue.tag;
        const stroke = this._bodyAnim.strokeMs(tag);
        const triggerMs = Math.max(0, (cue.offsetMs || 0) - stroke - 150);
        markers.push(() => this._bodyAnim.triggerGesture(tag));
        mtimes.push(triggerMs);
      }
      if (cue.type === 'mood') {
        const mood = cue.mood;
        markers.push(() => this._setTurnMood(mood));
        mtimes.push(cue.offsetMs || 0);
      }
    }
    if (header.isFinal) {
      const segIdx = header.segmentIndex;
      markers.push(() => { this._turnAudioPlayed.add(segIdx); this._checkTurnFinished(); });
      mtimes.push(audioBuf.duration * 1000);
    }
    if (markers.length > 0) { r.markers = markers; r.mtimes = mtimes; }
    this._head.speakAudio(r, { isRaw: true });
  }

  _handleAudioWithoutHead(header, audioBuf) {
    if (header.turnSeq === this._activeTurnSeq && !this._isPlaying && this._playQueue.length === 0) {
      this._enterFloorState(FloorState.SPEAKING);
    }
    this._playQueue.push({ audio: audioBuf, turnSeq: header.turnSeq,
      segmentIndex: header.segmentIndex, isFinal: header.isFinal });
    if (!this._isPlaying) this._playNextQueued();
  }

  _playNextQueued() {
    if (this._playQueue.length === 0) { this._isPlaying = false; this._emit('status', 'Ready'); return; }
    this._isPlaying = true;
    const item = this._playQueue.shift();
    if (!this._audioCtx) this._audioCtx = new AudioContext();
    const source = this._audioCtx.createBufferSource();
    source.buffer = item.audio;
    source.playbackRate.value = this._playbackRate;
    source.connect(this._audioCtx.destination);
    source.start();
    this._emit('status', 'Speaking... (' + this._playQueue.length + ' queued)');
    source.onended = () => {
      if (item.isFinal) {
        this._turnAudioPlayed.add(item.segmentIndex);
        this._checkTurnFinished();
      }
      this._playNextQueued();
    };
  }

  async _initBodyAnimations() {
    try {
      if (this._bodyAnim) {
        if (this._head) this._head.unregisterMorphLayer('gestureExpressions');
        this._bodyAnim.dispose(); this._bodyAnim = null;
      }
      const avatarModel = this._head.armature;
      if (!avatarModel) return;
      const configResp = await fetch('animations/avatar-performance.json');
      const config = await configResp.json();
      this._bodyAnim = await BodyAnimationSystem.create(avatarModel, config);
      this._head.registerMixer(this._bodyAnim.mixer, {
        boneClaim: [
          'Spine', 'Spine1', 'Spine2', 'Hips',
          'LeftShoulder', 'LeftArm', 'LeftForeArm', 'LeftHand',
          'LeftHandThumb1', 'LeftHandThumb2', 'LeftHandThumb3',
          'LeftHandIndex1', 'LeftHandIndex2', 'LeftHandIndex3',
          'LeftHandMiddle1', 'LeftHandMiddle2', 'LeftHandMiddle3',
          'LeftHandRing1', 'LeftHandRing2', 'LeftHandRing3',
          'LeftHandPinky1', 'LeftHandPinky2', 'LeftHandPinky3',
          'RightShoulder', 'RightArm', 'RightForeArm', 'RightHand',
          'RightHandThumb1', 'RightHandThumb2', 'RightHandThumb3',
          'RightHandIndex1', 'RightHandIndex2', 'RightHandIndex3',
          'RightHandMiddle1', 'RightHandMiddle2', 'RightHandMiddle3',
          'RightHandRing1', 'RightHandRing2', 'RightHandRing3',
          'RightHandPinky1', 'RightHandPinky2', 'RightHandPinky3',
          'LeftUpLeg', 'LeftLeg', 'LeftFoot', 'LeftToeBase',
          'RightUpLeg', 'RightLeg', 'RightFoot', 'RightToeBase',
        ],
      });
      this._head.registerMorphLayer('gestureExpressions', {
        order: 10, blend: 'additive',
        update: (dt, get, set) => {
          const exprs = this._bodyAnim.sampleExpressions();
          if (!exprs) return;
          for (const [name, value] of Object.entries(exprs)) set(name, value);
        }
      });
    } catch (e) {
      console.warn('[AvatarEngine] Body animation load failed:', e);
    }
  }
}


export class AvatarControls {

  constructor(container, engine, opts = {}) {
    this._container = container;
    this._engine = engine;
    this._config = null;
    this._init(opts);
  }

  async _init(opts) {
    this._render(opts);
    await this._loadConfig();
    this._loadVoices();
    this._pollModelStatus();
  }

  _render(opts) {
    this._container.innerHTML = `
      <label>LLM: <select id="ac-llmModel">
        <option value="claude-haiku-4-5@20251001">Haiku 4.5 (fast)</option>
        <option value="claude-sonnet-4@20250514">Sonnet 4</option>
        <option value="claude-opus-4@20250514">Opus 4</option>
      </select></label>
      <label>Voice: <select id="ac-ttsModel">
        <option value="">Loading voices...</option>
      </select></label>
      <label>Speed: <input id="ac-speed" type="range" min="0.6" max="1.4" step="0.05" value="0.9"
        style="width:80px;vertical-align:middle"> <span id="ac-speedVal">0.9x</span></label>
      <label>Avatar: <select id="ac-avatarModel">
        <option value="avatar.glb">Current</option>
      </select></label>
      <label>Animation: <select id="ac-animPicker">
        <option value="">Auto (idle cycle)</option>
      </select></label>
      <button id="ac-playAnim" style="padding:0.3rem 0.6rem;border-radius:6px;border:1px solid #555;background:#2a2a3e;color:#e0e0e0;cursor:pointer;font-size:0.85rem" title="Play selected animation">&#9654;</button>
      <div id="ac-modelStatus" style="font-size:0.75rem;color:#999"></div>
    `;
    this._el('ac-speed').addEventListener('input', () => {
      const val = this._el('ac-speed').value;
      this._el('ac-speedVal').textContent = val + 'x';
      this._engine.playbackRate = parseFloat(val);
    });
    this._el('ac-avatarModel').addEventListener('change', () => {
      this._engine.switchAvatar(this._el('ac-avatarModel').value);
    });
    this._el('ac-playAnim').addEventListener('click', () => {
      const clip = this._el('ac-animPicker').value;
      if (clip) this._engine.triggerGesture(clip);
    });
  }

  get llmModel() { return this._el('ac-llmModel').value; }
  get ttsModel() { return this._el('ac-ttsModel').value; }
  get speed() { return parseFloat(this._el('ac-speed').value); }

  async _loadConfig() {
    try {
      const r = await fetch('/api/avatar/config');
      this._config = await r.json();
      if (this._config.speed) {
        this._el('ac-speed').value = this._config.speed;
        this._el('ac-speedVal').textContent = this._config.speed + 'x';
        this._engine.playbackRate = this._config.speed;
      }
      if (this._config.minPlaybackRate) {
        this._el('ac-speed').min = this._config.minPlaybackRate;
      }
      if (this._config.model) {
        const sel = this._el('ac-llmModel');
        for (let i = 0; i < sel.options.length; i++) {
          if (sel.options[i].value === this._config.model) { sel.value = this._config.model; break; }
        }
      }
    } catch (err) {
      console.warn('Failed to load avatar config:', err);
    }
  }

  _loadVoices() {
    const sel = this._el('ac-ttsModel');
    fetch('/api/avatar/voices')
      .then(r => r.json())
      .then(voices => {
        sel.innerHTML = '';
        const groups = {};
        for (const v of voices) {
          let group, label;
          if (v.startsWith('qwen3:clone:')) { group = 'Qwen3 Cloned'; label = v.substring(12).replace(/_/g, ' '); }
          else if (v.startsWith('qwen3:')) { group = 'Qwen3-TTS'; label = v.substring(6); }
          else if (v.startsWith('kokoro:')) { group = 'Kokoro'; label = v.substring(7).replace(/_/g, ' '); }
          else if (v.startsWith('sherpa:')) { group = 'Sherpa VITS'; label = v.substring(7); }
          else if (v.startsWith('audio8') || v === 'cosyvoice3') { group = 'Experimental'; label = v; }
          else { group = 'VITS'; label = v; }
          if (!groups[group]) groups[group] = [];
          groups[group].push({ value: v, label });
        }
        for (const gname of Object.keys(groups)) {
          const optgroup = document.createElement('optgroup');
          optgroup.label = gname;
          for (const item of groups[gname]) {
            const opt = document.createElement('option');
            opt.value = item.value; opt.textContent = item.label;
            optgroup.appendChild(opt);
          }
          sel.appendChild(optgroup);
        }
        const saved = localStorage.getItem('avatar-ttsModel');
        if (saved && sel.querySelector('option[value="' + CSS.escape(saved) + '"]')) {
          sel.value = saved;
        } else if (this._config && this._config.ttsVoice) {
          sel.value = this._config.ttsVoice;
        }
        sel.addEventListener('change', () => {
          localStorage.setItem('avatar-ttsModel', sel.value);
        });
      })
      .catch(() => { sel.innerHTML = '<option value="lessac-medium">lessac-medium</option>'; });
  }

  _pollModelStatus() {
    const statusEl = this._el('ac-modelStatus');
    const sel = this._el('ac-ttsModel');
    const timer = setInterval(() => {
      fetch('/api/models/status')
        .then(r => r.json())
        .then(data => {
          const total = Object.keys(data).length;
          let ready = 0, downloading = 0, errors = 0;
          for (const key in data) {
            if (data[key] === 'READY') ready++;
            else if (data[key] === 'DOWNLOADING') downloading++;
            else if (data[key] === 'ERROR') errors++;
          }
          for (let i = 0; i < sel.options.length; i++) {
            const opt = sel.options[i];
            const engineKey = opt.value.includes(':') ? opt.value.split(':')[0] : opt.value;
            const st = data[engineKey] || data[opt.value];
            opt.disabled = st !== 'READY';
            opt.text = opt.text.replace(/ \(downloading\.\.\.\)$/, '');
            if (st === 'DOWNLOADING') opt.text += ' (downloading...)';
          }
          if (ready === total) {
            statusEl.innerHTML = '<span style="color:#4a9">All voice models ready</span>';
            clearInterval(timer);
            setTimeout(() => { statusEl.style.display = 'none'; }, 3000);
          } else if (downloading > 0) {
            statusEl.innerHTML = '<span style="color:#d9a547">Downloading: ' + ready + '/' + total + ' ready</span>';
          } else if (errors > 0) {
            statusEl.innerHTML = '<span style="color:#d64">Errors: ' + errors + '/' + total + '</span>';
            if (ready + errors === total) clearInterval(timer);
          }
        })
        .catch(() => {});
    }, 2000);
  }

  _el(id) { return document.getElementById(id); }
}
