import QRCode from 'qrcode-svg';
import logoUrl from '../assets/svg/logo.svg?url';
import { activityStatus, activityTitle } from './account/activity-presentation.js';

export const receiptDate = (value, locale) => new Intl.DateTimeFormat(locale, { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit', timeZoneName: 'short', calendar: 'gregory', numberingSystem: 'latn' }).format(new Date(value));
const money = value => value ? `${value.value} ${value.currency}` : '—';
export function receiptContent(document, t, locale) {
  const item = { ...document, state: document.status };
  const party = value => value ? [value.tag, value.address].filter(Boolean).join('\n') : '—';
  const rows = [{ label: t('Date'), value: receiptDate(document.date, locale) }, { label: t('From'), value: party(document.from), ltr: true }, { label: t('To'), value: party(document.to), ltr: true }];
  if (document.source === 'ramp') rows.push({ label: t('You pay'), value: money(document.paid), ltr: true }, { label: t('Receive'), value: money(document.received), ltr: true });
  if (document.midoriFee) rows.push({ label: t('Midori fee'), value: money(document.midoriFee), ltr: true });
  if (document.networkCost) rows.push({ label: t('Network cost'), value: document.networkCost.sponsored ? t('Sponsored by Midori') : money(document.networkCost), ltr: true });
  rows.push({ label: t('Network'), value: document.network }, { label: t('Reference'), value: document.reference, ltr: true });
  return { title: activityTitle(item, t), amount: money(document.amount), status: activityStatus(item, t), completed: ['confirmed', 'completed'].includes(document.status), test: document.environment === 'STAGING', rows };
}
const loadImage = source => new Promise((resolve, reject) => { const image = new Image(); image.onload = () => resolve(image); image.onerror = () => reject(Error('Image unavailable')); image.src = source; });
const bytes = value => new TextEncoder().encode(value);
function pdfFromCanvas(canvas) {
  // One immutable raster is used in both formats. PDF does not reformat amounts,
  // drop RTL glyphs, or depend on a reader's installed fonts.
  const jpeg = Uint8Array.from(atob(canvas.toDataURL('image/jpeg', 0.97).split(',')[1]), c => c.charCodeAt(0));
  const pageWidth = 595.28, pageHeight = 841.89, scale = Math.min(547.28 / canvas.width, 793.89 / canvas.height), width = canvas.width * scale, height = canvas.height * scale;
  const content = `q ${width.toFixed(3)} 0 0 ${height.toFixed(3)} ${((pageWidth - width) / 2).toFixed(3)} ${((pageHeight - height) / 2).toFixed(3)} cm /Receipt Do Q`;
  const objects = [bytes('<< /Type /Catalog /Pages 2 0 R >>'), bytes('<< /Type /Pages /Kids [3 0 R] /Count 1 >>'), bytes(`<< /Type /Page /Parent 2 0 R /MediaBox [0 0 ${pageWidth} ${pageHeight}] /Resources << /XObject << /Receipt 4 0 R >> >> /Contents 5 0 R >>`), new Blob([bytes(`<< /Type /XObject /Subtype /Image /Width ${canvas.width} /Height ${canvas.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length ${jpeg.length} >>\nstream\n`), jpeg, bytes('\nendstream')]), bytes(`<< /Length ${bytes(content).length} >>\nstream\n${content}\nendstream`)];
  const parts = [bytes('%PDF-1.4\n')], offsets = [0]; let offset = parts[0].length;
  objects.forEach((object, index) => { const prefix = bytes(`${index + 1} 0 obj\n`), suffix = bytes('\nendobj\n'); offsets.push(offset); parts.push(prefix, object, suffix); offset += prefix.length + (object.size ?? object.length) + suffix.length; });
  parts.push(bytes(`xref\n0 ${objects.length + 1}\n0000000000 65535 f \n${offsets.slice(1).map(value => String(value).padStart(10, '0') + ' 00000 n ').join('\n')}\ntrailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${offset}\n%%EOF\n`));
  return new Blob(parts, { type: 'application/pdf' });
}
export async function renderReceipt(document, verificationUrl, t, locale) {
  const link = new URL(verificationUrl);
  if (!['https:', 'http:'].includes(link.protocol) || link.username || link.password || !/^[A-Za-z0-9_-]{43}$/.test(link.hash.slice(1))) throw Error('Invalid verification link');
  await globalThis.document.fonts.ready;
  const content = receiptContent(document, t, locale), width = 1440, padding = 100;
  const canvas = globalThis.document.createElement('canvas'); canvas.width = width; canvas.height = 4000;
  const ctx = canvas.getContext('2d'), style = getComputedStyle(globalThis.document.documentElement), body = getComputedStyle(globalThis.document.body);
  const ink = style.getPropertyValue('--color-ink').trim(), muted = style.getPropertyValue('--color-muted').trim(), accent = style.getPropertyValue('--color-accent').trim(), surface = style.getPropertyValue('--color-surface').trim(), rule = style.getPropertyValue('--color-rule').trim();
  const rtl = globalThis.document.documentElement.dir === 'rtl', font = body.fontFamily;
  ctx.fillStyle = surface; ctx.fillRect(0, 0, width, canvas.height); let y = 90;
  const wrap = (text, size, weight = 400, maxWidth = width - 2 * padding) => {
    ctx.font = `${weight} ${size}px ${font}`; const lines = [];
    for (const paragraph of String(text).split('\n')) { let line = ''; for (const character of paragraph) { if (line && ctx.measureText(line + character).width > maxWidth) { lines.push(line); line = ''; } line += character; } lines.push(line); }
    return lines;
  };
  const write = (text, size, color = ink, weight = 400, ltr = false) => {
    ctx.font = `${weight} ${size}px ${font}`; ctx.fillStyle = color; ctx.direction = rtl && !ltr ? 'rtl' : 'ltr'; ctx.textAlign = rtl && !ltr ? 'right' : 'left'; ctx.textBaseline = 'top';
    const lines = wrap(text, size, weight); ctx.font = `${weight} ${size}px ${font}`;
    for (const line of lines) { ctx.fillText(line, rtl && !ltr ? width - padding : padding, y); y += size * 1.45; }
  };
  const logo = await loadImage(logoUrl); ctx.drawImage(logo, padding, y - 15, 165, 136); y += 160;
  write('Midori Wallet', 34, accent, 600); y += 16; write(t('Receipt'), 32, muted); write(content.title, 42, ink, 600); y += 20; write(content.amount, 76, ink, 650, true); write(content.status, 34, accent, 600);
  if (!content.completed) { y += 12; write(t('Not a completed payment.'), 30, muted); }
  if (content.test) { y += 10; write(t('Test mode. Do not use real money.'), 30, muted); }
  y += 40;
  for (const row of content.rows) { ctx.fillStyle = rule; ctx.fillRect(padding, y, width - 2 * padding, 1); y += 22; write(row.label, 27, muted); write(row.value, 34, ink, 500, row.ltr); y += 25; }
  const qrSvg = new QRCode({ content: verificationUrl, width: 380, height: 380, padding: 4, ecl: 'M', join: true }).svg();
  const qrUrl = URL.createObjectURL(new Blob([qrSvg], { type: 'image/svg+xml' }));
  try { const qr = await loadImage(qrUrl); y += 12; ctx.drawImage(qr, (width - 380) / 2, y, 380, 380); y += 404; } finally { URL.revokeObjectURL(qrUrl); }
  write(t('Scan to verify'), 30, accent, 600); write(link.origin, 28, muted, 400, true); y += 12; write(`${t('Created')}: ${receiptDate(document.issuedAt, locale)}`, 23, muted); y += 55;
  if (y > canvas.height) throw Error('Receipt layout overflow');
  const result = globalThis.document.createElement('canvas'); result.width = width; result.height = Math.ceil(y); result.getContext('2d').drawImage(canvas, 0, 0);
  const png = await new Promise(resolve => result.toBlob(resolve, 'image/png')); if (!png) throw Error('Receipt image unavailable');
  const pdf = pdfFromCanvas(result), name = `midori-receipt-${document.id}`;
  return { png: new globalThis.File([png], name + '.png', { type: 'image/png' }), pdf: new globalThis.File([pdf], name + '.pdf', { type: 'application/pdf' }), content };
}
