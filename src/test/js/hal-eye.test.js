import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { drawHalEyeOnContext, drawHalEye, HalEyeAnimator } from
  '../../main/resources/static/js/hal-eye.js';

// ── Helper: create a lightweight canvas context mock ────────────────────────
// We hand-roll the mock instead of depending on jest-canvas-mock so that
// the test setup stays dependency-free.  Every method that returns an object
// (createRadialGradient) returns a fresh spy-equipped stub.
function makeCtx() {
  const gradient = () => ({ addColorStop: vi.fn() });
  return {
    clearRect:             vi.fn(),
    beginPath:             vi.fn(),
    arc:                   vi.fn(),
    fill:                  vi.fn(),
    stroke:                vi.fn(),
    createRadialGradient:  vi.fn(gradient),
    fillStyle:             null,
    strokeStyle:           null,
    lineWidth:             null,
  };
}

// ── Helper: create a minimal canvas element mock ─────────────────────────────
function makeCanvas(size = 100) {
  const ctx = makeCtx();
  return {
    width:      size,
    height:     size,
    getContext: vi.fn(() => ctx),
    _ctx:       ctx,   // expose for assertions
  };
}

// ── Constant "now" used across tests so glowIntensity is deterministic ───────
// Math.sin(0) = 0  →  glowIntensity (thinking) = 0.6 + 0.4 * 0 = 0.6
const FIXED_NOW = 0;

// ════════════════════════════════════════════════════════════════════════════
// drawHalEyeOnContext – pure drawing logic
// ════════════════════════════════════════════════════════════════════════════
describe('drawHalEyeOnContext', () => {
  let ctx;
  beforeEach(() => { ctx = makeCtx(); });

  // ── clearRect ──────────────────────────────────────────────────────────
  it('clears the full canvas before drawing', () => {
    drawHalEyeOnContext(ctx, 100, false, FIXED_NOW);
    expect(ctx.clearRect).toHaveBeenCalledOnce();
    expect(ctx.clearRect).toHaveBeenCalledWith(0, 0, 100, 100);
  });

  it('uses the supplied size, not a hard-coded constant', () => {
    drawHalEyeOnContext(ctx, 220, false, FIXED_NOW);
    expect(ctx.clearRect).toHaveBeenCalledWith(0, 0, 220, 220);
  });

  // ── arc call counts ────────────────────────────────────────────────────
  it('draws 5 arcs when not thinking (outer, iris, lens, pupil, highlight)', () => {
    drawHalEyeOnContext(ctx, 100, false, FIXED_NOW);
    expect(ctx.arc).toHaveBeenCalledTimes(5);
  });

  it('draws 6 arcs when thinking (adds glow halo)', () => {
    drawHalEyeOnContext(ctx, 100, true, FIXED_NOW);
    expect(ctx.arc).toHaveBeenCalledTimes(6);
  });

  // ── fill / stroke call counts ─────────────────────────────────────────
  it('calls fill 5 times when not thinking', () => {
    drawHalEyeOnContext(ctx, 100, false, FIXED_NOW);
    expect(ctx.fill).toHaveBeenCalledTimes(5);
  });

  it('calls fill 6 times when thinking (halo adds one more fill)', () => {
    drawHalEyeOnContext(ctx, 100, true, FIXED_NOW);
    expect(ctx.fill).toHaveBeenCalledTimes(6);
  });

  it('calls stroke exactly 2 times (outer ring + lens)', () => {
    drawHalEyeOnContext(ctx, 100, false, FIXED_NOW);
    expect(ctx.stroke).toHaveBeenCalledTimes(2);
  });

  // ── gradient creation count ───────────────────────────────────────────
  it('creates 3 gradients when not thinking (outer, iris, lens)', () => {
    drawHalEyeOnContext(ctx, 100, false, FIXED_NOW);
    expect(ctx.createRadialGradient).toHaveBeenCalledTimes(3);
  });

  it('creates 4 gradients when thinking (adds halo gradient)', () => {
    drawHalEyeOnContext(ctx, 100, true, FIXED_NOW);
    expect(ctx.createRadialGradient).toHaveBeenCalledTimes(4);
  });

  // ── geometry proportions ──────────────────────────────────────────────
  it('centers the first 4 arcs at (size/2, size/2); specular highlight is offset', () => {
    const size = 200;
    drawHalEyeOnContext(ctx, size, false, FIXED_NOW);
    const cx = size / 2;
    const cy = size / 2;
    // Arcs 0-3 (outer, iris, lens, pupil) are all centered
    ctx.arc.mock.calls.slice(0, 4).forEach(([x, y]) => {
      expect(x).toBe(cx);
      expect(y).toBe(cy);
    });
    // Arc 4 (specular highlight) is intentionally offset for a realistic lens glint
    const [hx, hy] = ctx.arc.mock.calls[4];
    expect(hx).not.toBe(cx);
    expect(hy).not.toBe(cy);
  });

  it('outer radius is 46 % of size', () => {
    const size = 200;
    drawHalEyeOnContext(ctx, size, false, FIXED_NOW);
    // First arc call is the outer ring
    const [,, outerR] = ctx.arc.mock.calls[0];
    expect(outerR).toBeCloseTo(size * 0.46);
  });

  // ── glowIntensity ─────────────────────────────────────────────────────
  it('uses glowIntensity=0.85 when not thinking (fixed, no sine)', () => {
    // With thinking=false, intensity is always 0.85 regardless of `now`.
    // We verify indirectly: stroke was called for the lens with the right opacity.
    // The strokeStyle is set to rgba(255,80,50, 0.85*0.6) = rgba(255,80,50,0.51)
    drawHalEyeOnContext(ctx, 100, false, FIXED_NOW);
    expect(ctx.strokeStyle).toBe(`rgba(255, 80, 50, ${0.85 * 0.6})`);
  });

  it('glowIntensity is sine-based when thinking (now=0 → intensity=0.6)', () => {
    // Math.sin(0/180) = 0  →  0.6 + 0.4*0 = 0.6
    drawHalEyeOnContext(ctx, 100, true, FIXED_NOW);
    expect(ctx.strokeStyle).toBe(`rgba(255, 80, 50, ${0.6 * 0.6})`);
  });

  it('glowIntensity oscillates with different `now` values', () => {
    // now = Math.PI * 180 → sin(π) ≈ 0  → intensity ≈ 0.6
    const ctx1 = makeCtx();
    drawHalEyeOnContext(ctx1, 100, true, Math.PI * 180);
    // now = (Math.PI/2) * 180 = 90π → sin(π/2) = 1 → intensity = 1.0
    const ctx2 = makeCtx();
    drawHalEyeOnContext(ctx2, 100, true, (Math.PI / 2) * 180);
    // The strokeStyle value must differ between the two calls
    expect(ctx1.strokeStyle).not.toBe(ctx2.strokeStyle);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// drawHalEye – DOM adapter
// ════════════════════════════════════════════════════════════════════════════
describe('drawHalEye', () => {
  it('does nothing when canvas is null', () => {
    // Must not throw
    expect(() => drawHalEye(null, false)).not.toThrow();
  });

  it('does nothing when canvas is undefined', () => {
    expect(() => drawHalEye(undefined, false)).not.toThrow();
  });

  it('obtains a 2d context from the canvas element', () => {
    const canvas = makeCanvas(100);
    drawHalEye(canvas, false, FIXED_NOW);
    expect(canvas.getContext).toHaveBeenCalledWith('2d');
  });

  it('passes canvas.width as size to the drawing core', () => {
    const canvas = makeCanvas(150);
    drawHalEye(canvas, false, FIXED_NOW);
    expect(canvas._ctx.clearRect).toHaveBeenCalledWith(0, 0, 150, 150);
  });

  it('forwards the thinking flag', () => {
    const canvasFalse = makeCanvas(100);
    const canvasTrue  = makeCanvas(100);
    drawHalEye(canvasFalse, false, FIXED_NOW);
    drawHalEye(canvasTrue,  true,  FIXED_NOW);
    // thinking=false → 5 arcs, thinking=true → 6 arcs
    expect(canvasFalse._ctx.arc).toHaveBeenCalledTimes(5);
    expect(canvasTrue._ctx.arc).toHaveBeenCalledTimes(6);
  });
});

// ════════════════════════════════════════════════════════════════════════════
// HalEyeAnimator – lifecycle
// ════════════════════════════════════════════════════════════════════════════
describe('HalEyeAnimator', () => {
  let rafSpy, cafSpy;

  beforeEach(() => {
    // Replace global rAF/cAF with spies
    rafSpy = vi.spyOn(globalThis, 'requestAnimationFrame').mockReturnValue(42);
    cafSpy = vi.spyOn(globalThis, 'cancelAnimationFrame').mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('calls requestAnimationFrame on start()', () => {
    const animator = new HalEyeAnimator(makeCanvas(), makeCanvas());
    animator.start(() => false);
    expect(rafSpy).toHaveBeenCalledOnce();
  });

  it('calls cancelAnimationFrame with the stored handle on stop()', () => {
    const animator = new HalEyeAnimator(makeCanvas(), makeCanvas());
    animator.start(() => false);
    animator.stop();
    expect(cafSpy).toHaveBeenCalledWith(42);
  });

  it('clears the handle after stop() so double-stop is a no-op', () => {
    const animator = new HalEyeAnimator(makeCanvas(), makeCanvas());
    animator.start(() => false);
    animator.stop();
    animator.stop();  // second call must not throw or call cAF again
    expect(cafSpy).toHaveBeenCalledOnce();
  });

  it('draws on both canvases each tick', () => {
    const main = makeCanvas(220);
    const mini = makeCanvas(32);
    const animator = new HalEyeAnimator(main, mini);
    animator.start(() => false);
    // _tick() was called synchronously once before the first rAF callback
    expect(main._ctx.clearRect).toHaveBeenCalledWith(0, 0, 220, 220);
    expect(mini._ctx.clearRect).toHaveBeenCalledWith(0, 0, 32, 32);
  });

  it('reads the current thinking state via the callback on every tick', () => {
    let thinking = false;
    const main = makeCanvas(220);
    const animator = new HalEyeAnimator(main, makeCanvas(32));
    animator.start(() => thinking);

    // First tick → not thinking → 5 arcs
    expect(main._ctx.arc).toHaveBeenCalledTimes(5);

    // Simulate a second tick by calling _tick() directly with thinking=true
    thinking = true;
    main._ctx.arc.mockClear();
    animator._tick();
    expect(main._ctx.arc).toHaveBeenCalledTimes(6);
  });

  it('works gracefully when main canvas is null', () => {
    const animator = new HalEyeAnimator(null, makeCanvas(32));
    expect(() => animator.start(() => false)).not.toThrow();
  });

  it('works gracefully when mini canvas is null', () => {
    const animator = new HalEyeAnimator(makeCanvas(220), null);
    expect(() => animator.start(() => false)).not.toThrow();
  });
});



