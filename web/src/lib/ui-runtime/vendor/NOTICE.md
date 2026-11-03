Presentation-only compatibility subset from CoinSpace/cs-common v1.1.13, MIT.
Source commit: 8d94d384509687079eb5c76a4819af94800845e6.
Source: https://github.com/CoinSpace/cs-common/tree/8d94d384509687079eb5c76a4819af94800845e6
Amount.js, Transaction.js, errors.js and utils.js are preserved verbatim with their license.
CsWallet.js and fee.js are deliberately excluded. This subset cannot execute wallet operations or apply upstream fees.
The existing Vue components keep their interfaces while the WDK product adapter is implemented in F3/F4.
