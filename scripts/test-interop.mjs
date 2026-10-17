import fs from 'node:fs';
import assert from 'node:assert/strict';
import {createConfigStore} from '../src/common/config-store.js';
import {createSyncReceiver} from '../src/common/sync-receiver.js';
import {memoryStorage} from '../tests/helpers.mjs';
const fixture=JSON.parse(fs.readFileSync('android/app/build/interop/phone-packet.json','utf8'));
const store=createConfigStore(memoryStorage()),replies=[];
const receiver=createSyncReceiver({store,send:raw=>replies.push(JSON.parse(raw))});
for(const p of fixture.packets){assert(Buffer.byteLength(JSON.stringify(p))<=2048);await receiver.receive({data:{data:JSON.stringify(p)}});assert.equal(replies.at(-1).id,p.id);assert.equal(replies.at(-1).status,p.type==='ha-commit'?'saved':'ok');}
const record=await store.read();assert.equal(record.config.token,fixture.config.token);assert.deepEqual(record.config.entities.map(e=>e.id),fixture.config.entities.map(e=>e.id));assert.equal(record.config.entities[0].name,'거실');
console.log('Java XMS producer → Vela chunk receiver → durable commit interoperability passed.');
