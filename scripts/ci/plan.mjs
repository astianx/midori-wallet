import fs from 'node:fs';
import { pathToFileURL } from 'node:url';

// Matches update-core's release tag pattern (vX.Y.Z or vX.Y.Z-beta.N), anchored to a tag ref.
const releaseTagPattern = /^refs\/tags\/v(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})\.(0|[1-9][0-9]{0,8})(?:-beta\.(0|[1-9][0-9]{0,8}))?$/;

export function buildPlan({ event, ref, requested = false }) {
  const manual = event === 'workflow_dispatch' && (requested === true || requested === 'true');
  if (manual && ref !== 'refs/heads/main') throw new Error('Signed Android builds require main. Select main in Run workflow.');
  const releaseTag = event === 'push' && releaseTagPattern.test(ref || '');
  const signedAndroid = (ref === 'refs/heads/main' && (event === 'push' || manual)) || releaseTag;
  return {
    signedAndroid,
    releaseTag,
    reason: releaseTag
      ? `Release tag ${ref} pushed; building signed Android and release-profile desktop artifacts for a draft GitHub release.`
      : signedAndroid
        ? 'Android release requested on main; release-signing approval is required when configured.'
        : 'Validation builds only. Android signing runs on pushes to main, on version tags, or on manual requests on main.',
  };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const plan = buildPlan({ event: process.env.GITHUB_EVENT_NAME, ref: process.env.GITHUB_REF, requested: process.env.ANDROID_RELEASE_REQUESTED });
  if (process.env.GITHUB_OUTPUT) fs.appendFileSync(process.env.GITHUB_OUTPUT, `android-release=${plan.signedAndroid}\nrelease-tag=${plan.releaseTag}\n`);
  if (process.env.GITHUB_STEP_SUMMARY) fs.appendFileSync(process.env.GITHUB_STEP_SUMMARY, `## Build policy\n\n${plan.reason}\n\nValidation: web, desktop, Android debug and iOS Simulator. Signed Android output: APK and AAB.${plan.releaseTag ? ' Release-profile desktop and a draft GitHub release are also produced.' : ''}\n`);
  console.log(plan.reason);
}
