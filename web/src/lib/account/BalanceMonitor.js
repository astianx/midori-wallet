export class BalanceMonitor {
  #timer; #pending; #generation = 0;
  constructor({ refresh, interval = 15000, active = () => true }) { Object.assign(this, { refresh, interval, active }); }
  start() { this.stop(); const generation = this.#generation; const tick = async () => { if (generation !== this.#generation) return; try { if (this.active()) await this.update(); } finally { if (generation === this.#generation) this.#timer = setTimeout(tick, this.interval); } }; this.#timer = setTimeout(tick, this.interval); }
  update() { if (!this.#pending) this.#pending = Promise.resolve().then(this.refresh).finally(() => { this.#pending = null; }); return this.#pending; }
  stop() { this.#generation++; clearTimeout(this.#timer); }
}
