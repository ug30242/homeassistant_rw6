// Run the actual packaged component entrypoints/templates with native API doubles.
// This is compiled-code verification, not a firmware/network or visual rendering claim.
const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const JSZip=require('jszip');
const crypto=require('node:crypto');
const tick=()=>new Promise(resolve=>setImmediate(resolve));
async function flush(){for(let i=0;i<25;i++)await tick();}
async function smokeRpk(file){
  const zip=await JSZip.loadAsync(fs.readFileSync(file));
  const manifest=JSON.parse(await zip.file('manifest.json').async('string'));
  const config={schema:1,revision:10,server:'https://ha.example.test',token:'test-token-not-valid',allowInsecure:false,cleared:false,refreshSeconds:30,
    entities:[{id:'light.living_room',name:'Living room',icon:'light',group:'Home',favorite:true,order:0},{id:'sensor.temp',name:'Temperature',icon:'sensor',group:'Home',favorite:true,order:1}]};
  const payload=JSON.stringify(config);
  function crc(bytes){let c=0xffffffff;for(const b of bytes){c^=b;for(let j=0;j<8;j++)c=(c>>>1)^((c&1)?0xedb88320:0);}return ((c^0xffffffff)>>>0).toString(16).padStart(8,'0');}
  const trees={};
  for(const [route,entry] of Object.entries(manifest.router.pages)){
    const scheduled=new Map(),sent=[],requests=[],navigation=[];let timer=0;
    const storage={'ha.config.active':'a','ha.config.a':JSON.stringify({transfer:'a'.repeat(32),checksum:crc(Buffer.from(payload)),payload})};
    let light='off',hold=false;const held=[];
    const connect={send({data,success}){assert.equal(typeof data,'object');assert.equal(typeof data.data,'string');sent.push(JSON.parse(data.data));if(success)success();},getReadyState({success}){success({status:1});}};
    const native={
      '@app-module/system.storage':{get({key,default:fallback='',success}){success(storage[key]===undefined?fallback:storage[key]);},set({key,value,success}){storage[key]=value;success();}},
      '@app-module/system.router':{push(data){navigation.push(data);},back(){}},
      '@app-module/system.interconnect':{instance(){return connect;}},
      '@app-module/system.fetch':{fetch(options){requests.push(options);assert.equal(options.header.Authorization,'Bearer test-token-not-valid');
        if(hold){held.push(options);return;}
        let body;if(options.url.endsWith('/api/'))body={message:'API running.'};else if(options.method==='POST'){if(options.url.endsWith('turn_on'))light='on';if(options.url.endsWith('turn_off'))light='off';body=[];}
        else{const id=decodeURIComponent(options.url.split('/').at(-1));body={entity_id:id,state:id==='sensor.temp'?'22.1':light,attributes:id==='sensor.temp'?{unit_of_measurement:'C'}:{supported_color_modes:['brightness'],brightness:128}};}
        options.success({code:200,data:JSON.stringify(body)});
      }}
    };
    const context=vm.createContext({console:{log(){},warn(){},error(){}}});
    context.$app_require$=name=>{assert(Object.hasOwn(native,name),'Unexpected native import '+name);return native[name];};
    context.setTimeout=context.setInterval=fn=>{scheduled.set(++timer,fn);return timer;};
    context.clearTimeout=context.clearInterval=id=>scheduled.delete(id);
    context.aiot={__ce__(tag,config,children){return {tag,opts:config.__opts__,children};},__ci__(config,render){return config.__opts__.shown()?render():[];},__cf__(config,render){return config.__opts__.exp().map((value,index)=>render(index,value));}};
    function evaluate(source,name){return vm.runInContext('('+source.replace(/^export default /,'')+')',context,{filename:name,timeout:2000});}
    const app={};evaluate(await zip.file('app.js').async('string'),'app.js')(context,context,context,app,{});if(app.default.onCreate)app.default.onCreate();
    const compiled=route+'/'+entry.component+'.js',exports={};evaluate(await zip.file(compiled).async('string'),compiled)(context,context,context,exports,{});
    const result={};exports.entry(result);const definition=result.default,page=Object.assign({},definition,definition.data,{entityId:'light.living_room'});
    assert.equal(definition.template(page).tag,'div','First frame failed');
    if(page.onInit)await page.onInit();if(page.onShow)await page.onShow();await flush();
    function nodes(){const list=[];function walk(n){if(Array.isArray(n)){n.forEach(walk);return;}if(!n)return;list.push(n);(n.children||[]).forEach(walk);}walk(definition.template(page));return list;}
    const visible=nodes();assert(visible.some(n=>n.opts.events&&n.opts.events.click),'Missing interactive controls');
    if(route==='pages/index'){
      assert.equal(page.items.length,2);assert(page.items[0].canPower);assert.equal(page.items[1].canPower,false);
      // Exercise compiled event binding, including the service string argument.
      const on=visible.find(n=>n.tag==='input'&&(typeof n.opts.value==='function'?n.opts.value():n.opts.value)==='ON');assert(on);on.opts.events.click({});await flush();
      assert.equal(light,'on');assert.equal(page.status,'State confirmed.');assert(requests.some(r=>r.method==='POST'));
      if(page._cache){
        // Returning from a page and switching filters reuse recent snapshots.
        const count=requests.length;page.onHide();page.onShow();await flush();assert.equal(requests.length,count);
        page.cycleFilter();await flush();assert.equal(requests.length,count);
        // A slow background refresh must allow controls. Its older response must
        // never overwrite the control's confirmed value or message.
        hold=true;const refreshing=page.refresh();assert.equal(held.length,2);
        assert(page.items[0].canPower);hold=false;page._lastTap=0;await page.power(0,'turn_off');
        assert.equal(light,'off');assert.equal(page.status,'State confirmed.');
        for(const request of held.splice(0)){
          const id=decodeURIComponent(request.url.split('/').at(-1));
          request.success({code:200,data:JSON.stringify({entity_id:id,state:'on',attributes:{}})});
        }
        await refreshing;await flush();assert(page.items[0].state.startsWith('off'));assert.equal(page.status,'State confirmed.');
        assert(page.items[0].checked.startsWith('Checked '));
        // Failed requests retain values and mark them stale.
        hold=true;const failing=page.refresh();for(const request of held.splice(0))request.fail();
        await failing;assert(page.items[0].state.startsWith('STALE | off'));assert(!page.items[0].canPower);hold=false;
        await page.refresh();assert(page.items[0].canPower);
        // Separately compiled pages must use the same session cache.
        page.onHide();light='on';
        const detailExports={};evaluate(await zip.file('pages/detail/index.js').async('string'),'detail-cache-test.js')(context,context,context,detailExports,{});
        const detailResult={};detailExports.entry(detailResult);
        const detail=Object.assign({},detailResult.default,detailResult.default.data,{entityId:'light.living_room'});
        detail.onShow();await flush();assert.equal(detail.value.trim(),'on');detail.onHide();detail.onDestroy();
        const afterDetail=requests.length;page.onShow();await flush();assert.equal(requests.length,afterDetail);assert(page.items[0].state.startsWith('on'));
        const beforePoll=requests.length;scheduled.get(page._poll)();await flush();assert.equal(requests.length,beforePoll+2,'periodic refresh must not skip a tick because of cache TTL');
        // A filter changed during a slow refresh must load newly shown entities.
        hold=true;const oldFilter=page.refresh();
        page._config.entities.push({id:'switch.desk',domain:'switch',name:'Desk',icon:'switch',group:'Home',favorite:false,order:2});
        page.filter=0;page.cycleFilter();assert.equal(page.items.length,3);
        hold=false;for(const request of held.splice(0)){
          const id=decodeURIComponent(request.url.split('/').at(-1));request.success({code:200,data:JSON.stringify({entity_id:id,state:'on',attributes:{}})});
        }
        await oldFilter;await flush();assert(page.items[2].state.startsWith('on'));assert(requests.at(-1).url.endsWith('/switch.desk'));
        page._config.entities.pop();page.filter=0;page.render();
      }
      page.detail(0);assert.equal(navigation.at(-1).params.entityId,'light.living_room');page.sync();assert.equal(navigation.at(-1).uri,'pages/receive');
    }else if(route==='pages/detail'){
      assert.equal(page.name,'Living room');assert(page.actions.some(a=>a.service==='turn_on'));await page.run(0);assert.equal(page.status,'State confirmed.');assert.equal(page.value.trim(),'on');
    }else if(route==='pages/settings'){
      assert.equal(requests.length,0,'Info page must not poll');await page.probe(false);assert(page.status.includes('passed'));await page.probe(true);assert(requests.some(r=>r.url.endsWith('/homeassistant/update_entity')));
      await page.erase();await page.erase();assert.equal(storage['ha.config.a'],'');assert.equal(storage['ha.config.b'],'');
    }else{
      assert.equal(page.connection,'Phone connected.');await connect.onmessage({data:{data:JSON.stringify({type:'ha-hello',version:1,id:'b'.repeat(32)})}});assert.equal(sent.at(-1).type,'ha-ready');
      const updated=JSON.stringify({...config,revision:11,entities:[]}),bytes=Buffer.from(updated),transfer=crypto.randomBytes(16).toString('hex');let id=0;
      const p=(type,extra={})=>({type,version:1,id:(++id).toString(16).padStart(32,'0'),transfer,...extra});
      await connect.onmessage({data:JSON.stringify(p('ha-begin',{revision:11,total:Math.ceil(bytes.length/768),bytes:bytes.length,checksum:crc(bytes)}))});
      for(let i=0;i<bytes.length;i+=768)await connect.onmessage({data:JSON.stringify(p('ha-chunk',{index:i/768,data:bytes.subarray(i,i+768).toString('base64')}))});
      await connect.onmessage({data:JSON.stringify(p('ha-commit'))});assert.equal(sent.at(-1).status,'saved');assert.equal(JSON.parse(JSON.parse(storage['ha.config.b']).payload).entities.length,0);
      // The first page's close must not remove the next page's singleton handler.
      const previous=page._channel;await page.onShow();previous.close();await connect.onmessage({data:JSON.stringify(p('ha-hello'))});assert.equal(sent.at(-1).type,'ha-ready');
    }
    if(route==='pages/index'||route==='pages/detail'){
      hold=true;const pending=page.refresh(),status=page.status;assert(held.length>0);
      page.onHide();for(const request of held)request.success({code:200,data:'{"entity_id":"light.living_room","state":"on","attributes":{}}'});
      await pending;await flush();assert.equal(page.status,status,'Late native callback updated a hidden page');
    }
    trees[route]={nodes:visible.length,networkRequests:requests.length};
    if(page.onHide)page.onHide();if(page.onDestroy)page.onDestroy();await flush();assert.equal(scheduled.size,0,'Page timer leaked');
    if(route==='pages/receive'){const count=sent.length;await connect.onmessage({data:JSON.stringify({type:'ha-hello',version:1,id:'e'.repeat(32)})});assert.equal(sent.length,count);}
  }
  return trees;
}
module.exports={smokeRpk};
if(require.main===module)smokeRpk(process.argv[2]).then(r=>console.log('Compiled RPK smoke passed:',r)).catch(e=>{console.error(e.stack);process.exitCode=1;});
