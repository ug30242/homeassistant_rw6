const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/';
export function utf8(text) {
  const encoded = encodeURIComponent(text);
  const result = [];
  for (let i=0;i<encoded.length;i++) {
    if(encoded[i]==='%'){result.push(parseInt(encoded.slice(i+1,i+3),16));i+=2;}
    else result.push(encoded.charCodeAt(i));
  }
  return result;
}
export function fromUtf8(bytes) {
  let escaped = '';
  for (let i=0;i<bytes.length;i++) escaped += '%' + ('0'+bytes[i].toString(16)).slice(-2);
  return decodeURIComponent(escaped);
}
export function base64(bytes) {
  let result = '';
  for (let i=0;i<bytes.length;i+=3) {
    const n=(bytes[i]<<16)|((bytes[i+1]||0)<<8)|(bytes[i+2]||0);
    result += ALPHABET[(n>>>18)&63]+ALPHABET[(n>>>12)&63]+(i+1<bytes.length?ALPHABET[(n>>>6)&63]:'=')+(i+2<bytes.length?ALPHABET[n&63]:'=');
  }
  return result;
}
export function unbase64(text) {
  if (typeof text !== 'string' || !text.length || text.length > 1024 || text.length%4 || !/^[A-Za-z0-9+/]+={0,2}$/.test(text)) throw new Error('Invalid chunk.');
  const bytes = [];
  for (let i=0;i<text.length;i+=4) {
    const a=ALPHABET.indexOf(text[i]),b=ALPHABET.indexOf(text[i+1]),c=ALPHABET.indexOf(text[i+2]),d=ALPHABET.indexOf(text[i+3]);
    const n=(a<<18)|(b<<12)|((c<0?0:c)<<6)|(d<0?0:d);
    bytes.push((n>>>16)&255);
    if (text[i+2]!=='=') bytes.push((n>>>8)&255);
    if (text[i+3]!=='=') bytes.push(n&255);
  }
  if (base64(bytes)!==text) throw new Error('Invalid padding.');
  return bytes;
}
export function checksum(bytes) {
  let crc=0xffffffff;
  for (let i=0;i<bytes.length;i++) {
    crc ^= bytes[i];
    for (let j=0;j<8;j++) crc=(crc>>>1)^((crc&1)?0xedb88320:0);
  }
  return ('00000000'+((crc^0xffffffff)>>>0).toString(16)).slice(-8);
}
