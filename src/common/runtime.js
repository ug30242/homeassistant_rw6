import storage from '@system.storage';
import fetch from '@system.fetch';
import {createConfigStore} from './config-store.js';
import {createHaClient} from './ha-client.js';
import {createStateCache} from './state-cache.js';
// Vela compiles separate page bundles. Share only the storage operation queue across those bundles.
const root=typeof global!=='undefined'?global:{};
if(!root.__rw6HaStorageCoordinator)root.__rw6HaStorageCoordinator={queue:Promise.resolve()};
export const store=createConfigStore(storage,root.__rw6HaStorageCoordinator);
if(!root.__rw6HaStateCache)root.__rw6HaStateCache=createStateCache();
export const stateCache=root.__rw6HaStateCache;
export function client(config) { return createHaClient(fetch,config); }
