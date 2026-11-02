import fs from 'node:fs';
import path from 'node:path';
import {deflateRawSync} from 'node:zlib';
const folders=['app','db','drizzle','lib','public','scripts','vendor','worker'];
const names=['LICENSE','README.md','CONTRIBUTING.md','.env.example','.gitignore','.npmrc','package.json','package-lock.json','vite.config.ts','tsconfig.json','next.config.ts','postcss.config.mjs','drizzle.config.ts','cloudflare-env.d.ts','raw.d.ts'];
const items=[];
function add(file){if(file.endsWith('.zip')||file.endsWith('.tsbuildinfo'))return;items.push({path:file,mode:'100644',type:'blob',content:fs.readFileSync(file,'utf8')})}
function walk(dir){for(const e of fs.readdirSync(dir,{withFileTypes:true})){const f=path.posix.join(dir,e.name);if(e.isDirectory())walk(f);else if(e.isFile())add(f)}}
for(const f of names)if(fs.existsSync(f))add(f);for(const f of folders)walk(f);
items.push({path:'.openai/hosting.json',mode:'100644',type:'blob',content:JSON.stringify({d1:'DB',r2:'BUCKET'},null,2)+'\n'});
const table=new Uint32Array(256);for(let n=0;n<256;n++){let c=n;for(let k=0;k<8;k++)c=c&1?0xedb88320^(c>>>1):c>>>1;table[n]=c>>>0}
function crc(bytes){let c=0xffffffff;for(const b of bytes)c=table[(c^b)&255]^(c>>>8);return(c^0xffffffff)>>>0}
const parts=[],central=[];let offset=0;
for(const item of items){const name=Buffer.from(item.path),data=Buffer.from(item.content),packed=deflateRawSync(data),sum=crc(data);const h=Buffer.alloc(30);h.writeUInt32LE(0x04034b50);h.writeUInt16LE(20,4);h.writeUInt16LE(0x800,6);h.writeUInt16LE(8,8);h.writeUInt16LE(0x21,12);h.writeUInt32LE(sum,14);h.writeUInt32LE(packed.length,18);h.writeUInt32LE(data.length,22);h.writeUInt16LE(name.length,26);parts.push(h,name,packed);const c=Buffer.alloc(46);c.writeUInt32LE(0x02014b50);c.writeUInt16LE(20,4);c.writeUInt16LE(20,6);c.writeUInt16LE(0x800,8);c.writeUInt16LE(8,10);c.writeUInt16LE(0x21,14);c.writeUInt32LE(sum,16);c.writeUInt32LE(packed.length,20);c.writeUInt32LE(data.length,24);c.writeUInt16LE(name.length,28);c.writeUInt32LE(offset,42);central.push(c,name);offset+=h.length+name.length+packed.length}
const directory=Buffer.concat(central),end=Buffer.alloc(22);end.writeUInt32LE(0x06054b50);end.writeUInt16LE(items.length,8);end.writeUInt16LE(items.length,10);end.writeUInt32LE(directory.length,12);end.writeUInt32LE(offset,16);fs.writeFileSync('public/kosherhub-source.zip',Buffer.concat([...parts,directory,end]));fs.mkdirSync('.sites-runtime',{recursive:true});fs.writeFileSync('.sites-runtime/public-tree.json',JSON.stringify(items));console.log(`Exported ${items.length} source files; credentials and deployment identity excluded.`);
