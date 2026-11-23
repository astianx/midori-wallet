export const STABLECOIN_SOURCES = Object.freeze({
  checkedAt: '2026-11-23',
  circle: 'https://developers.circle.com/stablecoins/usdc-contract-addresses',
  tether: 'https://tether.to/en/supported-protocols/',
  usdt0: 'https://docs.usdt0.to/technical-documentation/deployments',
});
const catalog = [
  {
    "id": "base",
    "name": "Base",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x833589fcd6edb6e08f4c7c32d4f71b54bda02913",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "chainId": 8453,
    "nativeSymbol": "ETH",
    "accountType": "safe",
    "explorer": "https://basescan.org",
    "enabled": true
  },
  {
    "id": "arbitrum",
    "name": "Arbitrum",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xaf88d065e77c8cc2239327c5edb3a432268e5831",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT0",
        "contract": "0xfd086bc7cd5c481dcc9c85ebe478a1c0b69fcbb9",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "chainId": 42161,
    "nativeSymbol": "ETH",
    "rpcUrl": "https://arbitrum-one-rpc.publicnode.com",
    "explorer": "https://arbiscan.io",
    "enabled": true,
    "accountType": "safe",
    "bundlerUrl": "https://api.candide.dev/public/v3/42161"
  },
  {
    "id": "avalanche",
    "name": "Avalanche C-Chain",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xb97ef9ef8734c71904d8002f8b6bc66dd9c48a6e",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT",
        "contract": "0x9702230a8ea53601f5cd2dc00fdbc13d4df4a8c7",
        "decimals": 6,
        "representation": "native",
        "source": "tether"
      }
    ],
    "chainId": 43114,
    "nativeSymbol": "AVAX",
    "rpcUrl": "https://avalanche-c-chain-rpc.publicnode.com",
    "explorer": "https://snowtrace.io",
    "enabled": true,
    "accountType": "safe",
    "bundlerUrl": "https://api.candide.dev/public/v3/43114"
  },
  {
    "id": "ethereum",
    "name": "Ethereum",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT",
        "contract": "0xdac17f958d2ee523a2206206994597c13d831ec7",
        "decimals": 6,
        "representation": "native",
        "source": "tether"
      }
    ],
    "chainId": 1,
    "nativeSymbol": "ETH",
    "rpcUrl": "https://ethereum-rpc.publicnode.com",
    "explorer": "https://etherscan.io",
    "enabled": true,
    "accountType": "safe",
    "bundlerUrl": "https://api.candide.dev/public/v3/1"
  },
  {
    "id": "polygon",
    "name": "Polygon PoS",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x3c499c542cef5e3811e1192ce70d8cc03d5c3359",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT0",
        "contract": "0xc2132d05d31c914a87c6611c10748aeb04b58e8f",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "chainId": 137,
    "nativeSymbol": "POL",
    "rpcUrl": "https://polygon-bor-rpc.publicnode.com",
    "explorer": "https://polygonscan.com",
    "enabled": true,
    "accountType": "safe",
    "bundlerUrl": "https://api.candide.dev/public/v3/137"
  }
];
export const STABLECOIN_NETWORKS = Object.freeze(catalog.map(network => Object.freeze({ ...network, assets: Object.freeze(network.assets.map(Object.freeze)) })));
export const evmNetworks = STABLECOIN_NETWORKS.filter(network => network.enabled && network.id !== 'base');
export function stablecoinNetwork(id) { return STABLECOIN_NETWORKS.find(network => network.id === id); }
export function networkAsset(network, contract) { return network?.assets.find(asset => asset.contract.toLowerCase() === contract?.toLowerCase()); }
export function isStablecoin(symbol) { return ['USDC', 'USDT', 'USDT0'].includes(symbol); }

export function canSendStablecoin(symbol) { return symbol === 'USDC'; }
