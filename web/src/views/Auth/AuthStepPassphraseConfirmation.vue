<script>
import AuthStepLayout from '../../layouts/AuthStepLayout.vue';
import MwButton from '../../components/MwButton.vue';
import MwButtonGroup from '../../components/MwButtonGroup.vue';
import MwFormGroup from '../../components/MwForm/MwFormGroup.vue';
import MwFormInput from '../../components/MwForm/MwFormInput.vue';
import MwStep from '../../components/MwStep.vue';

import { getRandomInt } from '../../lib/helpers.js';
import { verifyWords } from '../../lib/account/recovery.js';

export default {
  components: {
    MwButton,
    AuthStepLayout,
    MwButtonGroup,
    MwFormInput,
    MwFormGroup,
  },
  extends: MwStep,
  data() {
    return {
      words: [],
      firstIndex: undefined,
      secondIndex: undefined,
      firstWord: '',
      secondWord: '',
      firstError: undefined,
      secondError: undefined,
    };
  },
  mounted() {

    this.firstIndex = getRandomInt(12);
    do {
      this.secondIndex = getRandomInt(12);
    } while (this.firstIndex === this.secondIndex);
  },
  methods: {
    confirm() {
      const valid = verifyWords(this.storage.recoveryHandle, [{ index: this.firstIndex, word: this.firstWord }, { index: this.secondIndex, word: this.secondWord }]);
      this.firstError = valid ? undefined : this.$t('Invalid word');
      this.secondError = this.firstError;
      if (!valid) return;
      this.firstWord = ''; this.secondWord = '';
      this.next('backup');
    },
  },
};
</script>

<template>
  <AuthStepLayout :title="$t('Confirm passphrase')">
    <div class="&__description">
      {{ $t('Almost done! Enter the following words from your passphrase.') }}
    </div>
    <MwFormGroup class="&__words">
      <MwFormInput
        v-model="firstWord"
        :label="$t('Word #{index}', { index: firstIndex + 1 })"
        :error="firstError"
        clear
        @update:modelValue="firstError = undefined"
      />
      <MwFormInput
        v-model="secondWord"
        :label="$t('Word #{index}', { index: secondIndex + 1 })"
        :error="secondError"
        clear
        @update:modelValue="secondError = undefined"
      />
    </MwFormGroup>
    <MwButtonGroup>
      <MwButton
        type="primary"
        @click="confirm"
      >
        {{ $t('Confirm') }}
      </MwButton>
      <MwButton
        type="primary-link"
        @click="back"
      >
        {{ $t('View passphrase again') }}
      </MwButton>
    </MwButtonGroup>
  </AuthStepLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    &__description {
      @include text-md;
    }

    &__words {
      flex-grow: 1;
    }
  }
</style>
