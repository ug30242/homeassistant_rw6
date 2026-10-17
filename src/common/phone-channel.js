import {createSyncReceiver} from './sync-receiver.js';

// XMS native send requires Object -> data -> JSON, including for replies (1006 diagnostics).
export function openPhoneChannel(connect,{store,update,connection}) {
  let live=true, callbacks={update,connection};
  const receiver=createSyncReceiver({store,update:text=>{if(live)callbacks.update(text);},send:json=>{
    if (!live) return;
    connect.send({data:{data:json},success(){},fail(data,code){
      if(live)callbacks.connection('Reply failed ('+(Number(code)||Number(data&&data.code)||0)+'). Check Mi Fitness.');
    }});
  }});
  connect.onmessage=event=>receiver.receive(event);
  connect.onopen=()=>{if(live)callbacks.connection('Phone connected.');};
  connect.onclose=()=>{if(live)callbacks.connection('Phone disconnected.');};
  connect.onerror=()=>{if(live)callbacks.connection('XMS error. Check Mi Fitness.');};
  return {check(){connect.getReadyState({success:data=>{if(live)callbacks.connection(data.status===1?'Phone connected.':'Open Android and Mi Fitness.');},fail:()=>{if(live)callbacks.connection('Connection unavailable.');}});},
    isSaving:receiver.isSaving,close(){live=false;receiver.close();callbacks={};}};
}
