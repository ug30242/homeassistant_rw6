// Session-only snapshots: never persist HA responses or credentials to storage.
export function createStateCache() {
  let identity='',bucket;
  return {forConfig(config) {
    const next=JSON.stringify([config.revision,config.server,config.token,config.cleared]);
    if(next!==identity || !bucket) {
      identity=next;
      const entries={};let sequence=0;
      bucket={
        get(id){return entries[id];},
        version(id){return entries[id]?entries[id].version:0;},
        invalidate(id){entries[id]=Object.assign({},entries[id],{version:++sequence});},
        put(id,state,version){
          if(version!==undefined && version!==this.version(id))return false;
          // Lists need only display values, not large HA attribute collections.
          const snapshot={entity_id:id,state:state.state.slice(0,100),attributes:{unit_of_measurement:String((state.attributes||{}).unit_of_measurement||'').slice(0,100)}};
          entries[id]={state:snapshot,updatedAt:Date.now(),error:null,version:++sequence};return true;
        },
        fail(id,error,version){
          if(version!==this.version(id))return false;
          entries[id]=Object.assign({},entries[id],{error,version:++sequence});return true;
        }
      };
    }
    return bucket;
  }};
}

// Keep responses small with per-entity GETs; /api/states can contain thousands of
// unrelated entities. Two workers bound transport load while avoiding serial RTTs.
export async function refreshStates(api,entities,cache,{force=false,ttl=30000,active=()=>true,skip=()=>false,onUpdate=()=>{}}={}) {
  const selected=entities.filter(e=>{const entry=cache.get(e.id);return force||!entry||!entry.state||entry.error||Date.now()-entry.updatedAt>=ttl;});
  let index=0,updated=0,failed=0,error=null,halt=false;
  async function worker() {
    while(active()&&!halt&&index<selected.length) {
      const entity=selected[index++],version=cache.version(entity.id);
      if(skip(entity.id))continue;
      try {
        const state=await api.state(entity.id);if(!active())return;
        if(cache.put(entity.id,state,version)){updated++;onUpdate();}
      } catch(e) {
        if(!active())return;
        if(cache.fail(entity.id,e.message,version)){failed++;error=e;onUpdate();}
        if(e.status!==404)halt=true;
      }
    }
  }
  await Promise.all([worker(),worker()]);
  return {updated,failed,error};
}
