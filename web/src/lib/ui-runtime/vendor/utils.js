import big from 'big.js';

const Big = big();
// 32 decimal points is enough for everyone
Big.DP = 32;

/**
 * atom - atomic piece of crypto
 * unit - human-readable piece of crypto
 * fiat - fiat currency
 * decimals - number of atoms in single crypto unit
 */

/**
 *
 * @param {string|number} number - usually price in fiat currency
 * @returns {number} - decimal points
 */
export function getPrecision(number) {
  const string = typeof number === 'string' ? number : Big(number).toFixed();
  if (string.includes('.')) {
    const precision = string.split('.')[1].length;
    return (precision > 2) ? precision : 2;
  } else {
    return 2;
  }
}

/**
 *
 * Convert value in fiat currency
 * to amount of crypto atoms
 * with respect of decimals
 *
 * @param {string|number} value - amount in fiat currency
 * @param {string|number} price - price of single crypto unit in fiat currency
 * @param {number} decimals - amount of atoms in single crypto unit
 * @returns {bigint} - amount in crypto atoms
 */
export function fiatToAtom(value, price, decimals) {
  // atom = fiat / price * (10 ** decimals)
  return BigInt(Big(10).pow(decimals).times(value).div(price).toFixed(0));
}

/**
 *
 * Convert value in crypto atoms
 * to amount in fiat currency
 * with respect of decimals
 *
 * @param {bigint} value - amount in crypto atoms
 * @param {string|number} price - price of single crypto unit in fiat currency
 * @param {number} decimals - amount of atoms in single crypto unit
 * @returns {string} - amount in fiat currency
 */
export function atomToFiat(value, price, decimals) {
  const precision = getPrecision(price);
  // fiat = atom * price / (10 ** decimals)
  return Big(value).times(price).div(Big(10).pow(decimals)).toFixed(precision);
}

/**
 *
 * Multiply crypto atoms
 * by fractional number
 *
 * @param {bigint} value - amount in crypto atoms
 * @param {string|number} multiplier - any number
 * @returns {bigint} - amount in crypto atoms
 */
export function multiplyAtom(value, multiplier) {
  return BigInt(Big(value).times(multiplier).toFixed(0, Big.roundDown));
}

/**
 *
 * Convert atoms to units
 *
 * @param {bigint} value - value in atoms
 * @param {number} decimals - amount of atoms in single crypto unit
 * @returns {string} - amount in units
 */
export function atomToUnit(value, decimals) {
  return Big(value).div(Big(10).pow(decimals)).toFixed(decimals);
}

/**
 *
 * Convert atoms to units rounded to fiat cents
 *
 * @param {bigint} value - value in atoms
 * @param {number} decimals - amount of atoms in single crypto unit
 * @param {string|number} price - price of single crypto unit in fiat currency
 * @returns {string} - amount in units
 */
export function atomToRoundUnit(value, decimals, price) {
  const priceOfCent = Big(1).div(price).times(0.01);
  const precision = priceOfCent.e > -1 ? 0 : Math.abs(priceOfCent.e);
  return Big(Big(value).div(Big(10).pow(decimals)).toFixed(precision)).toFixed();
}

/**
 *
 * @param {string|number} value - value in units
 * @param {number} decimals - amount of atoms in single crypto unit
 * @returns {bigint} - amount in atoms
 */
export function unitToAtom(value, decimals) {
  return BigInt(Big(value).times(Big(10).pow(decimals)).toFixed(0));
}
