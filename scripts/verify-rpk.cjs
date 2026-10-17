// Independent verification of aiot-toolkit's RPK v2 format. No private key needed.
const fs = require('node:fs');
const crypto = require('node:crypto');
const assert = require('node:assert/strict');
const JSZip = require('jszip');

const sha256 = b => crypto.createHash('sha256').update(b).digest();
const u32 = n => { const b = Buffer.alloc(4); b.writeUInt32LE(n); return b; };
class Reader {
  constructor(b) { this.b = b; this.offset = 0; }
  take(n) {
    assert(Number.isSafeInteger(n) && n >= 0 && this.offset + n <= this.b.length, 'Truncated signing block');
    const b = this.b.subarray(this.offset, this.offset + n); this.offset += n; return b;
  }
  int() { return this.take(4).readUInt32LE(); }
  long() { const n = this.take(8).readBigUInt64LE(); assert(n <= BigInt(Number.MAX_SAFE_INTEGER)); return Number(n); }
  field() { return this.take(this.int()); }
  done() { assert.equal(this.offset, this.b.length, 'Unexpected signing block data'); }
}
function crc32(text) {
  let crc = 0xffffffff;
  for (const byte of Buffer.from(text)) {
    crc ^= byte;
    for (let j = 0; j < 8; j++) crc = (crc >>> 1) ^ ((crc & 1) ? 0xedb88320 : 0);
  }
  return (crc ^ 0xffffffff) >>> 0;
}
function signatureRecord(data) {
  const r = new Reader(data);
  assert.equal(r.int(), 0x0103, 'Expected RSA-SHA256');
  const bytes = r.field(); r.done(); return bytes;
}
function verifySignatureBlock(b) {
  let eocd = -1;
  for (let i = b.length - 22; i >= Math.max(0, b.length - 65557); i--) {
    if (b.readUInt32LE(i) === 0x06054b50 && i + 22 + b.readUInt16LE(i + 20) === b.length) { eocd = i; break; }
  }
  assert(eocd >= 0, 'Missing ZIP end record');
  const cd = b.readUInt32LE(eocd + 16);
  assert(cd >= 32 && cd < eocd, 'Invalid central directory');
  assert.equal(b.subarray(cd - 16, cd).toString(), 'RPK Sig Block 42', 'Missing RPK signature');
  const size = Number(b.readBigUInt64LE(cd - 24));
  const start = cd - size - 8;
  assert(Number.isSafeInteger(size) && start >= 0 && size >= 24, 'Invalid signature size');
  assert.equal(Number(b.readBigUInt64LE(start)), size, 'Signature sizes differ');
  const pairs = new Map();
  const r = new Reader(b.subarray(start + 8, cd - 24));
  while (r.offset < r.b.length) {
    const pair = new Reader(r.take(r.long())); const id = pair.int();
    assert(!pairs.has(id), 'Duplicate signing record'); pairs.set(id, pair.take(pair.b.length - 4));
  }
  const signers = new Reader(pairs.get(0x01000101) || Buffer.alloc(0));
  const signerList = new Reader(signers.field()); signers.done();
  const signer = new Reader(signerList.field()); signerList.done();
  const signedData = signer.field();
  const signatures = new Reader(signer.field());
  const signature = signatureRecord(signatures.field()); signatures.done();
  const publicKey = signer.field(); signer.done();
  const data = new Reader(signedData);
  const digests = new Reader(data.field()); const digest = signatureRecord(digests.field()); digests.done();
  const certs = new Reader(data.field()); const cert = new crypto.X509Certificate(certs.field()); certs.done();
  assert.equal(data.field().length, 0); data.done();
  assert.deepEqual(cert.publicKey.export({type:'spki', format:'der'}), publicKey, 'Certificate public key mismatch');
  assert(crypto.verify('RSA-SHA256', signedData, cert.publicKey, signature), 'Invalid package signature');
  const footer = Buffer.from(b.subarray(eocd)); footer.writeUInt32LE(start, 16);
  const chunks = [b.subarray(0, start), b.subarray(cd, eocd), footer];
  const hashes = chunks.map(c => sha256(Buffer.concat([Buffer.from([0xa5]), u32(c.length), c])));
  const expected = sha256(Buffer.concat([Buffer.from([0x5a]), u32(3), ...hashes]));
  assert.deepEqual(digest, expected, 'RPK content digest mismatch');
  // Independently verify the signed per-file digest table as well.
  const fileList = new Reader(pairs.get(0x01000201) || Buffer.alloc(0));
  const fileRecord = new Reader(fileList.field()); fileList.done();
  const fileData = fileRecord.field(); const fileSignature = signatureRecord(fileRecord.field()); fileRecord.done();
  assert(crypto.verify('RSA-SHA256', fileData, cert.publicKey, fileSignature), 'Invalid file-list signature');
  const fileReader = new Reader(fileData); assert.equal(fileReader.int(), 0x0103);
  const files = new Map();
  while (fileReader.offset < fileData.length) {
    const name = fileReader.int(); const len = fileReader.take(2).readUInt16LE();
    assert(!files.has(name)); files.set(name, fileReader.take(len));
  }
  return {files, unsigned:Buffer.concat(chunks), fingerprint:cert.fingerprint256};
}

async function verifyRpk(buffer, {nested = false} = {}) {
  const signature = verifySignatureBlock(buffer);
  const zip = await JSZip.loadAsync(buffer, {checkCRC32:true});
  const fileNames = Object.keys(zip.files).filter(n => !zip.files[n].dir);
  for (const name of fileNames) {
    assert(!name.startsWith('/') && !name.split('/').includes('..'), 'Unsafe ZIP path');
    const bytes = await zip.file(name).async('nodebuffer');
    // For the nested CERT, toolkit signs the entire unsigned hash ZIP under
    // the hash.json name; outer records sign each uncompressed file.
    const input = nested ? signature.unsigned : bytes;
    assert.deepEqual(signature.files.get(crc32(name)), sha256(input), 'File digest mismatch: ' + name);
  }
  assert.equal(signature.files.size, fileNames.length, 'File-list coverage mismatch');
  if (nested) {
    assert.deepEqual(fileNames, ['hash.json']);
    const metadata = JSON.parse(await zip.file('hash.json').async('string'));
    assert.equal(metadata.algorithm, 'SHA-256');
    return {digests:metadata.digests, ...signature};
  }
  for (const required of ['META-INF/CERT', 'manifest.json', 'manifest-watch.json', 'app.js']) {
    assert(zip.file(required), 'Missing official build output: ' + required);
  }
  const meta = await verifyRpk(await zip.file('META-INF/CERT').async('nodebuffer'), {nested:true});
  assert.equal(meta.fingerprint, signature.fingerprint, 'Inner and outer signing certificates differ');
  const covered = fileNames.filter(n => n !== 'META-INF/CERT').sort();
  assert.deepEqual(Object.keys(meta.digests).sort(), covered, 'META-INF/CERT coverage mismatch');
  for (const name of covered) {
    assert.equal(meta.digests[name], sha256(await zip.file(name).async('nodebuffer')).toString('hex'), 'CERT digest mismatch: ' + name);
  }
  const manifest = JSON.parse(await zip.file('manifest.json').async('string'));
  assert.equal(manifest.package, 'com.vela.rw6ha');
  assert(manifest.deviceTypeList.includes('watch'));
  assert(Number.isInteger(manifest.versionCode) && manifest.versionCode > 0);
  assert(zip.file(manifest.icon.replace(/^\//, '')), 'Missing app icon');
  for (const [route, page] of Object.entries(manifest.router.pages)) {
    assert(zip.file(route + '/' + page.component + '.js'), 'Missing compiled route: ' + route);
  }
  const app = await zip.file('app.js').async('string');
  assert(/^export default function\s*\(/.test(app), 'Missing aiot 2.0.5 component entry');
  return {package:manifest.package, version:manifest.versionName, versionCode:manifest.versionCode,
    files: fileNames.length, certificate:signature.fingerprint};
}
module.exports = {verifyRpk};
if (require.main === module) {
  (async () => {
    assert(process.argv.length > 2, 'Usage: node scripts/verify-rpk.cjs <file.rpk> [...]');
    for (const path of process.argv.slice(2)) {
      const result = await verifyRpk(fs.readFileSync(path));
      assert.equal(result.version, JSON.parse(fs.readFileSync('package.json')).version, 'Release version mismatch');
      assert.equal(result.versionCode, JSON.parse(fs.readFileSync('src/manifest.json')).versionCode, 'Version code mismatch');
      if (path.includes('.release.')) {
        const cert = new crypto.X509Certificate(fs.readFileSync('certificates/release.pem'));
        assert.equal(result.certificate, cert.fingerprint256, 'Unexpected release signing identity');
      }
      console.log(path, result);
    }
  })().catch(e => { console.error('RPK verification failed:', e.message); process.exitCode = 1; });
}
