import {utf8,base64,checksum} from '../src/common/codec.js';
export function config(overrides={}) {return {schema:1,revision:10,server:'https://ha.example.test',token:'test-token-not-valid',allowInsecure:false,cleared:false,refreshSeconds:0,entities:[{id:'light.living_room',name:'거실',icon:'light',group:'거실',favorite:true,order:0}],...overrides};}
export function memoryStorage(){
  const values={};let writes=0;
  return {values,get writes(){return writes;},failKey:'',failRead:false,
    get({key,default:fallback='',success,fail}){if(this.failRead)fail();else success(values[key]===undefined?fallback:values[key]);},
    set({key,value,success,fail}){writes++;if(key===this.failKey)fail();else{values[key]=value;success();}}};
}
export function packets(value=config(),transfer='a'.repeat(32)) {
  const payload=JSON.stringify(value),bytes=utf8(payload),parts=[];
  let id=0;const packet=(type,rest={})=>({type,version:1,id:(++id).toString(16).padStart(32,'0'),transfer,...rest});
  parts.push(packet('ha-begin',{revision:value.revision,total:Math.ceil(bytes.length/768),bytes:bytes.length,checksum:checksum(bytes)}));
  for(let i=0;i<bytes.length;i+=768)parts.push(packet('ha-chunk',{index:i/768,data:base64(bytes.slice(i,i+768))}));
  parts.push(packet('ha-commit'));return parts;
}
