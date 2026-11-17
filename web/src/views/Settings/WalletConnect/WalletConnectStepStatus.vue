<script>
import MainLayout from '../../../layouts/MainLayout.vue';
import MwButton from '../../../components/MwButton.vue';
import MwAppIcon from '../../../components/MwAppIcon.vue';
import MwStep from '../../../components/MwStep.vue';
import MwTransactionStatus from '../../../components/MwTransactionStatus.vue';

const SIGN_REQUESTS = [
  'eth_signTypedData',
  'eth_signTypedData_v4',
  'eth_sign',
  'personal_sign',
];

export default {
  components: {
    MwTransactionStatus, MainLayout, MwButton, MwAppIcon,
  },
  extends: MwStep,
  computed: {
    signature() { return SIGN_REQUESTS.includes(this.storage.method); },
    paymentTitle() {
      const state = this.storage.intent?.status;
      return this.$t(state === 'confirmed' ? 'Payment sent' : state === 'reverted' ? 'Payment reverted' : ['expired', 'cancelled', 'rejected'].includes(state) ? 'Payment not sent' : 'Confirming payment');
    },
    paymentMessage() {
      const state = this.storage.intent?.status;
      return this.$t(state === 'confirmed' ? 'The recipient received the approved amount.' : state === 'reverted' ? 'The recipient did not receive this payment. The network may have charged a cost.' : ['expired', 'cancelled', 'rejected'].includes(state) ? 'You can review a new payment.' : 'We are checking this payment. Do not send it again.');
    },
    title() {
      if (SIGN_REQUESTS.includes(this.storage.method)) {
        return this.$t('Signature request');
      }
    },
    header() {
      if (SIGN_REQUESTS.includes(this.storage.method)) {
        if (this.storage.status) {
          return this.$t('Signing successful');
        } else {
          return this.$t('Signing failed');
        }
      }
    },
    message() {
      if (SIGN_REQUESTS.includes(this.storage.method)) {
        if (this.storage.status) {
          return this.$t('Message has been signed.');
        } else {
          return this.$t('Please try again later.');
        }
      }
    },
  },
  methods: {
    done() {
      this.backTo('main');
    },
  },
};
</script>

<template>
  <MwTransactionStatus
    v-if="signature"
    :transaction="storage.transaction"
    :status="storage.status"
    :title="title"
    :header="header"
    :message="message"
    @done="done"
  />
  <MainLayout v-else :title="paymentTitle" @back="done">
    <MwAppIcon :name="storage.intent?.status === 'confirmed' ? 'check' : 'clock'" aria-hidden="true" />
    <h1>{{ paymentTitle }}</h1>
    <p role="status">{{ paymentMessage }}</p>
    <MwButton type="primary" @click="$router.push({ name: 'activity' })">{{ $t('Activity') }}</MwButton>
    <MwButton type="secondary" @click="done">{{ $t('Done') }}</MwButton>
  </MainLayout>
</template>
