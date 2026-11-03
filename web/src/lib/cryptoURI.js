import { errors } from './account/domain.js';
import { paymentLink } from '../../../electron/lib/security.js';
const contracts = new Set(['0xfde4c96c8593536e31f229ea8f37b2ada2699bb2','0x833589fcd6edb6e08f4c7c32d4f71b54bda02913']);
export function parseCryptoURI(value) {
  const link = paymentLink(value);
  if (!link) throw new errors.AddressError('Invalid Base payment URI');
  if (/^0x[0-9a-fA-F]{40}$/.test(link)) return { address: link };
  const url = new URL(link);
  const [target, method] = url.pathname.split('/');
  const address = target.split('@')[0];
  if (!method) return { scheme:'ethereum', address };
  if (method !== 'transfer' || !contracts.has(address.toLowerCase()) || !url.searchParams.has('address')) throw new errors.AddressError('Unsupported payment token');
  const data = { scheme:'ethereum', token:address.toLowerCase(), address:url.searchParams.get('address') };
  if (url.searchParams.has('uint256')) {
    const units = BigInt(url.searchParams.get('uint256'));
    if (units >= 2n ** 256n) throw new errors.AddressError('Invalid payment amount');
    const fraction = (units % 1000000n).toString().padStart(6,'0').replace(/0+$/,'');
    data.amount = `${units / 1000000n}${fraction ? '.' + fraction : ''}`;
  }
  return data;
}
