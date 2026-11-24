import {HTML, SCRIPT} from "./page.mjs";
const MAX_BODY = 65536;
const MODEL = "gpt-realtime-2.1-mini";
const VOICES = new Set(["marin","cedar"]);
const DIALECTS = {
  emirati:"Use clear, natural Emirati Arabic. If the tester speaks English, respond in English.",
  msa:"Use clear Modern Standard Arabic unless the tester speaks English.",
  auto:"Match the tester's language. Use clear, natural Arabic or English."
};
const SECRET_PATTERN = /^[A-Za-z0-9_-]{32,128}$/;
const SECURITY = {
  "Cache-Control":"no-store",
  "X-Content-Type-Options":"nosniff",
  "Referrer-Policy":"no-referrer",
  "X-Frame-Options":"DENY",
  "Permissions-Policy":"microphone=(self), camera=(), geolocation=()",
  "Content-Security-Policy":"default-src 'none'; script-src 'self'; style-src 'unsafe-inline'; connect-src 'self'; media-src 'self' blob:; form-action 'none'; frame-ancestors 'none'; base-uri 'none'"
};
function reply(body, status=200, type="text/plain; charset=utf-8") {
  return new Response(body,{status, headers:{...SECURITY,"Content-Type":type}});
}
function json(body,status=200) {return reply(JSON.stringify(body),status,"application/json; charset=utf-8")}
async function equalsSecret(a,b) {
  const enc=new TextEncoder();
  const [x,y]=await Promise.all([a,b].map(s=>crypto.subtle.digest("SHA-256",enc.encode(s))));
  const left=new Uint8Array(x),right=new Uint8Array(y);
  let difference=0;
  for(let i=0;i<left.length;i++) difference|=left[i]^right[i];
  return difference===0;
}
async function boundedJson(req) {
  const reader=req.body?.getReader();
  if (!reader) throw new Error("body");
  let total=0;
  const chunks=[];
  try {
    for (;;) {
      const {value,done}=await reader.read();
      if (done) break;
      total+=value.byteLength;
      if(total>MAX_BODY){await reader.cancel();throw new Error("size")}
      chunks.push(value);
    }
  } finally {reader.releaseLock()}
  const data=new Uint8Array(total);
  let offset=0;
  for(const c of chunks){data.set(c,offset);offset+=c.byteLength}
  return JSON.parse(new TextDecoder("utf-8",{fatal:true}).decode(data));
}
export async function handle(request,env,upstream=fetch) {
  const url=new URL(request.url);
  if(request.method==="GET"&&url.pathname==="/health")return json({status:"ok"});
  if(request.method==="GET"&&url.pathname==="/voice-test")return reply(HTML,200,"text/html; charset=utf-8");
  if(request.method==="GET"&&url.pathname==="/voice-test.js")return reply(SCRIPT,200,"text/javascript; charset=utf-8");
  if(request.method!=="POST"||url.pathname!=="/session")return json({error:"not_found"},404);
  if(request.headers.get("Origin")!==url.origin)return json({error:"origin"},403);
  if(!SECRET_PATTERN.test(env.VOICE_TEST_TOKEN||"")||!env.OPENAI_API_KEY||!env.VOICE_RATE_LIMITER)return json({error:"not_configured"},503);
  const auth=request.headers.get("Authorization")||"";
  const token=auth.startsWith("Bearer ")?auth.slice(7):"";
  if(!SECRET_PATTERN.test(token)||!(await equalsSecret(token,env.VOICE_TEST_TOKEN)))return json({error:"unauthorized"},401);
  if((request.headers.get("Content-Type")||"").split(";")[0].trim()!=="application/json")return json({error:"invalid_type"},415);
  let input;
  try{input=await boundedJson(request)}catch{return json({error:"invalid_body"},400)}
  if(!input||input.consent!==true||!VOICES.has(input.voice)
     ||!Object.hasOwn(DIALECTS,input.dialect)||typeof input.sdp!=="string"
     ||input.sdp.length>60000||!input.sdp.startsWith("v=0")||!/[\r\n]m=audio /.test(input.sdp)
     ||/[\r\n]m=video /.test(input.sdp))return json({error:"invalid_session"},400);
  try {
    const rate=await env.VOICE_RATE_LIMITER.limit({key:"owner-voice-test"});
    if(!rate.success)return json({error:"rate_limited"},429);
    const session={
      type:"realtime",model:MODEL,output_modalities:["audio"],
      instructions:"You are an AI voice assistant for a private voice test. Keep answers brief, natural and transparent. Do not ask for private information. "+DIALECTS[input.dialect],
      audio:{output:{voice:input.voice}}
    };
    const body=new FormData();
    body.append("sdp",input.sdp);
    body.append("session",JSON.stringify(session));
    const result=await upstream("https://api.openai.com/v1/realtime/calls",{
      method:"POST",headers:{Authorization:"Bearer "+env.OPENAI_API_KEY},
      body,redirect:"error",signal:AbortSignal.timeout(20000)
    });
    if(!result.ok)return json({error:"provider_error"},result.status===429?429:502);
    const answer=await result.text();
    if(!answer.startsWith("v=0")||answer.length>MAX_BODY)return json({error:"invalid_answer"},502);
    return reply(answer,200,"application/sdp");
  }catch{return json({error:"connection_failed"},502)}
}
export default {fetch:(request,env)=>handle(request,env)};
