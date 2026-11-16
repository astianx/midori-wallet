import fs from 'node:fs';
import { pathToFileURL } from 'node:url';

export function buildPlan({ event, ref, requested = false }) {
  const manual = event === 'workflow_dispatch' && (requested === true || requested === 'true');
  if (manual && ref !== 'refs/heads/main') throw new Error('Signed Android builds require main. Select main in Run workflow.');
  const signedAndroid = ref === 'refs/heads/main' && (event === 'push' || manual);
  return {
    signedAndroid,
    reason: signedAndroid
      ? 'Android release requested on main; release-signing approval is required when configured.'
      : 'Validation builds only. Android signing runs on pushes to main or manual requests on main.',
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const plan = buildPlan({ event: process.env.GITHUB_EVENT_NAME, ref: process.env.GITHUB_REF, requested: process.env.ANDROID_RELEASE_REQUESTED });
  if (process.env.GITHUB_OUTPUT) fs.appendFileSync(process.env.GITHUB_OUTPUT, `android-release=${plan.signedAndroid}\n`);
  if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, `## Build policy\n\n${plan.reason}\n\nValidation: web, desktop, Android debug and iOS Simulator. Signed Android output: APK and AAB.\n`);
  console.log(plan.reason);
}
