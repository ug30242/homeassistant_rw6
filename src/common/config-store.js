import {validateConfig} from './config.js';
import {checksum, utf8} from './codec.js';

export function createConfigStore(storage,coordinator={queue:Promise.resolve()}) {
  function get(key) {
    return new Promise((resolve,reject)=>storage.get({key,default:'',success:resolve,fail:()=>reject(new Error('Storage unavailable.'))}));
  }
  function set(key,value) {
    return new Promise((resolve,reject)=>storage.set({key,value,success:resolve,fail:()=>reject(new Error('Storage write failed.'))}));
  }
  async function record(slot) {
    const raw=await get('ha.config.'+slot);
    if (!raw) return null;
    const parsed=JSON.parse(raw);
    if (!parsed || !/^[a-f0-9]{32}$/.test(parsed.transfer) || checksum(utf8(parsed.payload))!==parsed.checksum) throw new Error('Damaged configuration.');
    return {config:validateConfig(JSON.parse(parsed.payload)),transfer:parsed.transfer,checksum:parsed.checksum,slot};
  }
  async function read() {
    const pointer=await get('ha.config.active');
    if (!pointer) return null;
    if (pointer!=='a' && pointer!=='b') throw new Error('Storage index damaged.');
    try { const result=await record(pointer); if (result) return result; } catch (_) {}
    // Only the previous committed slot is a fallback; a staged write never replaces a healthy active slot.
    const previous=await record(pointer==='a'?'b':'a');
    if (!previous) throw new Error('Configuration unavailable.');
    return previous;
  }
  function save(config,transfer,payload,digest) {
    const task=coordinator.queue.then(async()=>{
      validateConfig(config);
      const current=await read();
      if (current && config.revision===current.config.revision && current.checksum===digest) return current;
      if (current && config.revision<=current.config.revision) throw new Error('Stale configuration.');
      const slot=current && current.slot==='a'?'b':'a';
      const raw=JSON.stringify({transfer,checksum:digest,payload});
      await set('ha.config.'+slot,raw);
      if (await get('ha.config.'+slot)!==raw) throw new Error('Storage verification failed.');
      await set('ha.config.active',slot);
      if (await get('ha.config.active')!==slot) throw new Error('Storage verification failed.');
      return {config,transfer,checksum:digest,slot};
    });
    coordinator.queue=task.then(()=>{},()=>{});
    return task;
  }
  // Credential erase writes two empty records, then removes staging credentials from both slots.
  function clear() {
    const task=coordinator.queue.then(async()=>{
      await set('ha.config.a',''); await set('ha.config.b',''); await set('ha.config.active','');
      if (await get('ha.config.a') || await get('ha.config.b') || await get('ha.config.active')) throw new Error('Erase verification failed.');
    });
    coordinator.queue=task.then(()=>{},()=>{});
    return task;
  }
  return {read,save,clear};
}
