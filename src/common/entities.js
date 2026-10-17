export function available(state) { return state && state.state!=='unavailable' && state.state!=='unknown'; }
export function commands(entity,state) {
  const domain=entity.id.split('.')[0];
  if(!available(state))return [];
  const features=Number(state.attributes && state.attributes.supported_features)||0;
  if(['light','switch','fan'].indexOf(domain)>=0) {
    const result=[{label:'ON',service:'turn_on',extra:{},expected:'on'},{label:'OFF',service:'turn_off',extra:{},expected:'off'}];
    const modes=state.attributes && state.attributes.supported_color_modes || [];
    if(domain==='light' && ((features&1) || modes.some(mode=>['brightness','color_temp','hs','xy','rgb','rgbw','rgbww','white'].indexOf(mode)>=0))) {
      [25,50,75,100].forEach(value=>result.push({label:value+'%',service:'turn_on',extra:{brightness_pct:value},expected:'on',brightness:value}));
    }
    return result;
  }
  if(domain==='cover') {
    const result=[];
    if(features&1)result.push({label:'Open',service:'open_cover',extra:{},expected:'open'});
    if(features&2)result.push({label:'Close',service:'close_cover',extra:{},expected:'closed'});
    if(features&8)result.push({label:'Stop',service:'stop_cover',extra:{}});
    if(features&4)[0,25,50,75,100].forEach(value=>result.push({label:value+'%',service:'set_cover_position',extra:{position:value},position:value}));
    return result;
  }
  if(domain==='scene' || domain==='script')return [{label:'Run',service:'turn_on',extra:{},execution:true}];
  return [];
}
export async function execute(client,entity,command,before,{delay=ms=>new Promise(resolve=>setTimeout(resolve,ms)),active=()=>true}={}) {
  // Reject stale or invented actions. Recompute from the fetched capabilities.
  if(!commands(entity,before).some(c=>JSON.stringify(c)===JSON.stringify(command)))throw new Error('Action unavailable. Refresh state.');
  const issued=Date.now();
  await client.service(entity.id.split('.')[0],command.service,entity.id,command.extra);
  let after;
  for(let attempt=0;attempt<3;attempt++) {
    if(!active())throw new Error('Request cancelled.');
    if(attempt)await delay(700);
    if(!active())throw new Error('Request cancelled.');
    after=await client.state(entity.id);
    if(!available(after))throw new Error('Entity unavailable after command.');
    let confirmed=false;
    if(command.execution) {
      const previous=entity.id.startsWith('scene.')?before.state:(before.attributes||{}).last_triggered;
      const next=entity.id.startsWith('scene.')?after.state:(after.attributes||{}).last_triggered;
      confirmed=!!next && next!==previous && Date.parse(next)>=issued-5000;
    } else if(command.position!==undefined) confirmed=Math.abs(Number((after.attributes||{}).current_position)-command.position)<=2;
    else if(command.brightness!==undefined) confirmed=after.state==='on' && Math.abs(Number((after.attributes||{}).brightness)/255*100-command.brightness)<=3;
    else if(command.expected)confirmed=after.state===command.expected;
    else confirmed=after.state!=='opening' && after.state!=='closing';
    if(confirmed)return {state:after,confirmed:true,message:'State confirmed.'};
  }
  return {state:after,confirmed:false,message:'Sent; state change unconfirmed. Refresh.'};
}
