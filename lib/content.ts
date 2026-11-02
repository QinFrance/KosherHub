import { env } from 'cloudflare:workers';
export const kinds=['device','category','shop','rom','topic','post'];
export {admin as requireAdmin} from './auth';
export function database(){if(!env.DB)throw new Error('Database unavailable');return env.DB}
export function safeUrl(value:unknown){if(!value)return '';const u=new URL(String(value));if(!['https:','http:'].includes(u.protocol))throw new Error('Use an http or https URL');return u.href}
export function validate(input:any){
 if(!kinds.includes(input.kind))throw new Error('Invalid content type');
 const title=String(input.title||'').trim();if(!title||title.length>160)throw new Error('Title must contain 1–160 characters');
 if(!['draft','published','archived'].includes(input.status))throw new Error('Invalid status');
 const data=input.data||{};if(typeof data!=='object'||Array.isArray(data))throw new Error('Invalid fields');
 const payload={featured:data.featured===true||data.featured==='on',description:String(data.description||'').slice(0,2000),body:String(data.body||'').slice(0,50000),url:safeUrl(data.url),category:String(data.category||'').slice(0,160),brand:String(data.brand||'').slice(0,120),type:String(data.type||'keypad').slice(0,50),version:String(data.version||'').slice(0,80),device:String(data.device||'').slice(0,160),checksum:String(data.checksum||'').slice(0,128),license:String(data.license||'').slice(0,120),fileId:String(data.fileId||'').slice(0,100)};
 return {kind:input.kind,title,status:input.status,payload:JSON.stringify(payload)};
}
export function unpack(row:any){return {...row,data:JSON.parse(row.payload),payload:undefined}}
