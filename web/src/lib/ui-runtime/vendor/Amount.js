export default class Amount {
  constructor(value, decimals) {
    this.value = BigInt(value);
    this.decimals = parseInt(decimals);
  }

  toString() {
    if (this.decimals === 0) return this.value.toString();
    const str = (this.value >= 0n ? this.value : -this.value)
      .toString()
      .padStart(this.decimals + 1, '0');
    return `${this.value >= 0n ? '' : '-'}${str.slice(0, -1 * this.decimals)}.${str.slice(-1 * this.decimals)}`
      // leading zeroes
      .replace(/0+$/, '')
      // leading dot
      .replace(/\.$/, '');
  }
  static fromString(str, decimals) {
    const [integer, fraction = ''] = str.split('.');
    if (decimals === undefined) {
      decimals = fraction.length;
    }
    return new Amount(`${integer}${fraction.padEnd(decimals, '0').slice(0, decimals)}`, decimals);
  }

  [Symbol.for('nodejs.util.inspect.custom')]() {
    return `${this.value} / 10^${this.decimals}`;
  }
}
