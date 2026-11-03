export function openWindowExtra() {
  return Promise.reject(Object.assign(new Error('External authentication callbacks are not enabled'), { code: 'CAPABILITY_UNAVAILABLE' }));
}
