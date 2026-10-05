const fs = require('node:fs')
const path = require('node:path')

const file = path.join(
  __dirname,
  'node_modules/@tetherto/wdk-worklet-bundler/dist/bundler-CiYCmOI9.js'
)
const original = 'if (/[^.\\w]import\\s*\\(/.test(content)) problems.push(`${key} still contains a dynamic import()`);'
const patched = 'if (/[^.\\w]import\\s*\\(/.test(content.replace(/\\basync\\s+import\\s*\\(/g, "async namedMethod("))) problems.push(`${key} still contains a dynamic import()`);'

let source = fs.readFileSync(file, 'utf8')
if (!source.includes(patched) && !source.includes(original)) {
  throw new Error('Bundler validation code changed; review the local workaround')
}
if (source.includes(original)) {
  source = source.replace(original, patched)
  console.log('Patched bundler validation for bare-module async import() method')
}

fs.writeFileSync(file, source)

// The wallet worklet does not use MessageChannel/BroadcastChannel globals.
// Loading bare-worker/global in bare-node-runtime 1.5.1 aborts BareKit on
// Android during startup, before any wallet call can be handled.
const runtimeGlobal = path.join(__dirname, 'node_modules/bare-node-runtime/global.js')
const runtimeSource = fs.readFileSync(runtimeGlobal, 'utf8')
const workerGlobal = "require('bare-worker/global')\n"
if (runtimeSource.includes(workerGlobal)) {
  fs.writeFileSync(runtimeGlobal, runtimeSource.replace(workerGlobal, ''))
  console.log('Excluded unused bare-worker globals from wallet worklet')
}
