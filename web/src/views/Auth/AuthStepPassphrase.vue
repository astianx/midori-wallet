<script>
import AuthStepLayout from '../../layouts/AuthStepLayout.vue';
import MwButton from '../../components/MwButton.vue';
import MwButtonGroup from '../../components/MwButtonGroup.vue';
import MwFormTextareaReadonly from '../../components/MwForm/MwFormTextareaReadonly.vue';
import MwStep from '../../components/MwStep.vue';
import MwWarning from '../../components/MwWarning.vue';

import TickIcon from '../../assets/svg/tick.svg';
import { recoveryPhrase } from '../../lib/account/recovery.js';

export default {
  components: {
    AuthStepLayout,
    MwButton,
    MwButtonGroup,
    MwFormTextareaReadonly,
    MwWarning,
    TickIcon,
  },
  extends: MwStep,
  data() {
    return {
      isCopied: false,
      agreements: [],
    };
  },
  computed: {
    phrase() { return recoveryPhrase(this.storage.recoveryHandle); },
    agreementLinkTexts() {
      return this.$tBrackets(this.$t('I agree to the {terms}((Terms of Service))'));
    },
  },
  methods: {
    copyToClipboard() {
      navigator.clipboard.writeText(this.phrase).then(() => {
        this.isCopied = true;
        setTimeout(() => {
          this.isCopied = false;
        }, 1000);
      }, () => {});
    },
  },
};
</script>

<template>
  <AuthStepLayout
    :title="$t('Your passphrase')"
    :description="$t('12 words')"
  >
    <div class="&__passphrase_wrapper">
      <MwFormTextareaReadonly
        class="&__passphrase"
        :value="phrase"
      />
      <MwButton
        class="&__copy"
        @click="copyToClipboard"
      >
        {{ isCopied ? $t('Copied!') : $t('Copy') }}
      </MwButton>
    </div>

    <div class="&__container">
      <MwWarning>
        <!-- eslint-disable-next-line max-len -->
        {{ $t('Your passphrase will not be shown again. You will lose access to your wallet without the passphrase.') }}
      </MwWarning>

      <label class="&__agreement">
        <input
          v-model="agreements"
          type="checkbox"
          value="backup"
          class="&__default-checkbox"
        >
        <div class="&__checkbox">
          <TickIcon class="&__check" />
        </div>
        {{ $t('I have written down or otherwise securely stored my passphrase') }}
      </label>

      <label class="&__agreement">
        <input
          v-model="agreements"
          type="checkbox"
          value="terms"
          class="&__default-checkbox"
        >
        <div class="&__checkbox">
          <TickIcon class="&__check" />
        </div>
        <i18n-t
          :keypath.camel="`[template] I agree to the {terms}((Terms of Service))`"
          tag="span"
        >
          <template #terms>
            <a @click.prevent="$safeOpen(`${$account.siteUrl}terms-of-service/`)">
              {{ agreementLinkTexts?.at(0) }}
            </a>
          </template>
        </i18n-t>
      </label>
    </div>

    <MwButtonGroup>
      <MwButton
        type="primary"
        :disabled="agreements.length !== 2"
        @click="next('passphraseConfirmation')"
      >
        {{ $t('Confirm') }}
      </MwButton>
    </MwButtonGroup>
  </AuthStepLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;

    &__passphrase_wrapper {
      display: flex;
      flex-direction: column;
      flex-grow: 1;
      gap: var(--spacing-xs);
    }

    &__passphrase {
      @include text-bold;
    }

    &__copy {
      @include text-xs;
      justify-content: center;
      color: var(--color-secondary);
    }

    &__container {
      display: flex;
      flex-direction: column;
      gap: var(--spacing-md);
    }

    &__warning {
      @include text-sm;
      display: flex;
      align-items: flex-start;
      color: var(--color-danger);
      gap: var(--spacing-xs);
    }

    &__danger_triangle {
      width: var(--spacing-xl);
      flex-shrink: 0;
    }

    &__agreement {
      @include text-sm;
      position: relative;
      display: flex;
      align-items: flex-start;
      cursor: pointer;
      gap: var(--spacing-md);
    }

    &__default-checkbox {
      position: absolute;
      opacity: 0;
      pointer-events: none;
      &:checked ~ #{ $self }__checkbox {
        border: none;
        background-color: var(--color-primary-brand);
        #{ $self }__check {
          display: block;
        }
      }
    }

    &__checkbox {
      display: flex;
      width: var(--spacing-md);
      height: var(--spacing-md);
      flex-shrink: 0;
      align-items: center;
      justify-content: center;
      border: 1px solid var(--color-neutral);
      border-radius: 0.25rem;
      margin-top: var(--spacing-2xs);
    }

    &__check {
      display: none;
      width: var(--spacing-md);
      height: var(--spacing-md);

      [stroke] {
        stroke: var(--color-primary-button-text);
      }
    }
  }
</style>
