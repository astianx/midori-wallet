<script>
import { Amount } from '../../lib/account/domain.js';

import MwAmountInput from '../../components/MwAmountInput.vue';
import MwFormAmountInput from '../../components/MwForm/MwFormAmountInput.vue';
import MwFormBigIntInput from '../../components/MwForm/MwFormBigIntInput.vue';
import MwFormDatepicker from '../../components/MwForm/MwFormDatepicker.vue';
import MwFormDropdown from '../../components/MwForm/MwFormDropdown.vue';
import MwFormFilepicker from '../../components/MwForm/MwFormFilepicker.vue';
import MwFormInput from '../../components/MwForm/MwFormInput.vue';
import MwFormSelect from '../../components/MwForm/MwFormSelect.vue';
import MwFormTextarea from '../../components/MwForm/MwFormTextarea.vue';
import MwFormTextareaReadonly from '../../components/MwForm/MwFormTextareaReadonly.vue';
import MwSwitch from '../../components/MwSwitch.vue';

import EditIcon from '../../assets/svg/edit.svg';
import SearchIcon from '../../assets/svg/search.svg';
import WalletSmallIcon from '../../assets/svg/walletSmall.svg';

import { dataUrlSize } from '../../lib/helpers.js';

export default {
  components: {
    MwAmountInput,
    MwFormAmountInput,
    MwFormBigIntInput,
    MwFormDatepicker,
    MwFormFilepicker,
    MwFormDropdown,
    MwFormInput,
    MwFormSelect,
    MwSwitch,
    MwFormTextarea,
    MwFormTextareaReadonly,
    EditIcon,
    SearchIcon,
    WalletSmallIcon,
  },
  data() {
    return {
      amountValue: new Amount(123000n, 8),
      secondAmountValue: undefined,
      crypto: {
        symbol: 'BTC',
        decimals: 8,
        price: 100000,
        factors: [{
          name: 'BTC',
          decimals: 8,
        }, {
          name: 'mBTC',
          decimals: 5,
        }, {
          name: 'μBTC',
          decimals: 2,
        }],
      },
      decimals: 8,
      price: 25678,
      amount: 1230n,
      factors: [{
        name: 'BTC',
        decimals: 8,
      }, {
        name: 'mBTC',
        decimals: 5,
      }, {
        name: 'μBTC',
        decimals: 2,
      }],
      switchValue: true,
      inputValue: '',
      selectValue: undefined,
      datepickerValue: undefined,
      filepickerValue: undefined,
      options: [{
        name: 'USA',
        value: 'us',
      }, {
        name: 'Russia',
        value: 'ru',
      }, {
        name: 'Pakistan',
        value: 'pk',
      }],
      number: 21000n,
    };
  },
  methods: {
    setAmount(amount, decimals) {
      this.secondAmountValue = amount === undefined ? undefined : new Amount(amount, decimals);
    },
    dataUrlSize,
  },
};
</script>

<template>
  <div class="&">
    <div class="&__group">
      <div>Cs Input</div>

      <MwFormInput
        v-model="inputValue"
        placeholder="Placeholder"
      />

      <MwFormInput
        v-model="inputValue"
        inputmode="numeric"
        placeholder="input mode = numeric"
      />

      <MwFormInput
        v-model="inputValue"
        inputmode="email"
        placeholder="input mode = email"
      />

      <MwFormInput
        v-model="inputValue"
        label="Label"
        placeholder="Placeholder"
      />

      <MwFormInput
        v-model="inputValue"
        label="Error state"
        placeholder="Invalid value"
        error="Error message"
      />

      <MwFormDropdown
        label="Dropdown"
        value="dropdown"
      />

      <MwFormInput
        v-model="inputValue"
        label="Clear X"
        clear
      />

      <MwFormInput
        label="Info button"
        info="note"
      />

      <MwFormInput
        placeholder="Icons"
      >
        <template #before>
          <SearchIcon />
        </template>
        <template #after>
          <SearchIcon />
        </template>
      </MwFormInput>

      <MwFormInput
        small
        placeholder="Small"
      >
        <template #before>
          <SearchIcon />
        </template>
      </MwFormInput>
    </div>

    <div class="&__group">
      <div>Cs Textarea</div>
      <MwFormTextarea />

      <MwFormTextarea label="Textarea" />

      <MwFormTextarea
        label="Error state"
        error="Error message"
      />

      <MwFormTextareaReadonly
        label="Readonly"
        value="some text here readonly some text here readonly"
        info="note"
      />
      <MwFormTextareaReadonly
        label="Readonly + copy"
        value="some text here readonly some text here readonly"
        copy
        info="note"
      />
      <MwFormTextareaReadonly
        label="Readonly + icon"
        value="Your wallet"
        info="note"
      >
        <template #before>
          <WalletSmallIcon />
        </template>
        <template #after>
          <EditIcon />
        </template>
      </MwFormTextareaReadonly>
      <MwFormTextareaReadonly
        label="Readonly (inheritDir)"
        value="some text here readonly some text here readonly"
        info="note"
        inheritDir
      />
      <MwFormTextareaReadonly
        label="Readonly + icon (inheritDir)"
        value="You wallet"
        info="note"
        inheritDir
      >
        <template #before>
          <WalletSmallIcon />
        </template>
        <template #after>
          <EditIcon />
        </template>
      </MwFormTextareaReadonly>
    </div>

    <div class="&__group">
      <div>
        <div>Base Amount Input</div>
        <div>Amount Value "{{ amountValue }}" {{ typeof amountValue }}</div>
        <div>Decimals = {{ decimals }}</div>
      </div>
      <MwAmountInput
        v-model="amountValue"
        :decimals="decimals"
      />
    </div>

    <div class="&__group">
      <div>
        <div>Cs Amount Input</div>
        <div>Amount Value "{{ amountValue }}" {{ typeof amountValue }}</div>
        <div>Decimals = {{ decimals }}</div>
      </div>
      <MwFormAmountInput
        v-model="amountValue"
        label="Amount"
        :decimals="decimals"
        symbol="BTC"
        :price="price"
        currency="USD"
      />
      <MwFormAmountInput
        v-model="amountValue"
        label="Amount without select"
        :decimals="decimals"
        symbol="BTC"
      />
      <MwFormAmountInput
        v-model="secondAmountValue"
        label="Amount (set amount / crypto)"
        :decimals="crypto.decimals"
        :symbol="crypto.symbol"
        :price="crypto.price"
        :factors="crypto.factors ? crypto.factors : []"
        currency="USD"
      />
      <a @click="setAmount(1005000n, crypto.decimals)">Set amount (100500)</a>
      <a @click="setAmount(0n, crypto.decimals)">Set amount (0)</a>
      <a @click="setAmount(undefined, crypto.decimals)">Set amount (undefined)</a>
      <a @click="crypto = { symbol: 'XMR', decimals: 12, price: 2000 }">Set crypto (XMR)</a>
      <a
        @click="crypto = {
          symbol: 'BTC',
          decimals: 8,
          price: 100000,
          factors: [{
            name: 'BTC',
            decimals: 8,
          }, {
            name: 'mBTC',
            decimals: 5,
          }, {
            name: 'μBTC',
            decimals: 2,
          }]}"
      >Set crypto (BTC)</a>
    </div>

    <div class="&__group">
      <div>Switch Value: "{{ switchValue }}" {{ typeof switchValue }}</div>
      <MwSwitch
        :checked="switchValue"
        @click="switchValue = !switchValue"
      />
    </div>

    <div class="&__group">
      <div>Select Value: "{{ selectValue }}"</div>
      <MwFormSelect
        v-model="selectValue"
        label="Select"
        :options="options"
      />
    </div>

    <div class="&__group">
      <div>Datepicker Value: "{{ datepickerValue }}"</div>
      <MwFormDatepicker
        v-model="datepickerValue"
        label="Date of birth"
      />
    </div>

    <div class="&__group">
      <div>
        Filepicker Value: "
        {{ filepickerValue?.dataUrl?.substring(0, 32) }},
        {{ dataUrlSize(filepickerValue?.dataUrl) }} bytes"
      </div>
      <MwFormFilepicker
        v-model="filepickerValue"
        label="Document"
        placeholder="Select file..."
        capture
        :clear="true"
      />
    </div>

    <div class="&__group">
      <div>
        Gas limit: {{ number }}
      </div>
      <MwFormBigIntInput
        v-model="number"
        label="Gas limit"
        info="Gas limit"
      />
    </div>
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    display: flex;
    flex-direction: column;
    gap: var(--spacing-4xl);

    &__group {
      display: flex;
      flex-direction: column;
      gap: var(--spacing-xl);
    }

    &__btn {
      max-width: 20rem;
    }
  }
</style>
