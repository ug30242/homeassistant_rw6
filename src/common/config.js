export const DOMAINS = ['light','switch','fan','cover','sensor','scene','script'];
export const MAX_BYTES = 49152;
export function validUrl(value, allowInsecure) {
  if (typeof value !== 'string' || value.length > 512 || /[\s?#\\]/.test(value)) throw new Error('Invalid server URL.');
  // Vela has no browser URL global. Reject userinfo and ambiguous authorities.
  const match = /^(https?):\/\/(\[[0-9a-fA-F:]+\]|[a-zA-Z0-9.-]+)(:[0-9]{1,5})?(\/[^\s?#]*)?$/.exec(value);
  if (!match || (match[1] === 'http' && !allowInsecure) || (match[3] && (+match[3].slice(1) < 1 || +match[3].slice(1) > 65535))) throw new Error('Use a valid HTTPS URL.');
  return value.replace(/\/+$/, '');
}
function text(value, max) { return typeof value === 'string' && value.length <= max && !/[\x00-\x1f\x7f]/.test(value); }
export function validateConfig(value) {
  if (!value || value.schema !== 1 || !Number.isSafeInteger(value.revision) || value.revision < 1) throw new Error('Unsupported configuration.');
  const cleared = value.cleared === true;
  const server = cleared ? '' : validUrl(value.server, value.allowInsecure === true);
  if (!text(value.token,4096) || (!cleared && !/^[!-~]+$/.test(value.token)) || (cleared && value.token !== '')) throw new Error('Invalid token.');
  if (!Array.isArray(value.entities) || value.entities.length > 80 || (cleared && value.entities.length)) throw new Error('Too many entities.');
  const ids = {};
  const entities = value.entities.map((entity,index) => {
    if (!entity || !/^[a-z_]+\.[a-z0-9_]+$/.test(entity.id) || entity.id.length > 160 || ids[entity.id]) throw new Error('Invalid entity.');
    const domain = entity.id.split('.')[0];
    if (DOMAINS.indexOf(domain) < 0 || !text(entity.name,80) || !text(entity.icon,32) || !/^[a-zA-Z0-9:_-]*$/.test(entity.icon) || !text(entity.group,40)) throw new Error('Invalid entity metadata.');
    ids[entity.id] = true;
    return {id:entity.id, name:entity.name, icon:entity.icon || domain, group:entity.group,
      favorite:entity.favorite === true, order:index, domain};
  });
  const refresh = value.refreshSeconds || 0;
  if ([0,30,60,120].indexOf(refresh) < 0) throw new Error('Invalid refresh interval.');
  return {schema:1, revision:value.revision, server, token:value.token, allowInsecure:value.allowInsecure === true,
    cleared, refreshSeconds:refresh, entities};
}
export function displayName(entity) {
  const name=(entity.name||'').trim().replace(/ +/g,' ');
  if(name && /^[\x20-\x7e]+$/.test(name))return name;
  const fallback=entity.id.split('.').slice(1).join('.').replace(/_+/g,' ').trim()||entity.id;
  return fallback.charAt(0).toUpperCase()+fallback.slice(1);
}
export function entityKind(entity) {
  const labels={light:'Light',switch:'Switch',fan:'Fan',cover:'Cover',sensor:'Sensor',scene:'Scene',script:'Script'};
  return labels[entity.id.split('.')[0]]||'Entity';
}
export function iconPath(entity) {
  const aliases={'mdi:lightbulb':'light','mdi:toggle-switch':'switch','mdi:fan':'fan','mdi:curtains':'cover','mdi:blinds':'cover','mdi:thermometer':'sensor','mdi:palette':'scene','mdi:script':'script'};
  const name=DOMAINS.indexOf(entity.icon)>=0?entity.icon:aliases[entity.icon]||entity.id.split('.')[0];
  return '/common/icons/'+name+'.png';
}
export function displayText(value) {
  const text = String(value === undefined || value === null ? '' : value);
  return /^[\x20-\x7e]*$/.test(text) ? text.slice(0,100) : 'See Home Assistant';
}
