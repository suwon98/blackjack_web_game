import assert from 'node:assert/strict';
import { Client } from '@stomp/stompjs';
const base=process.env.API_URL||'http://127.0.0.1:8080';
const stamp=Date.now().toString().slice(-7);
async function request(path,body,token){
  return fetch(base+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
}
const users=[];
for(let i=0;i<2;i++){
  const u={username:`sec${stamp}${i}`,nickname:`s${stamp}${i}`,password:'SecurityTest!123'};
  assert.equal((await request('/api/members/join',u)).status,200);
  Object.assign(u,await(await request('/api/members/login',u)).json());
  u.room=(await(await request('/api/rooms',{title:'Security test',maxPlayers:1,minBet:1000,isPrivate:false},u.accessToken)).json()).roomId;
  users.push(u);
}
async function rejected(headers,destination){
  let client;
  try{
    await new Promise((resolve,reject)=>{
      const timeout=setTimeout(()=>reject(new Error('Expected STOMP rejection')),8000);
      client=new Client({brokerURL:base.replace('http','ws')+'/ws-blackjack',connectHeaders:headers,reconnectDelay:0});
      client.onStompError=()=>{clearTimeout(timeout);resolve();};
      client.onConnect=()=>{if(destination)client.subscribe(destination,()=>{clearTimeout(timeout);reject(new Error('Unauthorized snapshot delivered'));});else{clearTimeout(timeout);reject(new Error('Unauthorized CONNECT accepted'));}};
      client.activate();
    });
  }finally{if(client)await client.deactivate();}
}
try{
  assert.equal((await request('/api/members/login',{padding:'x'.repeat(17000)})).status,413);
  await rejected({roomId:users[0].room});
  await rejected({roomId:users[0].room,Authorization:`Bearer ${users[1].accessToken}`});
  await rejected({roomId:users[0].room,Authorization:`Bearer ${users[0].accessToken}`},`/topic/room/${users[1].room}`);
  for(const u of users)await request(`/api/rooms/${u.room}/leave`,{},u.accessToken);
  const u=users[0];
  const responses=await Promise.all([0,1].map(()=>request('/api/members/reissue',{refreshToken:u.refreshToken})));
  assert.deepEqual(responses.map(r=>r.status).sort(),[200,400]);
  assert.equal((await request('/api/members/me',undefined,u.accessToken)).status,401);
  Object.assign(u,await responses.find(r=>r.status===200).json());
  assert.equal((await request('/api/members/logout',{},u.accessToken)).status,200);
  assert.equal((await request('/api/members/me',undefined,u.accessToken)).status,401);
  let limited=false;
  for(let i=0;i<35;i++){if((await request('/api/members/login',{})).status===429){limited=true;break;}}
  assert.equal(limited,true,'authentication rate limit');
  console.log('PASS: authentication rate limit, 16KB body limit, unauthenticated/wrong-room CONNECT, cross-room SUBSCRIBE, concurrent refresh single-use and immediate access revocation');
}finally{
  for(const u of users)await request(`/api/rooms/${u.room}/leave`,{},u.accessToken);
}
