import html from '../../public/admin.html?raw';
import {admin} from '../../lib/auth';
export const dynamic='force-dynamic';
export async function GET(request:Request){try{await admin(request)}catch{return Response.redirect(new URL('/studio/login',request.url),302)}return new Response(html,{headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'}})}
