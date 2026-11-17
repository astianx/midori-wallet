import { changellyClient } from '../lib/changelly.js';
const client = changellyClient({ changellyApiSecret: process.env.CHANGELLY_API_SECRET, changellyApiKey: process.env.CHANGELLY_API_KEY });
try {
  const currencies = await client.currencies();
  const source = currencies.find(item => item.contract === '0x833589fcd6edb6e08f4c7c32d4f71b54bda02913' && /^base$/i.test(item.network) && item.sell);
  const target = currencies.find(item => /usdt/i.test(item.ticker) && /^ethereum$/i.test(item.network) && item.buy);
  console.log(JSON.stringify({ currencies: currencies.length, source: source?.ticker || null, destination: target?.ticker || null, network: target?.network || null }));
  if (source && target) console.log(JSON.stringify({ estimate: await client.estimate(source.ticker, target.ticker, '100') }));
  if (!source) process.exitCode = 1;
} catch (error) { console.error(JSON.stringify({ code: error.code || 'CONFIGURATION_ERROR', message: 'Check Changelly credentials, API access and network connectivity' })); process.exitCode = 1; }
