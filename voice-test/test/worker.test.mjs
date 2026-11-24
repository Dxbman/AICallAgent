import test from "node:test";
import assert from "node:assert/strict";
import {handle} from "../src/worker.mjs";
const root="https://demo.example";
const token="a".repeat(43);
const sdp="v=0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n";
const env={OPENAI_API_KEY:"mock-server-only-key",VOICE_TEST_TOKEN:token,
  VOICE_RATE_LIMITER:{limit:async()=>({success:true})}};
const valid={sdp,voice:"marin",dialect:"emirati",consent:true};
function post(data=valid,auth=token,origin=root){
 return new Request(root+"/session",{method:"POST",headers:{Origin:origin,Authorization:"Bearer "+auth,"Content-Type":"application/json"},body:JSON.stringify(data)});
}
const forbidden=async()=>{throw Error("Unexpected upstream call")};
test("requires configured secrets, authentication, and same origin",async()=>{
 assert.equal((await handle(post(),{},forbidden)).status,503);
 assert.equal((await handle(post(valid,"b".repeat(43)),env,forbidden)).status,401);
 assert.equal((await handle(post(valid,token,"https://other.example"),env,forbidden)).status,403);
});
test("requires consent and validates selectable session options",async()=>{
 for(const bad of [{consent:false},{voice:"bad"},{dialect:"constructor"},{sdp:"bad"},{sdp:sdp+"m=video 9\r\n"}]){
  assert.equal((await handle(post({...valid,...bad}),env,forbidden)).status,400);
 }
});
test("rate limited requests never reach OpenAI",async()=>{
 const limited={...env,VOICE_RATE_LIMITER:{limit:async()=>({success:false})}};
 assert.equal((await handle(post(),limited,forbidden)).status,429);
});
test("uses fixed OpenAI route, server key and approved session configuration",async()=>{
 const res=await handle(post(),env,async(url,opts)=>{
  assert.equal(url,"https://api.openai.com/v1/realtime/calls");
  assert.equal(opts.headers.Authorization,"Bearer mock-server-only-key");
  assert.equal(opts.redirect,"error");
  const session=JSON.parse(opts.body.get("session"));
  assert.equal(session.model,"gpt-realtime-2.1-mini");
  assert.equal(session.audio.output.voice,"marin");
  assert.equal(opts.body.get("sdp"),sdp);
  return new Response(sdp,{status:201});
 });
 assert.equal(res.status,200);
 assert.equal(await res.text(),sdp);
 assert.equal(res.headers.get("Cache-Control"),"no-store");
});
test("public test page and health endpoint do not include secrets",async()=>{
 for(const p of ["/health","/voice-test","/voice-test.js"]){
  const res=await handle(new Request(root+p),env,forbidden);
  assert.equal(res.status,200);
  const body=await res.text();
  assert.ok(!body.includes(env.OPENAI_API_KEY)&&!body.includes(token));
 }
});
