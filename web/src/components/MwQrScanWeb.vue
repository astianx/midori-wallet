<script>
import { onShowOnHide } from '../lib/mixins.js';

import MwLoader from './MwLoader.vue';

export default {
  components: {
    MwLoader,
  },
  mixins: [onShowOnHide],
  emits: ['back', 'scan'],
  data() { return { isLoading: true, active: false, generation: 0, stream: null, reader: null, controls: null, barcodeDetector: null, scanInterval: null }; },
  onShow() { this.active = true; this.generation++; this.start(); },
  onHide() { this.active = false; this.generation++; this.stop(); },
  methods: {
    async start() {
      const generation = this.generation;
      this.isLoading = true;
      try {
        const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: { ideal: 'environment' }, width: { ideal: 320 }, height: { ideal: 320 } }, audio: false });
        if (!this.active || generation !== this.generation || !this.$refs.video) { stream.getTracks().forEach(track => track.stop()); return; }
        this.stream = stream; this.$refs.video.srcObject = stream;
        await this.$refs.video.play();
        if (!this.active || generation !== this.generation) { stream.getTracks().forEach(track => track.stop()); return; }
        if (window.BarcodeDetector && (await window.BarcodeDetector.getSupportedFormats()).includes('qr_code')) {
          if (!this.active || generation !== this.generation) return;
          this.barcodeDetector = new window.BarcodeDetector({ formats: ['qr_code'] });
          this.scanInterval = window.setInterval(this.scan, 1000);
        } else {
          const { BrowserQRCodeReader } = await import('@zxing/browser');
          if (!this.active || generation !== this.generation) return;
          this.reader = new BrowserQRCodeReader();
          const controls = await this.reader.decodeFromVideoElement(this.$refs.video, result => { if (result && this.active && generation === this.generation) this.$emit('scan', { data: result.getText() }); });
          if (!this.active || generation !== this.generation) { controls.stop(); return; }
          this.controls = controls;
        }
        this.isLoading = false;
      } catch { if (this.active && generation === this.generation) { this.stop(); this.$emit('back'); } }
    },
    stop() {
      clearInterval(this.scanInterval); this.controls?.stop(); this.$refs.video?.pause(); this.stream?.getTracks().forEach(track => track.stop()); this.stream = null; this.controls = null;
    },
    async scan() {
      const generation = this.generation;
      try { const barcodes = await this.barcodeDetector.detect(this.$refs.video); if (this.active && generation === this.generation && barcodes.length) this.$emit('scan', { data: barcodes[0].rawValue }); }
      catch { if (this.active && generation === this.generation) { this.stop(); this.$emit('back'); } }
    },
  },
};
</script>

<template>
  <MwLoader v-if="isLoading" />
  <div v-show="!isLoading">
    <video
      ref="video"
      class="&__video"
      playsinline
      muted
    />
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__video {
      display: block;
      width: 100%;
      max-width: 20rem;
      height: 100%;
      max-height: 20rem;
      border: 1px solid var(--color-divider);
      border-radius: 0.625rem;
      margin: 0 auto;
    }
  }
</style>
