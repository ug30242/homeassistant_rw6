// Firmware versions may expose the payload directly, under data, or as UTF-8 bytes.
export function decodePhoneMessage(input) {
  let value = input;
  for (let depth = 0; depth < 6; depth++) {
    if (value instanceof ArrayBuffer || ArrayBuffer.isView(value)) {
      const bytes = value instanceof ArrayBuffer ? new Uint8Array(value)
        : new Uint8Array(value.buffer, value.byteOffset, value.byteLength);
      if (bytes.length > 2048) throw new Error('Message too large.');
      let encoded = '';
      for (let i = 0; i < bytes.length; i++) encoded += '%' + ('0' + bytes[i].toString(16)).slice(-2);
      value = decodeURIComponent(encoded);
    }
    if (typeof value === 'string') {
      if (value.length > 2048) throw new Error('Message too large.');
      value = JSON.parse(value);
      continue;
    }
    if (!value || typeof value !== 'object' || Array.isArray(value)) break;
    if (typeof value.type === 'string') {
      if (value.version !== 1 || typeof value.id !== 'string' || !/^[a-f0-9]{32}$/.test(value.id)) break;
      return value;
    }
    value = value.data;
  }
  throw new Error('Invalid phone message.');
}
