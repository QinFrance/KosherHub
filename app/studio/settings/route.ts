import html from '../../../public/settings.html?raw';
import {owner} from '../../../lib/auth';
import {getChatGPTUser,chatGPTSignInPath} from '../../chatgpt-auth';
export const dynamic='force-dynamic';
export async function GET(request:Request){if(!await getChatGPTUser())return Response.redirect(new URL(chatGPTSignInPath('/studio/settings'),request.url),302);if(!await owner())return new Response('Owner access required',{status:403});return new Response(html,{headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'}})}
