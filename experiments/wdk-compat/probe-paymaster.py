import json,urllib.request,datetime
from pathlib import Path
entrypoint='0x0000000071727De22E5E9d8BAf0edAc6f37da032'
results=[]
for chain in [1,8453,84532]:
 url=f'https://api.candide.dev/public/v3/{chain}'
 for method,params in [('eth_supportedEntryPoints',[]),('pm_supportedERC20Tokens',[entrypoint])]:
  req=urllib.request.Request(url,data=json.dumps({'jsonrpc':'2.0','id':1,'method':method,'params':params}).encode(),headers={'Content-Type':'application/json'})
  try:
   with urllib.request.urlopen(req,timeout=15) as res:body=json.load(res)
  except Exception as e:body={'error':str(e)}
  results.append({'chainId':chain,'url':url,'method':method,'result':body})
result={'checkedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'readOnly':True,'results':results}
open(Path(__file__).resolve().parents[2]/'docs/evidencia/ejecucion/f1/public-fee-tokens.json','w').write(json.dumps(result,indent=2)+'\n')
for row in results:
 data=row['result'].get('result',{})
 tokens=data.get('tokens',[]) if isinstance(data,dict) else []
 print(row['chainId'],row['method'], [(t['symbol'],t['address'],int(t['decimals'],16)) for t in tokens] if tokens else ('OK' if 'result' in row['result'] else 'FAILED'))
