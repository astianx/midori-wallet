import { reactive } from 'vue';
import { paymentRecord, orderRecord } from './activity-presentation.js';

const readers = new WeakMap();
export function activityReader(account) {
  if (readers.has(account)) return readers.get(account);
  const state = reactive({ sources: account.wallets().map(wallet => ({ id: wallet.crypto._id, cursor: undefined, hasMore: true, paged: false, records: [], error: false, indexing: null })), orders: [], ordersError: false, ordersLoaded: false, loading: false, loaded: false, updatedAt: null });
  let generation = 0, request;
  const valid = value => value === generation && !account.isLocked;
  const presentPayment = async (transaction, wallet) => {
    // History deliberately groups some intent states as pending. Use its existing
    // intent reference to explain uncertainty, never to override a reorganisation.
    if (transaction.chainStatus === 'pending' && transaction.intentId) {
      try { const intent = await wallet.payment(transaction.intentId); if (intent.status === 'ambiguous') transaction = { ...transaction, chainStatus: 'ambiguous' }; } catch { /* Pending remains uncertain when the extra reading is unavailable. */ }
    }
    return paymentRecord(transaction, wallet);
  };
  const merge = (old, next) => [...new Map([...old, ...next].map(item => [item.id, item])).values()];
  const api = {
    state,
    records() { return [...state.sources.flatMap(source => source.records), ...state.orders].sort((a, b) => b.timestamp - a.timestamp || a.id.localeCompare(b.id)); },
    async load(refresh = true) {
      if (account.isLocked) return;
      if (request) return request;
      const current = generation;
      state.loading = true;
      request = (async () => {
        await Promise.all(state.sources.filter(source => refresh || source.hasMore || source.error).map(async source => {
          try {
            const wallet = account.wallet(source.id);
            const page = await wallet.loadTransactions({ cursor: refresh ? undefined : source.cursor });
            if (!valid(current)) return;
            const incoming = await Promise.all(page.transactions.map(tx => presentPayment(tx, wallet)));
            if (!valid(current)) return;
            source.records = merge(source.records, incoming);
            if (!refresh || !source.paged) { source.cursor = page.cursor; source.hasMore = Boolean(page.hasMore); }
            source.paged ||= !refresh;
            source.indexing = page.indexing;
            source.error = false;
            if (refresh) {
              const currentIds = new Set(incoming.map(item => item.id));
              await Promise.all(source.records.filter(item => !currentIds.has(item.id) && !['confirmed', 'reverted', 'expired', 'cancelled', 'rejected'].includes(item.state)).map(async item => {
                try { await api.detail('payment', source.id, item.reference); } catch { if (valid(current)) source.error = true; }
              }));
            }
          } catch (error) {
            if (!valid(current)) return;
            source.error = true;
            if (error.code === 'HISTORY_REFRESH_REQUIRED') { source.cursor = undefined; source.paged = false; source.hasMore = true; }
          }
        }));
        if (refresh && account.capabilities.ramp && state.sources[0]) {
          try {
            const result = await account.wallet(state.sources[0].id).rampOrders();
            if (valid(current)) {
              const incoming = result.orders.map(orderRecord), currentIds = new Set(incoming.map(item => item.id));
              state.orders = merge(state.orders, incoming); state.ordersError = false; state.ordersLoaded = true;
              await Promise.all(state.orders.filter(item => !currentIds.has(item.id) && !['completed', 'failed', 'refunded', 'expired'].includes(item.state)).map(async item => {
                try { await api.detail('ramp', undefined, item.reference); } catch { if (valid(current)) state.ordersError = true; }
              }));
            }
          } catch { if (valid(current)) state.ordersError = true; }
        }
        if (valid(current)) { state.loaded = true; if (!state.ordersError && state.sources.every(source => !source.error)) state.updatedAt = new Date(); }
      })().finally(() => { if (current === generation) { state.loading = false; request = undefined; } });
      return request;
    },
    async detail(source, walletId, reference) {
      const current = generation;
      if (!['payment', 'ramp'].includes(source) || typeof reference !== 'string') throw Error('Invalid reference');
      let record;
      if (source === 'ramp' && /^[0-9a-f-]{36}$/.test(reference)) {
        const wallet = account.wallet(state.sources[0]?.id);
        if (!wallet) throw Error('Unavailable');
        record = orderRecord(await wallet.rampOrder(reference));
      } else if (source === 'payment' && /^(?:e[1-9][0-9]*|p[0-9a-f-]{36})$/.test(reference)) {
        const wallet = account.wallet(walletId);
        if (!wallet) throw Error('Invalid asset');
        const transaction = await wallet.loadTransaction(reference);
        if (transaction.contract?.toLowerCase() !== wallet.crypto.address.toLowerCase()) throw Error('Different asset');
        record = await presentPayment(transaction, wallet);
      } else throw Error('Invalid reference');
      if (!valid(current)) throw Error('Locked');
      if (source === 'ramp') state.orders = merge(state.orders, [record]);
      else { const target = state.sources.find(item => item.id === walletId); target.records = merge(target.records, [record]); }
      return record;
    },
  };
  // No receipt or history survives locking, logging out or an in-flight account change.
  account.once('locked', () => { generation++; readers.delete(account); state.sources.forEach(source => { source.records = []; }); state.orders = []; state.loaded = false; state.loading = false; });
  readers.set(account, api);
  return api;
}
