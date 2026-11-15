<script>
import { savePaymentDraft, takePaymentDraft } from '../../../lib/navigation.js';
import { Amount } from '../../../lib/account/domain.js';

import MwPinStep from '../../../components/MwPinStep.vue';
import MwSteps from '../../../components/MwSteps.vue';

import CryptoSendStepAddress from './CryptoSendStepAddress.vue';
import CryptoSendStepAmount from './CryptoSendStepAmount.vue';
import CryptoSendStepConfirm from './CryptoSendStepConfirm.vue';
import CryptoSendStepGas from './CryptoSendStepGas.vue';
import CryptoSendStepMecto from './CryptoSendStepMecto.vue';
import CryptoSendStepMeta from './CryptoSendStepMeta.vue';
import CryptoSendStepMinerFee from './CryptoSendStepMinerFee.vue';
import CryptoSendStepPoor from './CryptoSendStepPoor.vue';
import CryptoSendStepQr from './CryptoSendStepQr.vue';
import CryptoSendStepStatus from './CryptoSendStepStatus.vue';

export default {
  components: {
    MwSteps,
  },
  data() {
    const owner = this.$wallet.address;
    const path = this.$route.fullPath;
    const draft = takePaymentDraft(owner, path);
    const temp = this.parseQuery(this.$route);
    if (draft) {
      temp.address = draft.address;
      if (draft.amount) temp.amount = Amount.fromString(draft.amount, this.$wallet.crypto.decimals);
    }
    return { owner, draftPath: path, storage: { temp } };
  },
  beforeUnmount() {
    const steps = this.$refs.steps;
    const active = steps?.$refs.activeStep;
    if (!steps || !this.$account.isCreated || ['confirmed','reverted','expired'].includes(steps.storage.intent?.status)) return;
    const address = typeof active?.address === 'string' ? active.address : steps.storage.alias || steps.storage.address || steps.storage.temp?.address || '';
    const amount = active?.amountValue ?? steps.storage.amount ?? steps.storage.temp?.amount;
    savePaymentDraft(this.owner, this.draftPath, { address, amount: amount?.toString() });
  },
  steps: {
    index: CryptoSendStepAddress,
    meta: CryptoSendStepMeta,
    fee: CryptoSendStepMinerFee,
    gas: CryptoSendStepGas,
    amount: CryptoSendStepAmount,
    confirm: CryptoSendStepConfirm,
    status: CryptoSendStepStatus,
    mecto: CryptoSendStepMecto,
    pin: MwPinStep,
    qr: CryptoSendStepQr,
    poor: CryptoSendStepPoor,
  },
  methods: {
    parseQuery(route) {
      const temp = {};
      if (route.query.address) {
        temp.address = route.query.address;
      }
      if (route.query.amount) {
        try {
          temp.amount = Amount.fromString(route.query.amount, this.$wallet.crypto.decimals);
        } catch (err) {
          console.error('Midori Wallet operation failed');
        }
      }
      if (route.query.destinationTag && this.$wallet.crypto._id === 'xrp@ripple') {
        temp.meta = {
          destinationTag: route.query.destinationTag,
        };
      }
      return temp;
    },
  },
};
</script>

<template>
  <MwSteps
    ref="steps"
    :steps="$options.steps"
    :initialStorage="storage"
  />
</template>
