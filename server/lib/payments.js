import { randomUUID } from 'node:crypto';
import { getAddress, ZeroAddress } from 'ethers';
import { ApiError, exactKeys, requireValue, notFound } from './errors.js';
import { canonical, sha256, uuidPattern } from './crypto.js';
import { account, activePrincipal } from './repositories.js';
import { validateResolution, verifyTagSnapshot } from './tags.js';
import { paymentOperation, paymentHash, paymentSignature, validatePaymentOperation } from '../../packages/wallet-core/src/payment.js';
import { paymentProvider, inspectOperationReceipt, userOperationEvents } from './payment-provider.js';

const active=['preparing','quoted','authorized','submitted','ambiguous','included','reorganized'];
const parseAddress=value=>{try{return getAddress(value).toLowerCase();}catch{throw fail('INVALID_RECIPIENT','Invalid wallet address',400);}};
const same=(a,b)=>typeof a==='string'&&typeof b==='string'&&a.toLowerCase()===b.toLowerCase();
const atomic=value=>typeof value==='string'&&/^(0|[1-9][0-9]{0,77})$/.test(value)&&BigInt(value)<2n**256n;
const fail=(code,message,status=409)=>new ApiError(status,code,message);
export function publicIntent(row,operation) {
  return {id:row.id,accountId:row.account_id,chainId:row.chain_id,contract:getAddress(row.contract),amountAtomic:String(row.amount_atomic),from:getAddress(row.sender),to:getAddress(row.recipient),recipientKind:row.recipient_kind,recipientTag:row.recipient_tag,feeMode:row.fee_mode,feeToken:row.fee_token&&getAddress(row.fee_token),status:row.status,failureCode:row.failure_code,expiresAt:row.expires_at,createdAt:row.created_at,updatedAt:row.updated_at,quote:row.quote,quoteDigest:row.quote_digest,userOperationHash:operation?.user_operation_hash||null,transactionHash:operation?.transaction_hash||null,actualFeeAtomic:operation?.actual_fee_atomic??null,accountingState:operation?.accounting_state||'pending'};
}
export async function loadIntent(client,walletId,id,lock=false) {
  requireValue(uuidPattern.test(id),'Invalid payment ID');
  const {rows}=await client.query(`SELECT *,amount_atomic::text,requested_fee_cap::text FROM tx_intents WHERE id=$1 AND wallet_id=$2${lock?' FOR UPDATE':''}`,[id,walletId]);
  if(!rows.length)throw notFound();return rows[0];
}
async function present(client,row) {return publicIntent(row,(await client.query('SELECT *,actual_fee_atomic::text FROM user_operations WHERE intent_id=$1',[row.id])).rows[0]);}

async function reserveSponsor(client,config,row,quote) {
  if(row.fee_mode!=='sponsored')return;
  const day=(await client.query("SELECT (clock_timestamp() AT TIME ZONE 'UTC')::date::text AS day")).rows[0].day;
  await client.query('INSERT INTO sponsor_budgets(day,limit_atomic) VALUES($1,$2) ON CONFLICT DO NOTHING',[day,config.sponsorDailyBudget]);
  const result=await client.query('UPDATE sponsor_budgets SET reserved_atomic=reserved_atomic+$2 WHERE day=$1 AND spent_atomic+reserved_atomic+$2<=limit_atomic RETURNING day',[day,quote.maxNativeAtomic]);
  if(!result.rowCount)throw fail('SPONSOR_BUDGET_EXHAUSTED','Sponsorship is unavailable. Choose token-paid gas and confirm a new quote.',503);
  await client.query("INSERT INTO sponsor_reservations(intent_id,budget_day,max_native_atomic,status) VALUES($1,$2,$3,'held')",[row.id,day,quote.maxNativeAtomic]);
}
export async function sponsorAccounting(client,id,action,actual='0',blockHash=null) {
  const {rows}=await client.query('SELECT *,max_native_atomic::text,actual_native_atomic::text FROM sponsor_reservations WHERE intent_id=$1 FOR UPDATE',[id]);
  const row=rows[0];if(!row)return;
  if(action==='settle'&&row.status==='held') {
    if(BigInt(actual)>BigInt(row.max_native_atomic))throw fail('SPONSOR_COST_MISMATCH','Sponsorship cost needs reconciliation');
    await client.query('UPDATE sponsor_budgets SET reserved_atomic=reserved_atomic-$2,spent_atomic=spent_atomic+$3 WHERE day=$1',[row.budget_day,row.max_native_atomic,actual]);
    await client.query("UPDATE sponsor_reservations SET status='settled',actual_native_atomic=$2,block_hash=$3,updated_at=clock_timestamp() WHERE intent_id=$1",[id,actual,blockHash]);
  } else if(action==='release'&&row.status==='held') {
    await client.query('UPDATE sponsor_budgets SET reserved_atomic=reserved_atomic-$2 WHERE day=$1',[row.budget_day,row.max_native_atomic]);
    await client.query("UPDATE sponsor_reservations SET status='released',updated_at=clock_timestamp() WHERE intent_id=$1",[id]);
  } else if(action==='reorganize'&&row.status==='settled') {
    await client.query('UPDATE sponsor_budgets SET reserved_atomic=reserved_atomic+$2,spent_atomic=spent_atomic-$3 WHERE day=$1',[row.budget_day,row.max_native_atomic,row.actual_native_atomic]);
    await client.query("UPDATE sponsor_reservations SET status='held',actual_native_atomic=NULL,block_hash=NULL,updated_at=clock_timestamp() WHERE intent_id=$1",[id]);
  }
}
export async function expireQuotes(db) {
  return db.transaction(async client=>{
    const {rows}=await client.query("UPDATE tx_intents SET status='expired',updated_at=clock_timestamp() WHERE id IN (SELECT id FROM tx_intents WHERE status IN ('preparing','quoted') AND expires_at<=clock_timestamp() LIMIT 100 FOR UPDATE SKIP LOCKED) RETURNING id");
    for(const row of rows)await sponsorAccounting(client,row.id,'release');return rows.length;
  });
}

export function paymentService(db,config,provider=paymentProvider(config)) {
  const enabled=mode=>{
    if(!config.paymentsEnabled)throw fail('PAYMENTS_UNAVAILABLE','Sending is temporarily unavailable',503);
    if(mode==='sponsored'&&!config.sponsorshipEnabled)throw fail('SPONSOR_UNAVAILABLE','Sponsorship is unavailable; choose a payment mode explicitly',503);
  };
  const ensureFunds=async row=>{
    const balances=await provider.balances(row.sender,[row.contract,row.fee_token]);
    const amount=BigInt(row.amount_atomic),fee=row.fee_mode==='token'?BigInt(row.quote.maxFeeAtomic):0n;
    const available=balances.assets[row.contract];
    if(available<amount+(same(row.contract,row.fee_token)?fee:0n))throw fail('INSUFFICIENT_FUNDS','Not enough funds for this amount and its network cost');
    if(row.fee_token&&balances.assets[row.fee_token]<fee)throw fail('INSUFFICIENT_GAS_FUNDS','Add USDT to cover the network cost');
    return balances;
  };
  return {
    provider,
    async get(walletId,id) {return present(db,await loadIntent(db,walletId,id));},
    async list(walletId,accountId) {
      await account(db,walletId,accountId);
      const {rows}=await db.query('SELECT *,amount_atomic::text,requested_fee_cap::text FROM tx_intents WHERE wallet_id=$1 AND account_id=$2 ORDER BY created_at DESC LIMIT 50',[walletId,accountId]);
      return {intents:await Promise.all(rows.map(row=>present(db,row)))};
    },
    async create(principal,accountId,body) {
      exactKeys(body,['idempotencyKey','contract','amountAtomic','recipient','feeMode','feeToken','maxFeeAtomic']);
      requireValue(uuidPattern.test(accountId)&&uuidPattern.test(body.idempotencyKey),'Invalid payment ID');
      requireValue(body.amountAtomic==='max'||(atomic(body.amountAtomic)&&BigInt(body.amountAtomic)>0n),'Invalid amount');
      requireValue(['token','sponsored'].includes(body.feeMode),'Invalid gas mode');enabled(body.feeMode);
      requireValue(atomic(body.maxFeeAtomic),'Invalid network cost limit');
      const contract=parseAddress(body.contract);
      const feeToken=body.feeToken==null?null:parseAddress(body.feeToken);
      // Network cost is always paid in the same token being sent (self-pay):
      // the sender already holds a balance in that token, so no separate
      // top-up in a different stablecoin is ever required. Both supported
      // assets (USDT, USDC) share 6 decimals, so a single maxGasFeeAtomic
      // cap applies to either without unit conversion.
      requireValue(body.feeMode==='token'?same(feeToken,contract)&&BigInt(body.maxFeeAtomic)>0n&&BigInt(body.maxFeeAtomic)<=BigInt(config.maxGasFeeAtomic):feeToken===null&&body.maxFeeAtomic==='0','Unapproved gas token or limit');
      exactKeys(body.recipient,body.recipient?.kind==='address'?['kind','address']:['kind','resolutionId']);
      requireValue(['address','midori_tag'].includes(body.recipient.kind),'Invalid recipient');
      const request={...body,contract,feeToken,recipient:body.recipient.kind==='address'?{kind:'address',address:parseAddress(body.recipient.address)}:{kind:'midori_tag',resolutionId:body.recipient.resolutionId}};
      const fingerprint=sha256(canonical(request));let fresh=false;
      await expireQuotes(db);
      const row=await db.transaction(async client=>{
        await activePrincipal(client,principal);await client.query('SELECT pg_advisory_xact_lock(hashtextextended($1,738194262))',[accountId]);
        const previous=await client.query('SELECT *,amount_atomic::text FROM tx_intents WHERE wallet_id=$1 AND idempotency_key=$2',[principal.wallet_id,body.idempotencyKey]);
        if(previous.rows.length) {if(previous.rows[0].fingerprint!==fingerprint)throw fail('IDEMPOTENCY_CONFLICT','This payment key was already used with different parameters');return previous.rows[0];}
        const saved=await account(client,principal.wallet_id,accountId);
        requireValue(saved.assets.some(asset=>same(asset.contract,contract)),'Unsupported payment token');
        const {rows:busy}=await client.query('SELECT id FROM tx_intents WHERE account_id=$1 AND status=ANY($2::text[])',[accountId,active]);
        if(busy.length)throw new ApiError(409,'PAYMENT_ALREADY_PENDING','Review the pending payment before creating another',{intentId:busy[0].id});
        let recipient=request.recipient.address,tag=null,revision=null,resolutionId=null;
        if(request.recipient.kind==='midori_tag') {
          requireValue(uuidPattern.test(request.recipient.resolutionId),'Invalid tag resolution');
          const result=await client.query('SELECT * FROM tag_resolutions WHERE id=$1 AND wallet_id=$2',[request.recipient.resolutionId,principal.wallet_id]);
          const resolution=result.rows[0];if(!resolution)throw fail('TAG_RECONFIRM_REQUIRED','Resolve the tag again before confirming');
          recipient=resolution.address;await validateResolution(client,principal.wallet_id,resolution.id,recipient);tag=resolution.alias;revision=resolution.revision;resolutionId=resolution.id;
        }
        requireValue(recipient&&recipient!==ZeroAddress.toLowerCase()&&!same(recipient,saved.address)&&!config.gasRecipients.some(address=>same(address,recipient)),'Invalid payment destination');
        fresh=true;const id=randomUUID();
        const inserted=await client.query(`INSERT INTO tx_intents(id,wallet_id,account_id,idempotency_key,fingerprint,chain_id,contract,amount_atomic,sender,recipient,recipient_kind,resolution_id,recipient_tag,tag_revision,fee_mode,fee_token,requested_fee_cap,expires_at)
          VALUES($1,$2,$3,$4,$5,8453,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,clock_timestamp()+make_interval(secs=>$17)) RETURNING *,amount_atomic::text`,[id,principal.wallet_id,accountId,body.idempotencyKey,fingerprint,contract,body.amountAtomic==='max'?'1':body.amountAtomic,saved.address.toLowerCase(),recipient,request.recipient.kind,resolutionId,tag,revision,body.feeMode,feeToken,body.maxFeeAtomic,config.paymentQuoteSeconds]);
        return {...inserted.rows[0],descriptor:saved.descriptor};
      });
      if(!fresh)return present(db,row);
      try {
        if(row.resolution_id)await verifyTagSnapshot(db,row.resolution_id,provider.chain);
        const balances=await provider.balances(row.sender,[row.contract,row.fee_token]);
        if(row.fee_token&&balances.assets[row.fee_token]===0n)throw fail('INSUFFICIENT_GAS_FUNDS','Add USDT to cover the network cost');
        let amount=body.amountAtomic==='max'?1n:BigInt(body.amountAtomic),quote;
        for(let attempt=0;attempt<5;attempt++) {
          if(amount<=0n)throw fail('INSUFFICIENT_FUNDS','There are no spendable funds after the network cost');
          quote=await provider.quote(row.descriptor,{contract:row.contract,recipient:row.recipient,amountAtomic:amount.toString(),feeMode:row.fee_mode,feeToken:row.fee_token,maxFeeAtomic:body.maxFeeAtomic,nonceKey:BigInt('0x'+row.id.replaceAll('-','')).toString()});
          const reserve=same(row.contract,row.fee_token)?BigInt(quote.maxFeeAtomic):0n;
          if(body.amountAtomic==='max'&&attempt===0){const next=balances.assets[row.contract]-reserve;if(next!==amount){amount=next;quote=null;continue;}}
          if(body.amountAtomic!=='max'||amount+reserve<=balances.assets[row.contract])break;
          amount=balances.assets[row.contract]-reserve;quote=null;
        }
        if(!quote)throw fail('QUOTE_CHANGED','The network cost changed; request a new quote');
        row.amount_atomic=amount.toString();row.quote=quote;await ensureFunds(row);
        const approved={nonceKey:BigInt('0x'+row.id.replaceAll('-','')).toString(),observedBlock:balances.blockNumber,amountAtomic:row.amount_atomic,contract:row.contract,recipient:row.recipient,recipientTag:row.recipient_tag,feeMode:row.fee_mode,feeToken:row.fee_token,expiresAt:new Date(row.expires_at).toISOString(),...quote};
        const digest=sha256(canonical(approved));
        return await db.transaction(async client=>{
          await activePrincipal(client,principal);const locked=await loadIntent(client,principal.wallet_id,row.id,true);
          if(locked.status!=='preparing'||new Date(locked.expires_at)<=new Date())throw fail('QUOTE_EXPIRED','Request a new quote before sending');
          if(locked.resolution_id)await validateResolution(client,principal.wallet_id,locked.resolution_id,locked.recipient);
          await reserveSponsor(client,config,locked,approved);
          const result=await client.query("UPDATE tx_intents SET amount_atomic=$2,quote=$3,quote_digest=$4,status='quoted',updated_at=clock_timestamp() WHERE id=$1 RETURNING *,amount_atomic::text",[row.id,row.amount_atomic,approved,digest]);
          return present(client,result.rows[0]);
        });
      } catch(error) {
        await db.query("UPDATE tx_intents SET status='rejected',failure_code=$2,updated_at=clock_timestamp() WHERE id=$1 AND status='preparing'",[row.id,error.code||'QUOTE_UNAVAILABLE']);
        if(error instanceof ApiError)throw error;
        throw fail('QUOTE_UNAVAILABLE','Could not calculate the network cost. No payment was sent.',503);
      }
    },
    async cancel(principal,id) {
      return db.transaction(async client=>{await activePrincipal(client,principal);const row=await loadIntent(client,principal.wallet_id,id,true);
        if(!['preparing','quoted','expired','cancelled','rejected'].includes(row.status))throw fail('PAYMENT_TRACKING_REQUIRED','This payment is already authorized. Keep tracking its result.');
        if(['preparing','quoted'].includes(row.status)){await client.query("UPDATE tx_intents SET status='cancelled',updated_at=clock_timestamp() WHERE id=$1",[id]);await sponsorAccounting(client,id,'release');row.status='cancelled';}
        return present(client,row);
      });
    },
    async authorize(principal,id,body) {
      exactKeys(body,['quoteDigest','signature']);requireValue(typeof body.signature==='string'&&/^0x[0-9a-fA-F]{130}$/.test(body.signature),'Invalid payment signature');
      const initial=await loadIntent(db,principal.wallet_id,id);enabled(initial.fee_mode);
      if(initial.quote_digest!==body.quoteDigest)throw fail('PAYMENT_CHANGED','The payment changed; confirm it again');
      if(initial.status!=='quoted') {if(['authorized','submitted','ambiguous','included','confirmed','reverted','reorganized'].includes(initial.status))return present(db,initial);throw fail('QUOTE_EXPIRED','Request and confirm a new quote');}
      if(new Date(initial.expires_at)<=new Date())throw fail('QUOTE_EXPIRED','Request and confirm a new quote');
      if(initial.resolution_id)await verifyTagSnapshot(db,initial.resolution_id,provider.chain);
      await ensureFunds(initial);
      const saved=await account(db,principal.wallet_id,initial.account_id);
      validatePaymentOperation(initial.quote.operation,saved.descriptor,{...initial.quote,contract:initial.contract,recipient:initial.recipient,amountAtomic:initial.amount_atomic,feeMode:initial.fee_mode,feeToken:initial.fee_token});
      if(!same(initial.quote.paymaster,config.paymasterAddress))throw fail('PAYMENT_CONFIGURATION_CHANGED','The payment service changed; confirm a new quote');
      let signature;
      try{signature=paymentSignature(initial.quote.operation,saved.descriptor,initial.expires_at,body.signature);}catch{throw fail('PAYMENT_SIGNATURE_INVALID','The payment signature could not be verified',400);}
      const operation={...initial.quote.operation,signature};const hash=paymentHash(operation,saved.descriptor).toLowerCase();
      const head=initial.quote.observedBlock;
      return db.transaction(async client=>{await activePrincipal(client,principal);const row=await loadIntent(client,principal.wallet_id,id,true);
        if(row.status!=='quoted')return present(client,row);
        if(new Date(row.expires_at)<=new Date()||row.quote_digest!==body.quoteDigest)throw fail('QUOTE_EXPIRED','Request and confirm a new quote');
        if(row.resolution_id)await validateResolution(client,principal.wallet_id,row.resolution_id,row.recipient);
        await client.query('INSERT INTO user_operations(intent_id,user_operation_hash,operation,nonce,submitted_block) VALUES($1,$2,$3,$4,$5)',[id,hash,paymentOperation(operation,true),BigInt(operation.nonce).toString(),BigInt(head).toString()]);
        await client.query('INSERT INTO payment_outbox(intent_id) VALUES($1)',[id]);
        const updated=await client.query("UPDATE tx_intents SET status='authorized',updated_at=clock_timestamp() WHERE id=$1 RETURNING *,amount_atomic::text",[id]);
        return present(client,updated.rows[0]);
      });
    },
  };
}

export async function deliverPayments(db,config,provider=paymentProvider(config)) {
  const {rows}=await db.transaction(async client=>{
    const result=await client.query(`UPDATE payment_outbox SET lease_until=clock_timestamp()+interval '60 seconds' WHERE intent_id IN
      (SELECT intent_id FROM payment_outbox WHERE completed_at IS NULL AND (lease_until IS NULL OR lease_until<clock_timestamp()) ORDER BY created_at LIMIT 10 FOR UPDATE SKIP LOCKED) RETURNING intent_id`);return result;
  });
  for(const item of rows) {
    const op=(await db.query('SELECT * FROM user_operations WHERE intent_id=$1',[item.intent_id])).rows[0];
    if(op.attempted_at){await db.query('UPDATE payment_outbox SET completed_at=clock_timestamp() WHERE intent_id=$1',[item.intent_id]);continue;}
    const intent=await loadIntent(db,(await db.query('SELECT wallet_id FROM tx_intents WHERE id=$1',[item.intent_id])).rows[0].wallet_id,item.intent_id);
    if(new Date(intent.expires_at)<=new Date()) {
      await db.transaction(async client=>{const locked=await loadIntent(client,intent.wallet_id,intent.id,true);const current=(await client.query('SELECT attempted_at FROM user_operations WHERE intent_id=$1 FOR UPDATE',[intent.id])).rows[0];if(current.attempted_at)return;await client.query("UPDATE tx_intents SET status='expired',updated_at=clock_timestamp() WHERE id=$1",[intent.id]);await client.query('UPDATE payment_outbox SET completed_at=clock_timestamp() WHERE intent_id=$1',[intent.id]);await client.query("UPDATE user_operations SET actual_fee_atomic=0,actual_native_atomic=0,accounting_state='settled' WHERE intent_id=$1",[intent.id]);await sponsorAccounting(client,intent.id,'release');});
      continue;
    }
    const claimed=await db.query('UPDATE user_operations SET attempted_at=clock_timestamp() WHERE intent_id=$1 AND attempted_at IS NULL RETURNING intent_id',[item.intent_id]);
    if(!claimed.rowCount)continue;
    let status='ambiguous';
    try {const hash=await provider.submit(op.operation);if(hash?.toLowerCase()===op.user_operation_hash)status='submitted';}catch{ /* A lost acknowledgement is reconciled before any further action. */ }
    await db.transaction(async client=>{await client.query("UPDATE tx_intents SET status=$2,updated_at=clock_timestamp() WHERE id=$1 AND status='authorized'",[item.intent_id,status]);await client.query('UPDATE payment_outbox SET completed_at=clock_timestamp() WHERE intent_id=$1',[item.intent_id]);});
  }
  return rows.length;
}

export async function reconcilePayments(db,config,provider=paymentProvider(config)) {
  if(BigInt(await provider.chain('eth_chainId'))!==8453n)throw fail('PAYMENT_NETWORK_MISMATCH','Unexpected payment network',502);
  const currentHead=BigInt(await provider.chain('eth_blockNumber'));
  const {rows}=await db.query(`SELECT i.id,i.wallet_id FROM tx_intents i JOIN user_operations u ON u.intent_id=i.id
    WHERE (i.status IN ('authorized','submitted','ambiguous','included','reorganized') OR (i.status IN ('confirmed','reverted') AND u.block_number>=$1)) AND (u.checked_at IS NULL OR u.checked_at<clock_timestamp()-CASE WHEN i.status IN ('confirmed','reverted') THEN interval '1 minute' ELSE interval '2 seconds' END) ORDER BY u.checked_at NULLS FIRST LIMIT 20`,[(currentHead>256n?currentHead-256n:0n).toString()]);
  for(const item of rows) {
    const row=await loadIntent(db,item.wallet_id,item.id);const op=(await db.query('SELECT * FROM user_operations WHERE intent_id=$1',[item.id])).rows[0];
    try {
      const previousCanonical=op.block_hash ? await provider.chain('eth_getBlockByNumber',[`0x${BigInt(op.block_number).toString(16)}`,false]):null;
      if(op.block_hash&&(!previousCanonical||!same(previousCanonical.hash,op.block_hash))) {
        await db.transaction(client=>resetPaymentForReorg(client,item.wallet_id,item.id));
        continue;
      }
      if(op.block_hash&&['confirmed','reverted'].includes(row.status)&&op.accounting_state==='settled') {await db.query('UPDATE user_operations SET checked_at=clock_timestamp() WHERE intent_id=$1',[item.id]);continue;}
      let bundled;try{bundled=await provider.receipt(op.user_operation_hash);}catch{bundled=null;}
      const tx=bundled?.receipt?.transactionHash||op.transaction_hash;
      let receipt=tx?await provider.transactionReceipt(tx):null;
      if(receipt){const canonicalBlock=await provider.chain('eth_getBlockByNumber',[receipt.blockNumber,false]);if(!canonicalBlock||!same(canonicalBlock.hash,receipt.blockHash))receipt=null;}
      if(!receipt) {
        const proof=await provider.locateOperation(op,(await account(db,row.wallet_id,row.account_id)).descriptor,row.expires_at,op.submitted_block);
        if(proof.transactionHash)receipt=await provider.transactionReceipt(proof.transactionHash);
        else {
          await db.query('UPDATE user_operations SET search_next_block=$2,search_checkpoint_block=$3,search_checkpoint_hash=$4,expiry_observed_block=$5,checked_at=clock_timestamp() WHERE intent_id=$1',[item.id,proof.nextBlock,proof.checkpointBlock,proof.checkpointHash,proof.stopBlock]);
          if(proof.absent)await db.transaction(async client=>{const locked=await loadIntent(client,item.wallet_id,item.id,true);if(!['authorized','submitted','ambiguous','reorganized'].includes(locked.status))return;await client.query("UPDATE tx_intents SET status='expired',updated_at=clock_timestamp() WHERE id=$1",[item.id]);await sponsorAccounting(client,item.id,'release');await client.query("UPDATE user_operations SET actual_fee_atomic=0,actual_native_atomic=0,accounting_state='settled' WHERE intent_id=$1",[item.id]);});
        }
        if(!receipt)continue;
      }
      const block=await provider.chain('eth_getBlockByNumber',[receipt.blockNumber,false]);
      if(!block||!same(block.hash,receipt.blockHash))continue;
      const head=BigInt(await provider.chain('eth_blockNumber'));
      const result=inspectOperationReceipt(receipt,row,op,config);
      const state=result.success?(head-BigInt(receipt.blockNumber)+1n>=BigInt(config.indexConfirmations)?'confirmed':'included'):'reverted';
      await db.transaction(async client=>{
        const locked=await loadIntent(client,item.wallet_id,item.id,true);
        if(!active.includes(locked.status)&&!['confirmed','reverted'].includes(locked.status))return;
        await client.query("UPDATE user_operations SET transaction_hash=$2,block_number=$3,block_hash=$4,success=$5,receipt=$6,actual_fee_atomic=$7,actual_native_atomic=$8,accounting_state=$9,payment_log_index=$10,gas_log_indexes=$11,checked_at=clock_timestamp() WHERE intent_id=$1",[item.id,receipt.transactionHash.toLowerCase(),BigInt(receipt.blockNumber).toString(),receipt.blockHash.toLowerCase(),result.success,receipt,result.actualFeeAtomic,result.actualNativeAtomic,result.accountingState,result.paymentLogIndex??null,result.gasLogIndexes]);
        await client.query('UPDATE tx_intents SET status=$2,updated_at=CASE WHEN status<>$2 THEN clock_timestamp() ELSE updated_at END WHERE id=$1',[item.id,state]);
        if(result.accountingState==='settled')await sponsorAccounting(client,item.id,'settle',result.actualNativeAtomic,receipt.blockHash.toLowerCase());
        await client.query("UPDATE chain_events SET intent_id=$1,event_kind='payment' WHERE chain_id=8453 AND transaction_hash=$2 AND log_index=$3 AND sender=$4 AND recipient=$5 AND contract=$6 AND amount_atomic=$7",[item.id,receipt.transactionHash.toLowerCase(),result.paymentLogIndex??-1,row.sender,row.recipient,row.contract,row.amount_atomic]);
        if(result.gasLogIndexes.length)await client.query("UPDATE chain_events SET intent_id=$1,event_kind='gas' WHERE chain_id=8453 AND transaction_hash=$2 AND log_index=ANY($3::integer[])",[item.id,receipt.transactionHash.toLowerCase(),result.gasLogIndexes]);
      });
    } catch(error) {await db.query('UPDATE user_operations SET checked_at=clock_timestamp() WHERE intent_id=$1',[item.id]);if(error.code==='PAYMENT_PROVIDER_LIMITED')throw error;}
  }
  return rows.length;
}

export async function observeKnownPaymentReceipts(client,config,receipts) {
  for(const receipt of receipts) {
    for(const log of receipt.logs||[]) {
      if(!same(log.address,config.entryPoint))continue;
      let event;try{event=userOperationEvents.parseLog(log);}catch{continue;}
      if(event?.name!=='UserOperationEvent')continue;
      const found=await client.query('SELECT i.id,i.wallet_id FROM user_operations u JOIN tx_intents i ON i.id=u.intent_id WHERE u.user_operation_hash=$1',[event.args.userOpHash.toLowerCase()]);
      if(!found.rows.length)continue;
      const row=await loadIntent(client,found.rows[0].wallet_id,found.rows[0].id,true);
      const op=(await client.query('SELECT * FROM user_operations WHERE intent_id=$1',[row.id])).rows[0];
      const result=inspectOperationReceipt(receipt,row,{...op,transaction_hash:null},config);
      if(op.block_hash&&!same(op.block_hash,receipt.blockHash))await resetPaymentForReorg(client,row.wallet_id,row.id);
      await client.query(`UPDATE user_operations SET transaction_hash=$2,block_number=$3,block_hash=$4,success=$5,receipt=$6,actual_fee_atomic=$7,actual_native_atomic=$8,
        accounting_state=$9,payment_log_index=$10,gas_log_indexes=$11,checked_at=NULL WHERE intent_id=$1`,[row.id,receipt.transactionHash.toLowerCase(),BigInt(receipt.blockNumber).toString(),receipt.blockHash.toLowerCase(),result.success,receipt,result.actualFeeAtomic,result.actualNativeAtomic,result.accountingState,result.paymentLogIndex??null,result.gasLogIndexes]);
    }
  }
}

export async function resetPaymentForReorg(client,walletId,id) {
  const row=await loadIntent(client,walletId,id,true);
  if(!['included','confirmed','reverted'].includes(row.status))return;
  await client.query("UPDATE tx_intents SET status='reorganized',updated_at=clock_timestamp() WHERE id=$1",[id]);
  await client.query('UPDATE chain_events SET canonical=false WHERE intent_id=$1',[id]);
  await client.query('UPDATE index_cursors SET revision=revision+1 WHERE account_id IN (SELECT account_id FROM event_participants WHERE event_id IN (SELECT id FROM chain_events WHERE intent_id=$1))',[id]);
  await sponsorAccounting(client,id,'reorganize');
  await client.query("UPDATE user_operations SET block_hash=NULL,block_number=NULL,transaction_hash=NULL,receipt=NULL,actual_fee_atomic=NULL,actual_native_atomic=NULL,payment_log_index=NULL,gas_log_indexes='{}',success=NULL,search_next_block=NULL,search_checkpoint_block=NULL,search_checkpoint_hash=NULL,expiry_observed_block=NULL,accounting_state='pending',checked_at=NULL WHERE intent_id=$1",[id]);
}
