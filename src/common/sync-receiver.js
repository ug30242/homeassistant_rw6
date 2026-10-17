import {validateConfig, MAX_BYTES} from './config.js';
import {unbase64, fromUtf8, checksum} from './codec.js';
import {decodePhoneMessage} from './phone-wire.js';

export function createSyncReceiver({store,send,update=()=>{},now=()=>Date.now()}) {
  let live=true, draft=null, saving=false;
  function reply(packet,status,extra={}) {
    if (live) send(JSON.stringify(Object.assign({type:packet.type==='ha-hello'?'ha-ready':'ha-ack',version:1,id:packet.id,
      transfer:packet.transfer,phase:packet.type.slice(3),status},extra)));
  }
  async function receive(event) {
    if (!live) return;
    let p;
    try { p=decodePhoneMessage(event); } catch (_) { return; }
    if (p.type==='ha-hello') { reply(p,'ok'); return; }
    if (!['ha-begin','ha-chunk','ha-commit'].includes(p.type)) return;
    if (!/^[a-f0-9]{32}$/.test(p.transfer)) { reply(p,'invalid'); return; }
    if (saving) { reply(p,'busy'); return; }
    if (draft && now()-draft.updated>90000) draft=null;
    if (p.type==='ha-begin') {
      if (!Number.isInteger(p.total) || p.total<1 || p.total>64 || !Number.isInteger(p.bytes) || p.bytes<1 || p.bytes>MAX_BYTES || p.total!==Math.ceil(p.bytes/768) || !/^[a-f0-9]{8}$/.test(p.checksum) || !Number.isSafeInteger(p.revision) || p.revision<1) { reply(p,'invalid'); return; }
      try {
        const current=await store.read();
        if (!live) return;
        if (current && (p.revision<current.config.revision || (p.revision===current.config.revision && current.checksum!==p.checksum))) { reply(p,'stale'); return; }
        if (draft && draft.transfer===p.transfer) {
          if (draft.checksum!==p.checksum || draft.bytes!==p.bytes || draft.total!==p.total || draft.revision!==p.revision) { reply(p,'invalid'); return; }
          draft.updated=now(); reply(p,'ok'); return;
        }
        draft={transfer:p.transfer,revision:p.revision,total:p.total,bytes:p.bytes,checksum:p.checksum,chunks:[],updated:now()};
        update('Receiving configuration...'); reply(p,'ok');
      } catch (_) { reply(p,'error'); update('Storage unavailable.'); }
      return;
    }
    if (!draft || draft.transfer!==p.transfer) { reply(p,'invalid'); return; }
    draft.updated=now();
    if (p.type==='ha-chunk') {
      try {
        if (!Number.isInteger(p.index) || p.index<0 || p.index>=draft.total) throw new Error();
        const bytes=unbase64(p.data);
        if (bytes.length!==(p.index===draft.total-1?draft.bytes-768*p.index:768) || (draft.chunks[p.index] && draft.chunks[p.index]!==p.data)) throw new Error();
        draft.chunks[p.index]=p.data;
        reply(p,'ok',{index:p.index}); update('Received part '+(p.index+1)+' / '+draft.total);
      } catch (_) { reply(p,'invalid',{index:p.index}); }
      return;
    }
    try {
      let bytes=[];
      for (let i=0;i<draft.total;i++) {
        if (!draft.chunks[i]) throw new Error();
        bytes=bytes.concat(unbase64(draft.chunks[i]));
      }
      if (bytes.length!==draft.bytes || checksum(bytes)!==draft.checksum) throw new Error();
      const payload=fromUtf8(bytes),config=validateConfig(JSON.parse(payload));
      if (config.revision!==draft.revision) throw new Error();
      saving=true;
      try {
        await store.save(config,draft.transfer,payload,draft.checksum);
        // An erase must overwrite both slots, including the previous credentials.
        if (config.cleared) await store.clear();
        reply(p,'saved',{revision:config.revision}); update(config.cleared?'Credentials erased.':'Saved. Android can be closed.');
      } catch (_) { reply(p,'error'); update('Save failed. Retry sync.'); }
      finally { saving=false; }
    } catch (_) { reply(p,'invalid'); update('Incomplete configuration. Retry.'); }
  }
  return {receive,isSaving:()=>saving,close(){live=false;draft=null;}};
}
