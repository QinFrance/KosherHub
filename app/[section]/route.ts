import html from '../../public/home.html?raw';
const pages=['phones','roms','sites','blog'];
export async function GET(_:Request,{params}:{params:Promise<{section:string}>}){const {section}=await params;if(!pages.includes(section))return new Response('Page not found',{status:404});return new Response(html,{headers:{'Content-Type':'text/html; charset=utf-8'}})}
