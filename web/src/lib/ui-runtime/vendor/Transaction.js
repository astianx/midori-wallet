import Amount from './Amount.js';

export default class Transaction {
  static STATUS_PENDING = Symbol('PENDING');
  static STATUS_SUCCESS = Symbol('SUCCESS');
  static STATUS_FAILED = Symbol('FAILED');

  static STATUSES = [
    Transaction.STATUS_PENDING,
    Transaction.STATUS_SUCCESS,
    Transaction.STATUS_FAILED,
  ];

  static ACTION_TRANSFER = Symbol('TRANSFER');
  static ACTION_TOKEN_TRANSFER = Symbol('TOKEN_TRANSFER');
  static ACTION_STAKING = Symbol('STAKING');
  static ACTION_UNSTAKING = Symbol('UNSTAKING');
  static ACTION_SMART_CONTRACT_CALL = Symbol('SMART_CONTRACT_CALL');
  static ACTION_CARD_DEPOSIT = Symbol('CARD_DEPOSIT');

  static ACTIONS = [
    Transaction.ACTION_TRANSFER,
    Transaction.ACTION_TOKEN_TRANSFER,
    Transaction.ACTION_STAKING,
    Transaction.ACTION_UNSTAKING,
    Transaction.ACTION_SMART_CONTRACT_CALL,
    Transaction.ACTION_CARD_DEPOSIT,
  ];

  constructor({
    status,
    id,
    amount,
    incoming,
    from,
    to,
    fee,
    timestamp,
    confirmations,
    minConfirmations,
    meta = undefined,
    rbf = false,
    action = Transaction.ACTION_TRANSFER,
    development = false,
  }) {
    if (!this.constructor.STATUSES.includes(status)) {
      throw new TypeError(`unsupported status: ${status}`);
    }
    this.status = status;
    if (!this.constructor.ACTIONS.includes(action)) {
      throw new TypeError(`unsupported action: ${action}`);
    }
    this.action = action;
    this.id = id;
    if (!(amount instanceof Amount)) {
      throw new TypeError(`amount must be an instance of Amount, ${typeof amount} provided`);
    }
    this.amount = amount;
    this.incoming = incoming;
    this.from = from;
    this.to = to;
    if (!(fee instanceof Amount || fee === undefined)) {
      throw new TypeError(`fee must be an instance of Amount or undefined, ${typeof fee} provided`);
    }
    this.fee = fee;
    this.timestamp = timestamp || new Date();
    this.confirmations = confirmations;
    this.minConfirmations = minConfirmations;
    this.meta = meta;
    this.rbf = rbf;

    this.development = !!development;
  }

  get url() {
    return '';
  }

  toString() {
    return `#${this.id}: ${this.from} => ${this.to} ${this.incoming ? '+' : '-'}${this.amount} (fee: ${this.fee})`;
  }

  [Symbol.for('nodejs.util.inspect.custom')]() {
    return this.toString();
  }
}
