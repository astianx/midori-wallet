import assert from 'node:assert/strict';
import { runOfflineCompatibility } from '@midori/wallet-core/testing';
const report = await runOfflineCompatibility();
assert.equal(report.passed,true);
assert.equal(report.networkRequests,0);
console.log(`WDK F1: ${Object.keys(report.checks).length} offline account/recovery/vault checks passed; zero RPC requests.`);
