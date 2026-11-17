export default {
  data() { return { viewportHeight: window.innerHeight, viewportOffset: 0, keyboardOpen: false, viewportBaseline: window.innerHeight }; },
  mounted() {
    window.addEventListener('resize', this.updateViewport, { passive: true });
    window.visualViewport?.addEventListener('resize', this.updateViewport, { passive: true });
    window.visualViewport?.addEventListener('scroll', this.updateViewport, { passive: true });
    this.updateViewport();
  },
  beforeUnmount() {
    window.removeEventListener('resize', this.updateViewport);
    window.visualViewport?.removeEventListener('resize', this.updateViewport);
    window.visualViewport?.removeEventListener('scroll', this.updateViewport);
    cancelAnimationFrame(this.viewportFrame);
  },
  methods: {
    updateViewport() {
      cancelAnimationFrame(this.viewportFrame);
      this.viewportFrame = requestAnimationFrame(() => {
        const viewport = window.visualViewport;
        const zoomed = viewport && Math.abs(viewport.scale - 1) > 0.05;
        const editable = document.activeElement?.matches('input:not([type=checkbox]):not([type=radio]),textarea,[contenteditable=true]');
        const height = zoomed ? window.innerHeight : Math.min(window.innerHeight, viewport?.height ?? window.innerHeight);
        if (!editable) this.viewportBaseline = height;
        this.viewportHeight = height;
        this.viewportOffset = zoomed ? 0 : viewport?.offsetTop || 0;
        this.keyboardOpen = !zoomed && editable && this.viewportBaseline - height > 120;
        if (this.keyboardOpen) this.$nextTick(() => document.activeElement?.scrollIntoView({ block: 'nearest', inline: 'nearest' }));
      });
    },
  },
};
