import test, { before, after } from 'node:test';
import assert from 'node:assert/strict';
import { randomBytes, randomUUID, generateKeyPairSync, createHash } from 'node:crypto';
import { createServer } from 'node:http';
import pg from 'pg';
import { Wallet } from 'ethers';
import { createWalletSession } from '../../packages/wallet-core/src/index.js';
import { createApp } from '../app.js';
import { loadConfig } from '../lib/config.js';
import { migrationFiles } from '../lib/migrations.js';
import { MidoriClient } from '../lib/client.js';

const enabled = !!process.env.MIDORI_TAG_TEST_DATABASE_URL;
const scenario = (name, action) => test(name, { skip: !enabled }, action);
const schema = `midori_tag_test_${randomBytes(6).toString('hex')}`;
let owner, runtime, db, server, origin, people, legacyBefore;
const digest = value => createHash('sha256').update(value).digest('hex');
const bridge = pool => ({
  query: (sql, args) => pool.query(sql, args),
  ready: async () => (await pool.query('SELECT version,checksum FROM schema_migrations ORDER BY version')).rows,
  async transaction(action) { const c = await pool.connect(); try { await c.query('BEGIN'); const result = await action(c); await c.query('COMMIT'); return result; } catch (error) { await c.query('ROLLBACK'); throw error; } finally { c.release(); } },
});

async function identity() {
  const id = randomUUID(), accountId = randomUUID(), deviceId = randomUUID(), sessionId = randomUUID();
  const pair = generateKeyPairSync('ed25519'), token = randomBytes(32).toString('base64url'), signer = Wallet.createRandom();
  const wallet = await createWalletSession(signer.mnemonic.phrase, { chainId: 8453 });
  const descriptor = wallet.descriptor; wallet.dispose();
  await owner.query('INSERT INTO wallets(id,public_key) VALUES($1,$2)', [id, randomBytes(32).toString('base64url')]);
  await owner.query('INSERT INTO devices(id,wallet_id,public_key) VALUES($1,$2,$3)', [deviceId,id,pair.publicKey.export({type:'spki',format:'der'}).subarray(-32).toString('base64url')]);
  await owner.query("INSERT INTO sessions(id,wallet_id,device_id,token_hash,expires_at) VALUES($1,$2,$3,$4,clock_timestamp()+interval '1 hour')",[sessionId,id,deviceId,digest(token)]);
  await owner.query('INSERT INTO chain_accounts(id,wallet_id,chain_id,address,owner_address,account_index,descriptor) VALUES($1,$2,8453,$3,$4,0,$5)',[accountId,id,descriptor.address.toLowerCase(),descriptor.ownerAddress.toLowerCase(),descriptor]);
  return { id, accountId, signer, descriptor, session: {walletId:id,deviceId,sessionId,token}, privateKey:pair.privateKey };
}

before(async () => {
  if (!enabled) return;
  assert.match(schema,/^midori_tag_test_[a-f0-9]{12}$/);
  const options = { connectionString:process.env.MIDORI_TAG_TEST_DATABASE_URL,options:`-c search_path=${schema},pg_catalog`,max:8,statement_timeout:10000 };
  owner = new pg.Pool(options);
  await owner.query(`CREATE SCHEMA "${schema}"`);
  const files = await migrationFiles();
  await owner.query('CREATE TABLE schema_migrations(version text PRIMARY KEY,checksum text NOT NULL)');
  for (const file of files.filter(f=>f.version!=='007_permanent_tags.sql')) { await owner.query(file.sql); await owner.query('INSERT INTO schema_migrations VALUES($1,$2)',[file.version,file.checksum]); }
  people = await Promise.all(Array.from({length:7},identity));
  await owner.query('INSERT INTO tag_reservations(alias,wallet_id) VALUES($1,$3),($2,$3)',['legacy-old','legacy-current',people[0].id]);
  await owner.query('INSERT INTO wallet_tags(alias,wallet_id,account_id,revision) VALUES($1,$2,$3,5)',['legacy-current',people[0].id,people[0].accountId]);
  legacyBefore = (await owner.query('SELECT * FROM wallet_tags')).rows;
  const migration = files.find(f=>f.version==='007_permanent_tags.sql');assert.ok(migration);
  await owner.query(migration.sql);await owner.query('INSERT INTO schema_migrations VALUES($1,$2)',[migration.version,migration.checksum]);
  const runtimeUrl=process.env.MIDORI_TAG_TEST_RUNTIME_DATABASE_URL||process.env.MIDORI_TAG_TEST_DATABASE_URL,role=decodeURIComponent(new URL(runtimeUrl).username);assert.match(role,/^[a-z][a-z0-9_]+$/);
  await owner.query(`GRANT USAGE ON SCHEMA "${schema}" TO "${role}"`);
  await owner.query(`GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA "${schema}" TO "${role}"`);
  await owner.query(`GRANT USAGE,SELECT ON ALL SEQUENCES IN SCHEMA "${schema}" TO "${role}"`);
  runtime = new pg.Pool({...options,connectionString:runtimeUrl});db=bridge(runtime);
  const chain=async method=>{if(method==='eth_chainId')return '0x2105';if(method==='eth_blockNumber')return '0x1';if(method==='eth_getCode')return '0x';throw Error('Unexpected chain request');};
  const config=loadConfig({DATABASE_URL:runtimeUrl,MIDORI_RATE_LIMIT:'10000',MIDORI_PUBLIC_ORIGIN:'http://localhost:5173'});
  server=createServer(createApp({db,config,provider:{chain}}));await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));origin=`http://127.0.0.1:${server.address().port}`;
  for(const person of people)person.client=new MidoriClient({origin,privateKey:person.privateKey,session:person.session});
});
after(async()=>{if(server)await new Promise(resolve=>server.close(resolve));if(runtime)await runtime.end();if(owner){if(/^midori_tag_test_[a-f0-9]{12}$/.test(schema))await owner.query(`DROP SCHEMA "${schema}" CASCADE`);await owner.end();}});
const options=(p,alias)=>p.client.request('POST','/api/v1/tag/options',{alias,accountId:p.accountId});
const proof=async(p,challenge)=>({id:challenge.id,signature:await p.signer.signMessage(challenge.message)});
const verify=(p,body)=>p.client.request('POST','/api/v1/tag/verify',body);

scenario('permanent tags preserve existing identities and prevent SQL mutation or recycling',async()=>{
  assert.deepEqual((await owner.query('SELECT * FROM wallet_tags')).rows,legacyBefore);
  for(const sql of ["UPDATE wallet_tags SET alias='renamed' WHERE alias='legacy-current'", "UPDATE wallet_tags SET revision=revision+1 WHERE alias='legacy-current'", "DELETE FROM wallet_tags WHERE alias='legacy-current'", "UPDATE tag_reservations SET alias='recycled' WHERE alias='legacy-old'", "DELETE FROM tag_reservations WHERE alias='legacy-old'"])
    await assert.rejects(runtime.query(sql),{code:'23514'});
  await assert.rejects(runtime.query("INSERT INTO wallet_tags(alias,wallet_id,account_id) VALUES('legacy-old',$1,$2)",[people[1].id,people[1].accountId]),{code:'23503'});
  await assert.rejects(options(people[1],'$LEGACY-OLD'),{status:409,code:'TAG_TAKEN'});
  await owner.query("UPDATE wallet_tags SET active=false WHERE alias='legacy-current'");
  await assert.rejects(options(people[0],'new-identity'),{status:409,code:'TAG_IMMUTABLE'});
  await owner.query("UPDATE wallet_tags SET active=true WHERE alias='legacy-current'");
  assert.equal((await people[0].client.request('GET','/api/v1/tag')).tag.alias,'legacy-current');
});
scenario('registration validates name, uniqueness and owner proof, then rejects a second binding',async()=>{
  const a=people[1],b=people[2];
  for(const alias of ['$admin','$$bad','a b','-bad','$'+'a'.repeat(64)])await assert.rejects(options(a,alias),{status:400});
  const challenge=await options(a,'$Stable-Tag'),stale=await options(a,'different-tag');
  assert.equal(challenge.alias,'stable-tag');assert.match(challenge.message,/@stable-tag\n/);
  await assert.rejects(verify(a,{id:challenge.id,signature:await b.signer.signMessage(challenge.message)}),{status:400});
  assert.equal((await owner.query('SELECT count(*)::int AS n FROM wallet_tags WHERE wallet_id=$1',[a.id])).rows[0].n,0);
  const result=await verify(a,await proof(a,challenge));assert.equal(result.tag.alias,'stable-tag');assert.equal(result.tag.revision,'1');
  await assert.rejects(options(a,'replacement'),{status:409,code:'TAG_IMMUTABLE'});
  await assert.rejects(options(a,'$STABLE-TAG'),{status:409,code:'TAG_IMMUTABLE'});
  await assert.rejects(verify(a,await proof(a,stale)),{status:409,code:'TAG_IMMUTABLE'});
  await assert.rejects(options(b,'@STABLE-TAG'),{status:409,code:'TAG_TAKEN'});
  const resolution=await b.client.request('POST','/api/v1/tags/resolve',{alias:'$STABLE-TAG',chainId:8453});assert.equal(resolution.alias,'stable-tag');assert.equal(resolution.address,a.descriptor.address);
  assert.equal((await a.client.request('GET','/api/v1/tag')).tag.revision,'1');
});
scenario('concurrent claims allow one alias owner and one lifetime assignment per wallet',async()=>{
  const a=people[3],b=people[4];const challenges=await Promise.all([options(a,'race-tag'),options(b,'$RACE-TAG')]);
  const results=await Promise.allSettled([verify(a,await proof(a,challenges[0])),verify(b,await proof(b,challenges[1]))]);assert.equal(results.filter(r=>r.status==='fulfilled').length,1);assert.equal(results.find(r=>r.status==='rejected').reason.code,'TAG_TAKEN');
  const c=people[5],choices=await Promise.all([options(c,'first-choice'),options(c,'second-choice')]);
  const one=await Promise.allSettled(choices.map(async choice=>verify(c,await proof(c,choice))));assert.equal(one.filter(r=>r.status==='fulfilled').length,1);assert.equal(one.find(r=>r.status==='rejected').reason.code,'TAG_IMMUTABLE');
  assert.equal((await owner.query('SELECT count(*)::int AS n FROM wallet_tags WHERE wallet_id=$1',[c.id])).rows[0].n,1);
  assert.equal((await owner.query('SELECT count(*)::int AS n FROM tag_reservations WHERE wallet_id=$1',[c.id])).rows[0].n,1);
});
