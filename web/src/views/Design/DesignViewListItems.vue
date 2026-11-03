<script>
import MwButton from '../../components/MwButton.vue';
import MwListItem from '../../components/MwListItem.vue';
import MwListItemDropdown from '../../components/MwListItemDropdown.vue';
import MwListItems from '../../components/MwListItems.vue';
import MwSwitch from '../../components/MwSwitch.vue';

import DeleteIcon from '../../assets/svg/delete.svg';
import ImportIcon from '../../assets/svg/import.svg';

export default {
  components: {
    MwButton,
    MwListItem,
    MwListItemDropdown,
    MwListItems,
    DeleteIcon,
    ImportIcon,
    MwSwitch,
  },
  data() {
    return {
      dropdownOptions: [
        { name: 'RUB', value: 'RUB' },
        { name: 'EUR', value: 'EUR' },
        { name: 'USD', value: 'USD' },
      ],
      dropdownValue: 'USD',
      switchValue: true,
    };
  },
};
</script>

<template>
  <div class="&">
    <MwListItems>
      <MwListItem title="Item #1" />
      <MwListItem title="Item #2" />
    </MwListItems>

    <MwListItems>
      <MwListItem
        title="Logout"
        type="danger"
        :arrow="false"
      />
    </MwListItems>

    <MwListItems title="With title">
      <MwListItem title="Item #1" />
      <MwListItem title="Item #2" />
    </MwListItems>

    <MwListItems title="With Description">
      <MwListItem
        title="Item #1"
        description="Description #1"
      />
      <MwListItem
        title="Item #2"
        description="Description #Long. Lorem, ipsum dolor sit amet consectetur adipisicing elit. Alias at libero?"
      />
    </MwListItems>

    <MwListItems title="Icon after">
      <MwListItem title="Item #1">
        <template #after>
          <MwButton>
            <DeleteIcon />
          </MwButton>
        </template>
      </MwListItem>
    </MwListItems>

    <MwListItems title="Icon before">
      <MwListItem title="Item #1">
        <template #before>
          <ImportIcon />
        </template>
      </MwListItem>
    </MwListItems>

    <MwListItems title="With dropdown">
      <MwListItem title="Local currency">
        <template #after>
          <MwListItemDropdown
            v-model="dropdownValue"
            :options="dropdownOptions"
          />
        </template>
      </MwListItem>
    </MwListItems>

    <MwListItems title="With switch">
      <MwListItem
        title="High Security"
        description="Send coins, export private keys, remove account, change securuty settings"
      >
        <template #after>
          <MwSwitch v-model="switchValue" />
        </template>
      </MwListItem>
    </MwListItems>
  </div>
</template>

<style lang="scss">
  .#{ $filename } {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 3.5rem;

    .mw-list-items {
      width: 100%;
      max-width: 35rem;
    }
  }
</style>
