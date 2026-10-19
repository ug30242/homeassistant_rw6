import {validUrl} from './config.js';

export function safeError(code) {
  if (code===401 || code===403) return 'Authentication failed. Update token on Android.';
  if (code===404) return 'Entity or API not found.';
  if (code===429) return 'Server busy. Retry later.';
  if (code>=500) return 'Home Assistant server error.';
  return 'HTTP request failed ('+code+').';
}
export function createHaClient(fetch,config,{timeout=12000,timers={setTimeout,clearTimeout}}={}) {
  const base=validUrl(config.server,config.allowInsecure);
  let closed=false, sequence=0;
  const pending=[];
  function request(method,path,data) {
    if(closed) return Promise.reject(new Error('Request cancelled.'));
    const requestId=++sequence;
    return new Promise((resolve,reject)=>{
      let done=false;
      const entry={cancel:()=>finish(new Error('Request cancelled.'))};
      function finish(error,result) {
        if(done)return;done=true;timers.clearTimeout(timer);
        const i=pending.indexOf(entry);if(i>=0)pending.splice(i,1);
        if(error)reject(error);else resolve(result);
      }
      const timer=timers.setTimeout(()=>finish(new Error('Network timeout. Check watch network.')),timeout);
      pending.push(entry);
      try {
        fetch.fetch({url:base+path,method,header:{Authorization:'Bearer '+config.token,'Content-Type':'application/json'},
          data:method==='POST'?JSON.stringify(data):'',responseType:'text',
          success:response=>{
            if(done || closed)return;
            const code=Number(response.code);
            if(code<200 || code>=300 || !Number.isInteger(code)) {const error=new Error(safeError(code||0));error.status=code;finish(error);return;}
            try {
              const raw=response.data;
              if(typeof raw==='string' && raw.length>262144)throw new Error();
              const body=typeof raw==='string'?JSON.parse(raw):raw;
              if(body===null || typeof body!=='object')throw new Error();
              finish(null,{body,requestId,code});
            } catch (_) {finish(new Error('Invalid server response.'));}
          },fail:()=>finish(new Error('Network unavailable. Check Mi Fitness, DNS and TLS.'))});
      } catch (_) {finish(new Error('Watch fetch unavailable.'));}
    });
  }
  return {request,
    async ping(){const r=await request('GET','/api/');if(r.body.message!=='API running.')throw new Error('Unexpected Home Assistant server.');return r;},
    async state(id){
      if(!/^[a-z_]+\.[a-z0-9_]+$/.test(id))throw new Error('Invalid entity.');
      const r=await request('GET','/api/states/'+encodeURIComponent(id));
      if(r.body.entity_id!==id || typeof r.body.state!=='string')throw new Error('Entity response mismatch.');
      return r.body;
    },
    async service(domain,service,id,extra={}) {
      if(!/^[a-z_]+$/.test(domain) || !/^[a-z_]+$/.test(service))throw new Error('Invalid service.');
      return request('POST','/api/services/'+domain+'/'+service,Object.assign({},extra,{entity_id:id}));
    },
    close(){closed=true;pending.slice().forEach(entry=>entry.cancel());}};
}
