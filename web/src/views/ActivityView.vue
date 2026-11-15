<script>
import MainLayout from '../layouts/MainLayout.vue';
import MwActivityList from '../components/MwActivityList.vue';
export default { components: { MainLayout, MwActivityList } };
</script>
<template><MainLayout :title="$t('Activity')" wide allowReadFailure><MwActivityList filters /></MainLayout></template>
