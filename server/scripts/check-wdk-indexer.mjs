import { loadWdkIndexerConfig, inspectWdkIndexer } from '../lib/wdk-indexer.js';

try {
  const report = await inspectWdkIndexer(loadWdkIndexerConfig());
  console.log(JSON.stringify({ event: 'wdk_indexer_checked', ...report }, null, 2));
  process.exitCode = !report.configured ? 1 : report.coverageComplete ? 0 : 2;
} catch (error) {
  console.error(JSON.stringify({ event: 'wdk_indexer_check_failed', code: error.code || 'WDK_INDEXER_CONFIG_INVALID', ...(error.details ? { details: error.details } : {}) }));
  process.exitCode = 1;
}
