<script>
import { onShowOnHide } from '../../../lib/mixins.js';
import { formatMidoriTag, normalizeTagAlias } from '../../../lib/tags.js';
import MwButton from '../../../components/MwButton.vue';
import MwButtonGroup from '../../../components/MwButtonGroup.vue';
import MwFormGroup from '../../../components/MwForm/MwFormGroup.vue';
import MwFormTextareaReadonly from '../../../components/MwForm/MwFormTextareaReadonly.vue';
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
    MwFormTextareaReadonly,
  },
  extends: MwStep,
  mixins: [onShowOnHide],
  onShow() { this.active = true; },
  onHide() { this.active = false; },
  data() {
    return {
      active: false,
      isLoading: false,
      registeredTag: formatMidoriTag(this.$account.tag?.alias),
      tagLocked: !!this.$account.tag,
      username: formatMidoriTag(this.$account.tag?.alias || this.$user.username),
      email: this.$user.email,
      errors: {},
    };
  },
  computed: {
    canCreateTag() { return !this.tagLocked && this.$account.tag !== undefined && this.$account.capabilities.tags === true && this.$account.connection === 'online'; },
  },
  methods: {
    async save() {
      if (this.isLoading) return;
      if (this.canCreateTag && this.username && !isValidUsername(this.username)) {
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
        let username = normalizeTagAlias(this.registeredTag || this.$account.user.username || '');
        if (this.canCreateTag && this.username) {
          username = await this.$account.updateUsername(this.username);
          this.registeredTag = formatMidoriTag(username); this.tagLocked = true;
        }
        this.username = formatMidoriTag(username);
        this.$account.emit('update', 'user');
        this.$account.details.set('userInfo', {
          username,
          email: this.email,
        });
        await this.$account.details.save();
        this.$account.emit('update', 'user');
        if (this.active) this.$router.up();
      } catch (err) {
        if (err.code === 'TAG_IMMUTABLE') {
          this.tagLocked = true; this.registeredTag = formatMidoriTag(this.$account.tag?.alias);
          this.errors.username = this.$t('This tag is already registered and cannot be changed.');
          this.$account.emit('update', 'user'); return;
        }
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
    :title="$t('Profile')"
  >
    <MwFormGroup class="&__content">
      <MwFormTextareaReadonly v-if="!canCreateTag" :value="registeredTag || '—'" :label="$t('Your Midori tag')" :copy="!!registeredTag" />
      <MwFormInput v-else
        v-model="username"
        :error="errors.username"
        :label="$t('Your Midori tag')"
        placeholder="$usuario"
        :clear="true"
        @update:modelValue="errors.username = undefined"
      />
      <p class="&__note">{{ $t('Choose carefully. Your tag can only be registered once and cannot be changed.') }}</p>
      <p v-if="errors.username && !canCreateTag" role="alert">{{ errors.username }}</p>
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
        <a href="https://gravatar.com/" @click.prevent="$safeOpen('https://gravatar.com/')">{{ $t('Create Gravatar.') }}</a>
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
        type="primary-link"
        @click="$router.push({name: 'settings.advanced'})"
      >
        {{ $t('Advanced tools') }}
      </MwButton>
      <MwButton type="secondary" :disabled="isLoading" @click="$router.up()">{{ $t('Cancel') }}</MwButton>
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
