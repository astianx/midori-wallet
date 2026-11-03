<script>
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormInput from '../../../components/MwForm/MwFormInput.vue';
import MwStep from '../../../components/MwStep.vue';
import MainLayout from '../../../layouts/MainLayout.vue';
import {
  isValidEmail,
  isValidUsername,
} from '../../../lib/helpers.js';

import { RequestError } from '../../../lib/ui-runtime/vendor/errors.js';

export default {
  components: {
    MainLayout,
    MwButton,
    MwButtonGroup,
    MwFormGroup,
    MwFormInput,
  },
  extends: MwStep,
  data() {
    return {
      isLoading: false,
      username: this.$user.username,
      email: this.$user.email,
      errors: {},
    };
  },
  methods: {
    async save() {
      if (this.username && !isValidUsername(this.username)) {
        this.errors.username = this.$t('Invalid username');
        return;
      }
      if (this.email && !isValidEmail(this.email)) {
        this.errors.email = this.$t('Invalid email');
        return;
      }
      this.isLoading = true;
      this.errors = {};
      try {
        if (this.username) this.username = await this.$account.updateUsername(this.username);
        else this.username = this.$account.user.username || '';
        this.$account.emit('update', 'user');
        this.$account.details.set('userInfo', {
          username: this.username,
          email: this.email,
        });
        await this.$account.details.save();
        this.$account.emit('update', 'user');
        this.$router.up();
      } catch (err) {
        if (err.code === 'TAG_TAKEN' || (err instanceof RequestError && err.status === 409)) {
          this.errors.username = this.$t('Username already taken');
          return;
        }
        this.errors.username = err.status === 400 ? this.$t('Invalid or reserved tag') : this.$account.unknownError(err);
      } finally {
        this.isLoading = false;
      }
    },
  },
};
</script>

<template>
  <MainLayout
    :title="$t('Account')"
  >
    <MwFormGroup class="&__content">
      <MwFormInput
        v-model="username"
        :error="errors.username"
        :label="$t('Your Midori tag')"
        placeholder="@usuario"
        :clear="true"
        @update:modelValue="errors.username = undefined"
      />
      <div class="&__note">{{ $t('Your username is your Midori tag. Share it to receive USDT and USDC on Base.') }}</div>
      <MwFormInput
        v-model="email"
        :error="errors.email"
        :label="$t('Gravatar email')"
        :clear="true"
        @update:modelValue="errors.email = undefined"
      />
      <div class="&__note">
        <!-- eslint-disable-next-line max-len -->
        {{ $t('Gravatar (globally recognised avatar) is a service that lets you re-use the same avatar across websites and apps by specifying an email address.') }}
        <a @click="$safeOpen('https://gravatar.com/')">{{ $t('Create Gravatar.') }}</a>
      </div>
    </MwFormGroup>
    <MwButtonGroup>
      <MwButton
        type="primary"
        :isLoading="isLoading"
        @click="save"
      >
        {{ $t('Save') }}
      </MwButton>
      <MwButton
        type="danger-link"
        @click="next('remove')"
      >
        {{ $t('Remove account') }}
      </MwButton>
    </MwButtonGroup>
  </MainLayout>
</template>

<style lang="scss">
  .#{ $filename } {
    &__content {
      flex-grow: 1;
    }

    &__note {
      @include text-md;
    }
  }
</style>
