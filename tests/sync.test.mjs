import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createConfigStore} from '../src/common/config-store.js';
import {createSyncReceiver} from '../src/common/sync-receiver.js';
import {openPhoneChannel} from '../src/common/phone-channel.js';
import {decodePhoneMessage} from '../src/common/phone-wire.js';
import {config,memoryStorage,packets} from './helpers.mjs';

function fixture(storage=memoryStorage()) {
  const store=createConfigStore(storage),sent=[];
  const receiver=createSyncReceiver({store,send:raw=>sent.push(JSON.parse(raw))});
  return {store,storage,sent,receiver,async send(parts){for(const p of parts)await receiver.receive({data:{data:JSON.stringify(p)}});}};
}
test('full unicode configuration is committed only after all chunks and readback',async()=>{
  const f=fixture();const value=config({token:'x'.repeat(3500)}),parts=packets(value);
  await f.send(parts.slice(0,-1));assert.equal(await f.store.read(),null);assert.equal(f.storage.writes,0);
  await f.send(parts.slice(-1));assert.equal(f.sent.at(-1).status,'saved');assert.equal((await f.store.read()).config.entities[0].name,'거실');
  assert.equal((await f.store.read()).config.token,value.token);assert(f.sent.every(p=>!JSON.stringify(p).includes(value.token)));
});
test('duplicate chunk, commit and restarted transfer do not duplicate or rewrite config',async()=>{
  const f=fixture(),parts=packets();await f.send([parts[0],parts[1],parts[1],parts[2]]);assert.equal(f.sent.at(-1).status,'saved');const writes=f.storage.writes;
  await f.send([parts.at(-1)]);assert.equal(f.sent.at(-1).status,'saved');assert.equal(f.storage.writes,writes);
  await f.send(packets(config(),'b'.repeat(32)));assert.equal(f.sent.at(-1).status,'saved');assert.equal(f.storage.writes,writes);
});
test('missing, altered, conflicting and oversized parts never replace last valid config',async()=>{
  const f=fixture();await f.send(packets());const old=(await f.store.read()).config;
  const parts=packets(config({revision:11,token:'z'.repeat(3500)}),'b'.repeat(32));
  await f.send([parts[0],parts[1],parts.at(-1)]);assert.equal(f.sent.at(-1).status,'invalid');assert.deepEqual((await f.store.read()).config,old);
  await f.send([{...parts[1],data:'YQ=='}]);assert.equal(f.sent.at(-1).status,'invalid');
  await f.send([{...parts[0],bytes:49153}]);assert.equal(f.sent.at(-1).status,'invalid');
  await f.send(packets(config({revision:9}),'c'.repeat(32)).slice(0,1));assert.equal(f.sent.at(-1).status,'stale');
  await f.send(packets(config({token:'different'}),'d'.repeat(32)).slice(0,1));assert.equal(f.sent.at(-1).status,'stale');
  assert.deepEqual((await f.store.read()).config,old);
});
test('whole replacement removes deleted entities; empty selection is valid',async()=>{
  const f=fixture();await f.send(packets());await f.send(packets(config({revision:11,entities:[]}),'b'.repeat(32)));
  assert.equal(f.sent.at(-1).status,'saved');assert.deepEqual((await f.store.read()).config.entities,[]);
});
test('storage and pointer failures preserve the previous active configuration',async()=>{
  for(const key of ['ha.config.b','ha.config.active']){
    const f=fixture();await f.send(packets());f.storage.failKey=key;
    await f.send(packets(config({revision:11,token:'replacement-token'}),'b'.repeat(32)));
    assert.equal(f.sent.at(-1).status,'error');assert.equal((await f.store.read()).config.revision,10);
    const reboot=createConfigStore(f.storage);assert.equal((await reboot.read()).config.token,'test-token-not-valid');
  }
});
test('uncommitted staging is ignored on reboot; corrupt active falls back to previous record',async()=>{
  const f=fixture();await f.send(packets());f.storage.values['ha.config.b']='invalid';assert.equal((await createConfigStore(f.storage).read()).config.revision,10);
  await f.send(packets(config({revision:11}),'b'.repeat(32)));f.storage.values['ha.config.b']='invalid';assert.equal((await f.store.read()).config.revision,10);
});
test('credential erase clears both persisted copies and supports retried commit',async()=>{
  const f=fixture();await f.send(packets());await f.send(packets(config({revision:11}),'b'.repeat(32)));
  const erase=packets(config({revision:12,cleared:true,server:'',token:'',entities:[]}),'c'.repeat(32));await f.send(erase);
  assert.equal(f.sent.at(-1).status,'saved');assert.equal(await f.store.read(),null);assert(!JSON.stringify(f.storage.values).includes('test-token-not-valid'));
  await f.send([erase.at(-1)]);assert.equal(f.sent.at(-1).status,'saved');
});
test('bounded native envelopes, retained callbacks, and reply 1006 regression',async()=>{
  const hello={type:'ha-hello',version:1,id:'a'.repeat(32)},raw=JSON.stringify(hello);
  for(const input of [hello,raw,{data:raw},{data:{data:raw}},JSON.stringify(raw),{data:new TextEncoder().encode(raw)}])assert.deepEqual(decodePhoneMessage(input),hello);
  assert.throws(()=>decodePhoneMessage('x'.repeat(2049)));
  const sent=[],messages=[];const connect={send({data,fail}){assert.equal(typeof data,'object');sent.push(JSON.parse(data.data));fail({},1006);},getReadyState({success}){success({status:1});}};
  const old=openPhoneChannel(connect,{store:{},update(){throw new Error('old page callback');},connection(){throw new Error('old page callback');}});
  const current=openPhoneChannel(connect,{store:{},update(){},connection:text=>messages.push(text)});old.close();await connect.onmessage({data:raw});assert.equal(sent.at(-1).type,'ha-ready');assert(messages.at(-1).includes('1006'));
  current.close();await connect.onmessage({data:raw});assert.equal(sent.length,1);
});
test('expired incomplete transfer is rejected without touching storage',async()=>{
  const storage=memoryStorage(),store=createConfigStore(storage),sent=[];let time=0;
  const receiver=createSyncReceiver({store,now:()=>time,send:r=>sent.push(JSON.parse(r))});const p=packets();await receiver.receive(p[0]);time=90001;await receiver.receive(p[1]);assert.equal(sent.at(-1).status,'invalid');assert.equal(storage.writes,0);
});
test('erase from another page waits for in-flight save and cannot reintroduce credentials',async()=>{
  const native=memoryStorage(),coordinator={queue:Promise.resolve()};
  const receiverStore=createConfigStore(native,coordinator),settingsStore=createConfigStore(native,coordinator);
  let release;const original=native.set.bind(native);let delayed=true;
  native.set=options=>{if(delayed&&options.key==='ha.config.a'){delayed=false;release=()=>original(options);}else original(options);};
  const sent=[],receiver=createSyncReceiver({store:receiverStore,send:raw=>sent.push(JSON.parse(raw))});const parts=packets();
  for(const p of parts.slice(0,-1))await receiver.receive(p);
  const commit=receiver.receive(parts.at(-1));
  for(let i=0;i<10&&!release;i++)await Promise.resolve();assert(release);assert(receiver.isSaving());
  receiver.close();const erase=settingsStore.clear();release();await Promise.all([commit,erase]);
  assert.equal(await settingsStore.read(),null);assert(!JSON.stringify(native.values).includes('test-token-not-valid'));assert(!sent.some(p=>p.status==='saved'));
});
