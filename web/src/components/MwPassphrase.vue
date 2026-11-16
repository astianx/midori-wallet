<script>
import { hex } from '@scure/base';
import { hmac } from '@noble/hashes/hmac';
import { sha256 } from '@noble/hashes/sha256';
import { wordlist } from '@scure/bip39/wordlists/english';
import { mnemonicToSeed, validateMnemonic } from '@scure/bip39';

import { onShowOnHide } from '../lib/mixins.js';
import MwButton from './MwButton.vue';
import { beginRecovery, discardRecovery } from '../lib/account/recovery.js';
import MwButtonGroup from './MwButtonGroup.vue';
import MwFormTextarea from './MwForm/MwFormTextarea.vue';

export default {
  components: {
    MwButton,
    MwButtonGroup,
    MwFormTextarea,
  },
  mixins: [onShowOnHide],
  onShow() { this.active = true; },
  onHide() { this.active = false; this.passphrase = ''; this.suggestions = []; },
  props: {
    recoveryDescriptor: { type: Object, default: undefined },
    recovery: { type: Boolean, default: false },
    modelValue: {
      type: String,
      default: '',
    },
    isLoading: {
      type: Boolean,
      default: false,
    },
  },
  emits: ['update:modelValue', 'confirm'],
  data() {
    return {
      active: false,
      busy: false,
      error: undefined,
      suggestions: [],
      showHelp: false,
    };
  },
  computed: {
    passphrase: {
      get() {
        return this.modelValue;
      },
      set(value) {
        this.$emit('update:modelValue', value);
      },
    },
    words() {
      return this.passphrase.toLowerCase().split(/\s+/).filter(Boolean);
    },
  },
  watch: {
    passphrase(passphrase, passphraseOld) {
      this.error = undefined;
      const passphraseDelta = Math.abs(passphrase.length - passphraseOld.length);
      if (passphraseDelta > 1) return this.suggestions = [];

      const last = passphrase.split(' ').at(-1);
      if (last) {
        const suggestions = wordlist.filter((word) => word.startsWith(last)).slice(0, 3);
        if (suggestions.length === 1 && suggestions[0] === last) {
          this.suggestions = [];
        } else {
          this.suggestions = suggestions;
        }
      } else {
        this.suggestions = [];
      }
    },
  },
  methods: {
    async confirm() {
      if (this.busy || this.isLoading) return;
      this.busy = true; const passphrase = this.words.join(' ');
      this.error = undefined;
      try {
        if (!validateMnemonic(passphrase, wordlist)) throw new Error();
        if (this.recovery) {
          const handle = await beginRecovery(passphrase, this.recoveryDescriptor);
          if (!this.active) { discardRecovery(handle); return; }
          this.$emit('update:modelValue', '');
          this.$emit('confirm', handle);
          return;
        }
        const seed = await mnemonicToSeed(passphrase);
        if (!this.active) { seed.fill(0); return; }
        if (this.$account.isCreated) {
          const detailsKey = hmac(sha256, 'Midori Wallet', hex.encode(seed));
          if (hex.encode(detailsKey) !== hex.encode(this.$account.clientStorage.getDetailsKey())) {
            throw new Error();
          }
        }
        this.$emit('confirm', seed);
      } catch (err) {
        this.error = this.$t(this.recoveryDescriptor ? 'The recovery file does not match this account.' : 'Invalid passphrase');
      } finally { this.busy = false; }
    },
    acceptSuggestion(suggestion) {
      window.taptic?.tap();
      this.error = undefined;
      this.passphrase = [
        ...this.words.slice(0, -1),
        suggestion + ' ',
      ].join(' ');
      this.$refs.passphrase.focus();
    },
  },
};
</script>

<template>
  <div class="&__container">
    <MwFormTextarea
      ref="passphrase"
      v-model="passphrase"
      :label="$t('Passphrase')"
      :error="error"
    />
    <div
      v-if="suggestions.length"
      class="&__suggestions"
      lang="en"
    >
      <MwButton
        v-for="suggestion of suggestions"
        :key="suggestion"
        class="&__suggestion"
        @click="() => acceptSuggestion(suggestion)"
      >
        {{ suggestion }}
      </MwButton>
    </div>
  </div>

  <p v-if="showHelp" role="status">{{ $t('Your 12 words restore your wallet. You can also save this optional recovery file. It contains no private keys.') }}</p>
  <MwButtonGroup class="&__buttons">
    <MwButton
      type="primary"
      :isLoading="isLoading || busy"
      @click="confirm"
    >
      {{ $t('Confirm') }}
    </MwButton>
    <MwButton
      type="primary-link"
      @click="showHelp = !showHelp"
    >
      {{ $t('What is a passphrase?') }}
    </MwButton>
  </MwButtonGroup>
</template>

<style lang="scss">
  .#{ $filename } {
    &__container {
      display: flex;
      flex-direction: column;
      flex-grow: 1;
      gap: var(--spacing-xl);
    }

    &__suggestions {
      display: flex;
      flex-wrap: wrap;
      gap: var(--spacing-xs);
    }

    &__suggestion {
      @include text-md;
      padding: var(--spacing-xs) var(--spacing-lg);
      border-radius: 0.75rem;
      background-color: var(--color-modal-background);
      box-shadow: 0 0.75rem 2.5rem rgb(0 0 0 / 7%);
    }

    &__buttons {
      flex-shrink: 0;
    }
  }
</style>
