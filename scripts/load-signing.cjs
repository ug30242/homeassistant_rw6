// CI only: materialize repository Secrets, verify the committed PUBLIC identity, never print key material.
const fs=require('node:fs');
const crypto=require('node:crypto');
const assert=require('node:assert/strict');
const key=process.env.HA_RELEASE_PRIVATE_KEY_PEM;
const cert=process.env.HA_RELEASE_CERTIFICATE_PEM;
assert(key&&cert,'Set HA_RELEASE_PRIVATE_KEY_PEM and HA_RELEASE_CERTIFICATE_PEM repository Secrets first.');
const expected=new crypto.X509Certificate(fs.readFileSync('certificates/release.pem'));
const provided=new crypto.X509Certificate(cert);
assert.equal(provided.fingerprint256,expected.fingerprint256,'Signing certificate changed; preserve the published identity.');
assert.deepEqual(crypto.createPublicKey(key).export({format:'der',type:'spki'}),provided.publicKey.export({format:'der',type:'spki'}),'Private key does not match certificate');
fs.mkdirSync('sign/release',{recursive:true,mode:0o700});
fs.writeFileSync('sign/release/private.pem',key,{mode:0o600});
fs.writeFileSync('sign/release/certificate.pem',cert,{mode:0o600});
console.log('Signing identity verified and materialized in ignored storage.');
