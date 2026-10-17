import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createHaClient} from '../src/common/ha-client.js';
import {validateConfig,displayName,validUrl} from '../src/common/config.js';
import {commands,execute} from '../src/common/entities.js';
import {utf8,fromUtf8,checksum,base64,unbase64} from '../src/common/codec.js';
import {config} from './helpers.mjs';

test('UTF8, Base64, CRC32 match published vectors and split unicode',()=>{
  assert.equal(checksum(utf8('123456789')),'cbf43926');
  for(const value of ['Hello','거실 💡','A'.repeat(767)+'한글']){const bytes=utf8(value);assert.deepEqual(bytes,[...Buffer.from(value)]);assert.equal(base64(bytes),Buffer.from(value).toString('base64'));let decoded=[];for(let i=0;i<bytes.length;i+=768)decoded=decoded.concat(unbase64(base64(bytes.slice(i,i+768))));assert.equal(fromUtf8(decoded),value);}
  assert.throws(()=>unbase64('YR=='));assert.throws(()=>unbase64('YQ='));
});
test('schema validates URL, domains, token size, duplicate IDs and text limits',()=>{
  assert.equal(validUrl('https://ha.example.test/base///',false),'https://ha.example.test/base');
  for(const url of ['http://ha.test','https://user:pass@ha.test','https://ha.test?token=x','https://ha.test/#x','https://ha.test:99999','https://ha.test\\evil'])assert.throws(()=>validUrl(url,false));
  assert.equal(validUrl('http://192.168.1.2:8123',true),'http://192.168.1.2:8123');
  assert.equal(displayName(config().entities[0]),'living room');assert.equal(displayName({id:'sensor.temp',name:'Temperature'}),'Temperature');
  for(const value of [config({token:'bad\nheader'}),config({token:'x'.repeat(4097)}),config({entities:[...config().entities,...config().entities]}),config({entities:[{...config().entities[0],id:'climate.home'}]})])assert.throws(()=>validateConfig(value));
});
test('watch uses direct authenticated GET/POST paths and fetches actual state',async()=>{
  const calls=[];const native={fetch(options){calls.push(options);options.success({code:200,data:JSON.stringify(options.url.endsWith('/api/')?{message:'API running.'}:options.method==='POST'?[]:{entity_id:'light.living_room',state:'off',attributes:{}})});}};
  const client=createHaClient(native,config());await client.ping();await client.state('light.living_room');await client.service('light','turn_on','light.living_room');client.close();
  assert.deepEqual(calls.map(c=>c.method),['GET','GET','POST']);assert(calls.every(c=>c.header.Authorization==='Bearer test-token-not-valid'));assert.equal(JSON.parse(calls[2].data).entity_id,'light.living_room');
  assert.equal(calls[2].url,'https://ha.example.test/api/services/light/turn_on');
});
test('HTTP authentication, offline, malformed responses, mismatched entities and timeout are safe',async()=>{
  for(const code of [401,403,404,429,500]){
    const c=createHaClient({fetch(o){o.success({code,data:'token-should-never-appear'});}},config());await assert.rejects(c.ping(),error=>!error.message.includes('token-should-never-appear'));c.close();
  }
  const wrong=createHaClient({fetch(o){o.success({code:200,data:'{"entity_id":"light.other","state":"on"}'});}},config());await assert.rejects(wrong.state('light.living_room'),/mismatch/);wrong.close();
  const malformed=createHaClient({fetch(o){o.success({code:200,data:'not-json'});}},config());await assert.rejects(malformed.ping(),/Invalid/);malformed.close();
  let trigger,late;const c=createHaClient({fetch(o){late=o;}},config(),{timers:{setTimeout(fn){trigger=fn;return 1;},clearTimeout(){}}});const pending=c.ping();trigger();await assert.rejects(pending,/timeout/);late.success({code:200,data:'{"message":"API running."}'});c.close();
  const cancel=createHaClient({fetch(){}},config());const stopped=cancel.ping();cancel.close();await assert.rejects(stopped,/cancelled/);
});
test('supported domains expose correct services and sensors are read only',()=>{
  const entity=domain=>({id:domain+'.test'}),state={state:'on',attributes:{supported_features:15,supported_color_modes:['brightness']}};
  for(const d of ['light','switch','fan'])assert.deepEqual(commands(entity(d),state).slice(0,2).map(c=>c.service),['turn_on','turn_off']);
  assert(commands(entity('light'),state).some(c=>c.extra.brightness_pct===50));
  assert.deepEqual(commands(entity('cover'),state).slice(0,3).map(c=>c.service),['open_cover','close_cover','stop_cover']);
  assert(commands(entity('cover'),state).some(c=>c.extra.position===50));
  for(const d of ['scene','script'])assert.equal(commands(entity(d),state)[0].service,'turn_on');
  assert.deepEqual(commands(entity('sensor'),state),[]);assert.deepEqual(commands(entity('cover'),{state:'open',attributes:{supported_features:0}}),[]);
  assert.deepEqual(commands(entity('light'),{state:'unavailable'}),[]);
});
test('service acceptance alone is unconfirmed; scene/script require changed execution evidence',async()=>{
  const entity={id:'light.test'},before={state:'off',attributes:{}},on=commands(entity,before)[0];let reads=0;
  const client={async service(){},async state(){reads++;return before;}};
  const result=await execute(client,entity,on,before,{delay:async()=>{}});assert.equal(result.confirmed,false);assert.equal(reads,3);
  client.state=async()=>({state:'on',attributes:{}});assert.equal((await execute(client,entity,on,before)).confirmed,true);
  for(const d of ['scene','script']){
    const e={id:d+'.test'},b={state:d==='scene'?'2020-01-01T00:00:00Z':'off',attributes:{last_triggered:'2020-01-01T00:00:00Z'}};
    client.state=async()=>b;assert.equal((await execute(client,e,commands(e,b)[0],b,{delay:async()=>{}})).confirmed,false);
    client.state=async()=>({state:d==='scene'?new Date().toISOString():'off',attributes:{last_triggered:new Date().toISOString()}});
    assert.equal((await execute(client,e,commands(e,b)[0],b)).confirmed,true);
  }
  await assert.rejects(execute(client,entity,{service:'toggle'},before),/unavailable/);
});
