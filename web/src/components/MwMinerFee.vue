<script>
import MwButton from '../components/MwButton.vue';
import MwFormGroup from '../components/MwForm/MwFormGroup.vue';
import MwFormSelect from '../components/MwForm/MwFormSelect.vue';

import * as BitcoinSymbols from '../lib/ui-runtime/bitcoin-symbols.js';
import * as KaspaSymbols from '../lib/ui-runtime/kaspa-symbols.js';
import * as MoneroSymbols from '../lib/ui-runtime/monero-symbols.js';
import { WalletState } from '../lib/account/domain.js';

export default {
  components: {
    MwButton,
    MwFormGroup,
    MwFormSelect,
  },
  props: {
    feeRates: {
      type: Array,
      default() {
        return [];
      },
    },
  },
  emits: ['confirm'],
  data() {
    const options = this.feeRates.map((feeRate) => {
      if (feeRate === WalletState.FEE_RATE_DEFAULT) {
        let time;
        if (this.$wallet.crypto._id === 'monero@monero') {
          time = (10 * this.$wallet.blocktime / 60).toFixed();
        } else if (this.$wallet.crypto._id === 'kaspa@kaspa') {
          time = '1';
        } else {
          time = (6 * this.$wallet.blocktime / 60).toFixed();
        }
        return {
          name: `${this.$t('default')} (${this.$t('~{time} min', { time })})`,
          value: 'default',
          feeRate,
        };
      }
      if (feeRate === BitcoinSymbols.FEE_RATE_FASTEST
        || feeRate === MoneroSymbols.FEE_RATE_FASTEST
        || feeRate === KaspaSymbols.FEE_RATE_FASTEST
      ) {
        let time;
        if (this.$wallet.crypto._id === 'monero@monero') {
          time = (5 * this.$wallet.blocktime / 60).toFixed();
        } else if (this.$wallet.crypto._id === 'kaspa@kaspa') {
          time = '0';
        } else {
          time = (2 * this.$wallet.blocktime / 60).toFixed();
        }
        return {
          name: `${this.$t('fast')} (${this.$t('~{time} min', { time })})`,
          value: 'fast',
          feeRate,
        };
      }
      if (feeRate === BitcoinSymbols.FEE_RATE_MINIMUM
        || feeRate === KaspaSymbols.FEE_RATE_MINIMUM
      ) {
        let time;
        if (this.$wallet.crypto._id === 'kaspa@kaspa') {
          time = '60';
        } else {
          time = (12 * this.$wallet.blocktime / 60).toFixed();
        }
        return {
          name: `${this.$t('slow')} (${this.$t('~{time} min', { time })})`,
          value: 'minimum',
          feeRate,
        };
      }
    });
    return {
      value: 'default',
      options,
    };
  },
  methods: {
    confirm() {
      this.$emit('confirm', this.options.find((item) => item.value === this.value).feeRate);
    },
  },
};
</script>

<template>
  <MwFormGroup class="&">
    <MwFormSelect
      v-model="value"
      :options="options"
      :label="$t('Transaction speed')"
    />
    <div class="&__info">
      {{ $t('The higher the speed, the faster transaction is processed by the network.') }}
    </div>
  </MwFormGroup>
  <MwButton
    type="primary"
    @click="confirm"
  >
    {{ $t('Continue') }}
  </MwButton>
</template>

<style lang="scss">
  .#{ $filename } {
    $self: &;
    flex-grow: 1;

    &__info {
      @include text-md;
    }
  }
</style>
