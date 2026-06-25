/**
 * hal-eye.js – HAL 9000 Eye Animation
 *
 * Three exported units, deliberately separated by testability:
 *
 *  drawHalEyeOnContext(ctx, size, thinking, now)
 *    Pure drawing function – no DOM access, fully unit-testable.
 *    `now` defaults to Date.now() and can be injected in tests to make
 *    the glow-intensity calculation deterministic.
 *
 *  drawHalEye(canvas, thinking, now)
 *    Thin DOM adapter – retrieves the 2D context from a canvas element
 *    and delegates to drawHalEyeOnContext.
 *
 *  HalEyeAnimator
 *    Manages the requestAnimationFrame loop.  Consumers supply a
 *    `isThinkingFn` callback so the animator never reads global state.
 */

// ── Pure drawing core ────────────────────────────────────────────────────────

/**
 * @param {CanvasRenderingContext2D} ctx
 * @param {number}  size     – canvas width/height in px
 * @param {boolean} thinking – true → pulsing glow animation
 * @param {number}  [now]    – timestamp used for glow (injectable for tests)
 */
export function drawHalEyeOnContext(ctx, size, thinking, now = Date.now()) {
  const cx = size / 2;
  const cy = size / 2;
  const outerR = size * 0.46;
  const midR   = size * 0.34;
  const innerR = size * 0.18;
  const pupilR = size * 0.09;

  ctx.clearRect(0, 0, size, size);

  // ── Outer dark ring ──────────────────────────────────────────────────
  ctx.beginPath();
  ctx.arc(cx, cy, outerR, 0, Math.PI * 2);
  const outerGrad = ctx.createRadialGradient(cx, cy, midR, cx, cy, outerR);
  outerGrad.addColorStop(0, '#1a0000');
  outerGrad.addColorStop(1, '#050000');
  ctx.fillStyle = outerGrad;
  ctx.fill();
  ctx.strokeStyle = '#2a0000';
  ctx.lineWidth = 2;
  ctx.stroke();

  // ── Middle iris (glowing red) ────────────────────────────────────────
  const glowIntensity = thinking
    ? (0.6 + 0.4 * Math.sin(now / 180))
    : 0.85;

  ctx.beginPath();
  ctx.arc(cx, cy, midR, 0, Math.PI * 2);
  const irisGrad = ctx.createRadialGradient(cx, cy, 0, cx, cy, midR);
  irisGrad.addColorStop(0,   `rgba(255, 40, 0, ${glowIntensity})`);
  irisGrad.addColorStop(0.4, `rgba(200, 0, 0, ${glowIntensity * 0.9})`);
  irisGrad.addColorStop(0.8, `rgba(120, 0, 0, ${glowIntensity * 0.7})`);
  irisGrad.addColorStop(1,   `rgba(60, 0, 0, ${glowIntensity * 0.5})`);
  ctx.fillStyle = irisGrad;
  ctx.fill();

  // ── Outer glow halo (thinking only) ─────────────────────────────────
  if (thinking) {
    ctx.beginPath();
    ctx.arc(cx, cy, midR + 4, 0, Math.PI * 2);
    const halo = ctx.createRadialGradient(cx, cy, midR, cx, cy, midR + size * 0.12);
    halo.addColorStop(0, `rgba(220, 0, 0, ${0.3 * glowIntensity})`);
    halo.addColorStop(1, 'rgba(220, 0, 0, 0)');
    ctx.fillStyle = halo;
    ctx.fill();
  }

  // ── Inner lens ring ──────────────────────────────────────────────────
  ctx.beginPath();
  ctx.arc(cx, cy, innerR, 0, Math.PI * 2);
  const lensGrad = ctx.createRadialGradient(cx, cy, 0, cx, cy, innerR);
  lensGrad.addColorStop(0,   '#ff4422');
  lensGrad.addColorStop(0.5, '#cc1100');
  lensGrad.addColorStop(1,   '#880000');
  ctx.fillStyle = lensGrad;
  ctx.fill();
  ctx.strokeStyle = `rgba(255, 80, 50, ${glowIntensity * 0.6})`;
  ctx.lineWidth = 1.5;
  ctx.stroke();

  // ── Pupil ────────────────────────────────────────────────────────────
  ctx.beginPath();
  ctx.arc(cx, cy, pupilR, 0, Math.PI * 2);
  ctx.fillStyle = '#050000';
  ctx.fill();

  // ── Specular highlight ───────────────────────────────────────────────
  ctx.beginPath();
  ctx.arc(cx - innerR * 0.3, cy - innerR * 0.3, pupilR * 0.35, 0, Math.PI * 2);
  ctx.fillStyle = `rgba(255, 180, 150, ${0.4 * glowIntensity})`;
  ctx.fill();
}

// ── DOM adapter ──────────────────────────────────────────────────────────────

/**
 * @param {HTMLCanvasElement|null} canvas
 * @param {boolean} thinking
 * @param {number}  [now]
 */
export function drawHalEye(canvas, thinking, now = Date.now()) {
  if (!canvas) return;
  drawHalEyeOnContext(canvas.getContext('2d'), canvas.width, thinking, now);
}

// ── Animation controller ─────────────────────────────────────────────────────

export class HalEyeAnimator {
  /**
   * @param {HTMLCanvasElement|null} mainCanvas  – large eye panel (220 px)
   * @param {HTMLCanvasElement|null} miniCanvas  – small header eye (32 px)
   */
  constructor(mainCanvas, miniCanvas) {
    this._mainCanvas   = mainCanvas;
    this._miniCanvas   = miniCanvas;
    this._rafHandle    = null;
    this._isThinkingFn = () => false;
  }

  /**
   * Start the animation loop.
   * @param {() => boolean} isThinkingFn  – called every frame to read current state
   */
  start(isThinkingFn) {
    this._isThinkingFn = isThinkingFn ?? (() => false);
    this._tick();
  }

  /** Stop the animation loop. */
  stop() {
    if (this._rafHandle !== null) {
      cancelAnimationFrame(this._rafHandle);
      this._rafHandle = null;
    }
  }

  _tick() {
    const thinking = this._isThinkingFn();
    drawHalEye(this._mainCanvas, thinking);
    drawHalEye(this._miniCanvas, thinking);
    this._rafHandle = requestAnimationFrame(() => this._tick());
  }
}

