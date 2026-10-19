import {test} from 'node:test';
import assert from 'node:assert/strict';
import {createStateCache,refreshStates} from '../src/common/state-cache.js';
import {config} from './helpers.mjs';
const state=(id,value='off')=>({entity_id:id,state:value,attributes:{}});
const entities=Array.from({length:8},(_,i)=>({id:'light.lamp_'+i}));

test('snapshots survive page reentry, expire by TTL and isolate changed configuration',async()=>{
  const shared=createStateCache(),value=config(),cache=shared.forConfig(value);let reads=0;
  const api={async state(id){reads++;return state(id);}};
  await refreshStates(api,entities,cache);assert.equal(reads,8);
  assert.equal(shared.forConfig({...value}),cache);
  await refreshStates(api,entities,cache);assert.equal(reads,8,'reentry and filters reuse fresh snapshots');
  await refreshStates(api,entities,cache,{force:true});assert.equal(reads,16,'explicit refresh bypasses TTL');
  await refreshStates(api,entities,cache,{ttl:0});assert.equal(reads,24,'expired snapshots refresh');
  for(const change of [{revision:11},{token:'other-token'},{server:'https://other.test'},{cleared:true}]){
    const next=shared.forConfig({...value,...change});assert.equal(next.get(entities[0].id),undefined);
  }
});

test('background requests are limited to two; cancellation prevents cache writes and more requests',async()=>{
  const cache=createStateCache().forConfig(config()),pending=[];let active=true;
  const api={state(id){return new Promise(resolve=>pending.push({id,resolve}));}};
  const refresh=refreshStates(api,entities,cache,{active:()=>active});
  assert.equal(pending.length,2,'two requests start without waiting for a serial round trip');
  pending[0].resolve(state(pending[0].id));await new Promise(setImmediate);
  assert.equal(pending.length,3,'one worker advances when a response arrives');
  active=false;pending.slice(1).forEach(p=>p.resolve(state(p.id)));await refresh;
  assert.equal(pending.length,3);assert.equal(cache.get(entities[1].id),undefined);
});

test('failed refresh retains old value, marks stale, and stops transport failures without querying all entities',async()=>{
  const cache=createStateCache().forConfig(config());cache.put(entities[0].id,state(entities[0].id));let reads=0;
  const result=await refreshStates({async state(){reads++;throw new Error('Network unavailable.');}},entities,cache,{force:true});
  assert.equal(reads,2);assert.equal(result.failed,2);assert.equal(cache.get(entities[0].id).state.state,'off');assert.equal(cache.get(entities[0].id).error,'Network unavailable.');
  const notFound=new Error('Entity not found.');notFound.status=404;reads=0;
  await refreshStates({async state(id){reads++;if(id===entities[0].id)throw notFound;return state(id);}},entities,cache,{force:true});
  assert.equal(reads,8,'a removed entity must not block other entities');
});

test('late background snapshot cannot overwrite a control result and active controls are skipped',async()=>{
  const cache=createStateCache().forConfig(config());let respond;
  const refresh=refreshStates({state(id){return new Promise(resolve=>respond=()=>resolve(state(id)));}},entities.slice(0,1),cache);
  cache.invalidate(entities[0].id);cache.put(entities[0].id,state(entities[0].id,'on'));respond();await refresh;
  assert.equal(cache.get(entities[0].id).state.state,'on');
  await refreshStates({async state(){assert.fail('active control must not be polled');}},entities.slice(0,1),cache,{force:true,skip:()=>true});
});
