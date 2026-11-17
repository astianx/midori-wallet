<script>
const paths = {
  home: 'M3 10 12 3l9 7v10a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z',
  activity: 'M4 5h16M4 12h10M4 19h7m6-5 4 3-4 3m4-3h-7',
  receive: 'M12 3v12m-5-5 5 5 5-5M4 16v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4',
  send: 'M12 16V4m-5 5 5-5 5 5M4 15v5a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-5',
  user: 'M20 21v-2a6 6 0 0 0-6-6h-4a6 6 0 0 0-6 6v2M12 9a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
  arrow: 'M5 12h14m-6-6 6 6-6 6',
  plus: 'M12 5v14M5 12h14',
  eye: 'M2 12s3-7 10-7 10 7 10 7-3 7-10 7S2 12 2 12Zm10 3a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
  hide: 'm3 3 18 18M10 5.2c.6-.1 1.3-.2 2-.2 7 0 10 7 10 7a17 17 0 0 1-4 4.5M6.3 6.3A18 18 0 0 0 2 12s3 7 10 7c1.6 0 3-.4 4.2-.9',
  clock: 'M12 7v5l3 2m6-2a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
  check: 'm5 12 4 4L19 6',
  help: 'M9.1 9a3 3 0 0 1 5.8 1c0 2-3 2-3 4m.1 3h.01M21 12a9 9 0 1 1-18 0 9 9 0 0 1 18 0Z',
  refresh: 'M20 7v5h-5M4 17v-5h5m-4-3a7.5 7.5 0 0 1 12.4-3L20 9M4 15l2.6 3A7.5 7.5 0 0 0 19 15',
};
export default { props: { name: { type: String, default: 'arrow' } }, computed: { path() { return paths[this.name] || paths.arrow; } } };
</script>
<template>
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.65" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path :d="path" /></svg>
</template>
