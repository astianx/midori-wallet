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
    "enabled": true
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
    "enabled": true
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
    "enabled": true
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
    "enabled": true
  },
  {
    "id": "algorand",
    "name": "Algorand",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "31566704",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "aptos",
    "name": "Aptos",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xbae207659db88bea0cbead6da0ed00aac12edcdda169e591cd41c94180b46f3b",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT",
        "contract": "0x357b0b74bc833e95a115ad22604854d6b0fca151cecd94111770e5d6ffc9dc2b",
        "decimals": 6,
        "representation": "native",
        "source": "tether"
      }
    ],
    "enabled": false
  },
  {
    "id": "arc",
    "name": "Arc",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x3600000000000000000000000000000000000000",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "berachain",
    "name": "Berachain",
    "assets": [
      {
        "symbol": "USDT0",
        "contract": "0x779ded0c9e1022225f8e0630b35a9b54be713736",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "celo",
    "name": "Celo",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xceba9300f2b948710d2653dd7b07f33a8b32118c",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT",
        "contract": "0x48065fbbe25f71c9282ddf5e1cd6d6a887483d5e",
        "decimals": 6,
        "representation": "native",
        "source": "tether"
      }
    ],
    "enabled": false
  },
  {
    "id": "codex",
    "name": "Codex",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xd996633a415985dbd7d6d12f4a4343e31f5037cf",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "conflux",
    "name": "Conflux eSpace",
    "assets": [
      {
        "symbol": "USDT0",
        "contract": "0xaf37e8b6c9ed7f6318979f56fc287d76c30847ff",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "cronos",
    "name": "Cronos",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x3d7f2c478aafdb65542bcb44bceec05849999d2d",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "edge",
    "name": "EDGE",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x98d2919b9a214e6fa5384ac81e6864ba686ad74c",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "flare",
    "name": "Flare",
    "assets": [
      {
        "symbol": "USDT0",
        "contract": "0xe7cd86e13ac4309349f30b3435a9d337750fc82d",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "hedera",
    "name": "Hedera",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0.0.456858",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT0",
        "contract": "0x00000000000000000000000000000000009ce723",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "hypercore",
    "name": "HyperCore",
    "assets": [
      {
        "symbol": "USDT0",
        "contract": "0x25faedc3f054130dbb4e4203aca63567",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "hyperevm",
    "name": "HyperEVM",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xb88339cb7199b77e23db6e890353e22632ba630f",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT0",
        "contract": "0xb8ce59fc3717ada4c02eadf9682a9e934f625ebb",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "injective",
    "name": "Injective",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xa00c59ff5a080d2b954d0c75e46e22a0c371235a",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "ink",
    "name": "Ink",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x2d270e6886d130d724215a266106e6832161eaed",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT0",
        "contract": "0x0200c29006150606b650577bbe7b6248f58470c1",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "kaia",
    "name": "Kaia",
    "assets": [
      {
        "symbol": "USDT",
        "contract": "0xd077a400968890eacc75cdc901f0356c943e4fdb",
        "decimals": 6,
        "representation": "native",
        "source": "tether"
      }
    ],
    "enabled": false
  },
  {
    "id": "kava",
    "name": "Kava",
    "assets": [
      {
        "symbol": "USDT",
        "contract": "0x919c1c267bc06a7039e03fcc2ef738525769109c",
        "decimals": 6,
        "representation": "native",
        "source": "tether"
      }
    ],
    "enabled": false
  },
  {
    "id": "linea",
    "name": "Linea",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x176211869ca2b568f2a7d4ee941e073a821ee1ff",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "liquid",
    "name": "Liquid",
    "assets": [
      {
        "symbol": "USDT",
        "contract": "ce091c998b83c78bb71a632313ba3760f1763d9cfcffae02258ffa9865a37bd2",
        "decimals": 6,
        "representation": "native",
        "source": "tether"
      }
    ],
    "enabled": false
  },
  {
    "id": "mantle",
    "name": "Mantle",
    "assets": [
      {
        "symbol": "USDT0",
        "contract": "0x779ded0c9e1022225f8e0630b35a9b54be713736",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "megaeth",
    "name": "MegaETH",
    "assets": [
      {
        "symbol": "USDT0",
        "contract": "0xb8ce59fc3717ada4c02eadf9682a9e934f625ebb",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "monad",
    "name": "Monad",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x754704bc059f8c67012fed69bc8a327a5aafb603",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT0",
        "contract": "0xe7cd86e13ac4309349f30b3435a9d337750fc82d",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "morph",
    "name": "Morph",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xcfb1186f4e93d60e60a8bdd997427d1f33bc372b",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT0",
        "contract": "0xe7cd86e13ac4309349f30b3435a9d337750fc82d",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "near",
    "name": "NEAR",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "17208628f84f5d6ad33f0da3bbbeb27ffcb398eac501a31bd6ad2011e36133a1",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT",
        "contract": "usdt.tether-token.near",
        "decimals": 6,
        "representation": "native",
        "source": "tether"
      }
    ],
    "enabled": false
  },
  {
    "id": "noble",
    "name": "Noble",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "uusdc",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "optimism",
    "name": "OP Mainnet",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x0b2c639c533813f4aa9d7837caf62653d097ff85",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT0",
        "contract": "0x01bff41798a0bcf287b996046ca68b395dbc1071",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "pharos",
    "name": "Pharos",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xc879c018db60520f4355c26ed1a6d572cdac1815",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "plasma",
    "name": "Plasma",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x2d661c89d812261039af9764eceaaee884f5f67f",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT0",
        "contract": "0xb8ce59fc3717ada4c02eadf9682a9e934f625ebb",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "plume",
    "name": "Plume",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x222365ef19f7947e5484218551b56bb3965aa7af",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "polkadot",
    "name": "Polkadot Asset Hub",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "1337",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT",
        "contract": "1984",
        "decimals": 6,
        "representation": "native",
        "source": "tether"
      }
    ],
    "enabled": false
  },
  {
    "id": "rootstock",
    "name": "Rootstock",
    "assets": [
      {
        "symbol": "USDT0",
        "contract": "0x779ded0c9e1022225f8e0630b35a9b54be713736",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "sei",
    "name": "Sei",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xe15fc38f6d8c56af07bbcbe3baf5708a2bf42392",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT0",
        "contract": "0x9151434b16b9763660705744891fa906f660ecc5",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "solana",
    "name": "Solana",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT",
        "contract": "Es9vMFrzaCERmJfrF4H2FYD4KCoNkY11McCe8BenwNYB",
        "decimals": 6,
        "representation": "native",
        "source": "tether"
      }
    ],
    "enabled": false
  },
  {
    "id": "sonic",
    "name": "Sonic",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x29219dd400f2bf60e5a23d13be72b486d4038894",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "stable",
    "name": "Stable",
    "assets": [
      {
        "symbol": "USDT0",
        "contract": "0x779ded0c9e1022225f8e0630b35a9b54be713736",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "starknet",
    "name": "Starknet",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x033068F6539f8e6e6b131e6B2B814e6c34A5224bC66947c47DaB9dFeE93b35fb",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "stellar",
    "name": "Stellar",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "USDC-GA5ZSEJYB37JRC5AVCIA5MOP4RHTM335X2KGX3IHOJAPP5RE34K4KZVN",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT0",
        "contract": "CBSJZEIO5C7KC2SF3MKSNXXJSW5G3VTNBX4ATMKUI3B2MR4JKM4R26YF",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "sui",
    "name": "Sui",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xdba34672e30cb065b1f93e3ab55318768fd6fef66c15942c9f7cb846e2f900e7::usdc::USDC",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "ton",
    "name": "TON",
    "assets": [
      {
        "symbol": "USDT",
        "contract": "EQCxE6mUtQJKFnGfaROTKOt1lZbDiiX1kCixRv7Nw2Id_sDs",
        "decimals": 6,
        "representation": "native",
        "source": "tether"
      }
    ],
    "enabled": false
  },
  {
    "id": "tempo",
    "name": "Tempo",
    "assets": [
      {
        "symbol": "USDT0",
        "contract": "0x20c00000000000000000000014f22ca97301eb73",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "tezos",
    "name": "Tezos",
    "assets": [
      {
        "symbol": "USDT",
        "contract": "KT1XnTn74bUtxHfDtBmm2bGZAQfhPbvKWR8o:0",
        "decimals": 6,
        "representation": "native",
        "source": "tether"
      }
    ],
    "enabled": false
  },
  {
    "id": "tron",
    "name": "Tron",
    "assets": [
      {
        "symbol": "USDT",
        "contract": "TR7NHqjeKQxGTCi8q8ZY4pL8otSzgjLj6t",
        "decimals": 6,
        "representation": "native",
        "source": "tether"
      }
    ],
    "enabled": false
  },
  {
    "id": "unichain",
    "name": "Unichain",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x078d782b760474a361dda0af3839290b0ef57ad6",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT0",
        "contract": "0x9151434b16b9763660705744891fa906f660ecc5",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "world-chain",
    "name": "World Chain",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x79a02482a880bce3f13e09da970dc34db4cd24d1",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "x-layer",
    "name": "X Layer",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xb6ceceab302e2e4948951ee7843fc24e92933061",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      },
      {
        "symbol": "USDT0",
        "contract": "0x779ded0c9e1022225f8e0630b35a9b54be713736",
        "decimals": 6,
        "representation": "omnichain",
        "source": "usdt0"
      }
    ],
    "enabled": false
  },
  {
    "id": "xdc",
    "name": "XDC",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0xfa2958cb79b0491cc627c1557f441ef849ca8eb1",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "xrpl",
    "name": "XRPL",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "5553444300000000000000000000000000000000.rGm7WCVp9gb4jZHWTEtGUr4dd74z2XuWhE",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  },
  {
    "id": "zksync",
    "name": "ZKsync Era",
    "assets": [
      {
        "symbol": "USDC",
        "contract": "0x1d17cbcf0d6d143135ae902365d2e5e2a16538d4",
        "decimals": 6,
        "representation": "native",
        "source": "circle"
      }
    ],
    "enabled": false
  }
];
export const STABLECOIN_NETWORKS = Object.freeze(catalog.map(network => Object.freeze({ ...network, assets: Object.freeze(network.assets.map(Object.freeze)) })));
export const evmNetworks = STABLECOIN_NETWORKS.filter(network => network.enabled && network.accountType !== 'safe');
export function stablecoinNetwork(id) { return STABLECOIN_NETWORKS.find(network => network.id === id); }
export function networkAsset(network, contract) { return network?.assets.find(asset => asset.contract.toLowerCase() === contract?.toLowerCase()); }
export function isStablecoin(symbol) { return ['USDC', 'USDT', 'USDT0'].includes(symbol); }
