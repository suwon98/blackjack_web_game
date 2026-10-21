import assert from 'node:assert/strict';
import { Client } from '@stomp/stompjs';
const base=process.env.API_URL || 'http://localhost:18081';
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const stamp=Date.now().toString().slice(-8);
const users=[0,1,2].map(i=>({username:`ready${stamp}${i}`,nickname:`ready${stamp.slice(-4)}${i}`,password:'ReadyTest!123',errors:[],events:[],state:null}));
let roomId;
async function api(path,body,u,expected=200) {
  const response=await fetch(base+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(u?.accessToken?{Authorization:`Bearer ${u.accessToken}`}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
  const text=await response.text();assert.equal(response.status,expected,`${path}: ${text}`);
  try{return JSON.parse(text);}catch{return text;}
}
async function until(fn,label,timeout=15000) { const end=Date.now()+timeout;while(Date.now()<end){if(fn())return;await sleep(30);}throw Error(`Timeout: ${label}`); }
function send(u,route,body={}) {u.client.publish({destination:`/app/game/${route}`,body:JSON.stringify({roomId,...body})});}
try {
  for(const u of users){await api('/api/members/join',u);Object.assign(u,await api('/api/members/login',u));}
  roomId=(await api('/api/rooms',{title:'Readiness integration',maxPlayers:3,isPrivate:false,minBet:1000},users[0])).roomId;
  for(let i=0;i<users.length;i++) {
    const u=users[i];if(i)await api(`/api/rooms/${roomId}/enter`,{},u);
    u.client=new Client({brokerURL:base.replace('http','ws')+'/ws-blackjack',connectHeaders:{roomId,Authorization:`Bearer ${u.accessToken}`},reconnectDelay:0});
    u.client.onConnect=()=>{
      u.client.subscribe(`/topic/room/${roomId}`,m=>u.state=JSON.parse(m.body));
      u.client.subscribe('/user/queue/errors',m=>u.errors.push(JSON.parse(m.body)));
      u.client.subscribe('/user/queue/room-events',m=>u.events.push(JSON.parse(m.body)));
      send(u,'sync');
    };
    u.client.activate();await until(()=>u.state,'connect');send(u,'seat',{seatIndex:i+1});
    await until(()=>users[0].state.seats[i+1]===u.username,'seat');
  }
  const [host,a,b]=users;assert.equal(host.state.host,host.username);
  send(a,'start');await until(()=>a.errors.length===1,'reject guest start');
  send(host,'start');await until(()=>host.errors.length===1,'reject start before readiness');
  send(a,'ready',{ready:true,username:b.username});
  await until(()=>host.state.waitingTarget===b.username,'one unready guest timer');
  assert.deepEqual(host.state.readyPlayers,[a.username]);
  const started=Date.now();await until(()=>b.events.length===1,'unready guest removal');
  assert.ok(Date.now()-started>=9000,'kick waits for deadline');assert.equal(b.events[0].type,'KICKED');
  await api(`/api/rooms/${roomId}/enter`,{},b,400);
  const snapshots=JSON.stringify(b.state);
  await until(()=>host.events.length===1,'idle host removal');
  await until(()=>a.state.host===a.username,'host transfer');
  assert.equal(JSON.stringify(b.state),snapshots,'removed user receives no more room state');
  assert.equal((await api('/api/members/me',undefined,host)).money,100000);
  assert.equal((await api('/api/members/me',undefined,b)).money,100000);
  send(a,'start');await until(()=>a.state.state==='BETTING','new host start');
  await api(`/api/rooms/${roomId}/leave`,{},a);roomId=null;
  assert.equal((await api('/api/members/me',undefined,a)).money,100000);
  console.log('PASS: host-only start, readiness, forged identity rejection, actual 10s guest/host removal, private notifications, host transfer, re-entry cooldown, outbound isolation and unchanged wallets');
} finally {
  if(roomId)for(const u of users){try{await api(`/api/rooms/${roomId}/leave`,{},u);}catch{}}
  await Promise.all(users.filter(u=>u.client).map(u=>u.client.deactivate()));
}
