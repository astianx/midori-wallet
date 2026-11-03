<script>
import { onShowOnHide } from '../lib/mixins.js';

import MwLoader from './MwLoader.vue';

export default {
  components: {
    MwLoader,
  },
  mixins: [onShowOnHide],
  emits: ['back', 'scan'],
  data() {
    return {
      isLoading: true,
    };
  },
  async onShow() {
    await this.start();
  },
  async onHide() {
    await this.stop();
  },
  methods: {
    async start() {
      try {
        this.$options.stream = await navigator.mediaDevices.getUserMedia({
          video: {
            facingMode: { ideal: 'environment' },
            width: { ideal: 320 },
            height: { ideal: 320 },
          },
          audio: false,
        });
        if (!this.$refs.video) { this.$options.stream.getTracks().forEach(track => track.stop()); return; }
        this.$refs.video.srcObject = this.$options.stream;
        await this.$refs.video.play();
        if (window.BarcodeDetector && (await window.BarcodeDetector.getSupportedFormats()).includes('qr_code')) {
          this.$options.barcodeDetector = new window.BarcodeDetector({ formats: ['qr_code'] });
          this.$options.scanInterval = window.setInterval(this.scan, 1000);
        } else {
          const { BrowserQRCodeReader } = await import('@zxing/browser');
          this.$options.reader = new BrowserQRCodeReader();
          this.$options.controls = await this.$options.reader.decodeFromVideoElement(this.$refs.video, (result) => { if (result) this.$emit('scan', { data: result.getText() }); });
        }
        this.isLoading = false;
      } catch (err) {
        // TODO handle errors
        // https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia#exceptions
        console.error('Camera unavailable');
        this.$emit('back');
      }
    },
    async stop() {
      try {
        clearInterval(this.$options.scanInterval);
        this.$options.controls?.stop();
        this.$refs.video?.pause();
        if (this.$options.stream) {
          const tracks = this.$options.stream.getTracks();
          tracks.forEach((track) => {
            if (track.readyState === 'live') {
              track.stop();
            }
          });
        }
      } catch (err) {
        // TODO handle errors
        console.error('Camera unavailable');
      }
    },
    async scan() {
      try {
        const barcodes = await this.$options.barcodeDetector.detect(this.$refs.video);
        if (barcodes.length <= 0) return;
        this.$emit('scan', { data: barcodes[0].rawValue });
      } catch (err) {
        // TODO handle errors
        console.error('Camera unavailable');
        this.$emit('back');
      }
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
