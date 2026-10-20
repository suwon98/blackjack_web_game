import assert from 'node:assert/strict';
import { spawn, execFileSync } from 'node:child_process';
import { mkdirSync, openSync, closeSync } from 'node:fs';
import { resolve } from 'node:path';
import { Client } from '@stomp/stompjs';

const root=resolve('../..');
const dir=resolve(root,'.run',`restart-${Date.now()}`);
mkdirSync(dir,{recursive:true});
const composeMode=process.env.COMPOSE_TEST==='1';
const base=composeMode?'http://localhost:18081':'http://127.0.0.1:18080';
function compose(...args){return execFileSync('docker',['compose','-p','blackjack-verify','-f','compose.yml','-f','compose.local.yml',...args],{cwd:root,env:{...process.env,DOMAIN:'localhost'},encoding:'utf8',windowsHide:true,timeout:120000});}
function storedSnapshot(){
  assert.match(roomId,/^[0-9a-f-]+$/);
  const query=`SELECT payload FROM room_snapshot WHERE id='${roomId}'`;
  return JSON.parse(compose('exec','-T','db','sh','-c','MYSQL_PWD="$(cat /run/secrets/db-password)" mariadb -ublackjack blackjack --batch --raw --skip-column-names -e "'+query+'"').trim());
}
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
let server,client,state,roomId,user;
async function until(fn,label,timeout=30000) {
  const end=Date.now()+timeout;
  while(Date.now()<end){if(await fn())return;await sleep(50);}
  throw new Error(`Timeout: ${label}. Logs: ${dir}`);
}
async function start(){
  if(composeMode){
    compose('up','-d','db','backend','web');
    await until(async()=>{try{return(await fetch(base+'/actuator/health')).ok;}catch{return false;}},'container health',60000);
    return;
  }
  const log=openSync(resolve(dir,`server-${Date.now()}.log`),'a');
  server=spawn('java',['-jar',resolve(root,'blackjack/build/libs/blackjack-0.0.1-SNAPSHOT.jar'),'--spring.profiles.active=local','--server.port=18080',`--spring.datasource.url=jdbc:h2:file:${dir.replaceAll('\\','/')}/database;MODE=MariaDB;DB_CLOSE_ON_EXIT=FALSE;WRITE_DELAY=0`],{cwd:root,stdio:['ignore',log,log],windowsHide:true});
  closeSync(log);
  await until(async()=>{try{return(await fetch(base+'/actuator/health')).ok;}catch{return false;}},'server startup',60000);
}
async function kill(){
  if(composeMode){compose('kill','-s','SIGKILL','backend');if(client){await client.deactivate();client=null;}return;}
  if(!server)return;
  const process=server;server=null;
  await new Promise(resolve=>{process.once('exit',resolve);process.kill('SIGKILL');});
  if(client){await client.deactivate();client=null;}
}
async function api(path,body,expected=200){
  const r=await fetch(base+path,{method:body===undefined?'GET':'POST',headers:{'Content-Type':'application/json',...(user?.accessToken?{Authorization:`Bearer ${user.accessToken}`}:{})},...(body===undefined?{}:{body:JSON.stringify(body)})});
  const text=await r.text();assert.equal(r.status,expected,`${path}: ${text}`);
  try{return JSON.parse(text);}catch{return text;}
}
function send(route,body={}){client.publish({destination:`/app/game/${route}`,body:JSON.stringify({roomId,...body})});}
async function connect(){
  state=null;
  client=new Client({brokerURL:base.replace('http','ws')+'/ws-blackjack',connectHeaders:{roomId,Authorization:`Bearer ${user.accessToken}`},reconnectDelay:0});
  client.onConnect=()=>{client.subscribe(`/topic/room/${roomId}`,m=>state=JSON.parse(m.body));send('sync');};
  client.onStompError=f=>console.error('STOMP:',f.headers.message,f.body);
  client.activate();await until(()=>state,'snapshot sync');
}
try{
  await start();
  const name=`restart${Date.now().toString().slice(-8)}`;
  const credentials={username:name,nickname:name.slice(-10),password:'RestartTest!123'};
  await api('/api/members/join',credentials);
  user=await api('/api/members/login',credentials);
  roomId=(await api('/api/rooms',{title:'Restart verification',maxPlayers:1,isPrivate:false,minBet:1000})).roomId;
  await connect();send('seat',{seatIndex:1});await until(()=>state.seats['1']===name,'seat');
  send('start');await until(()=>state.state==='BETTING','start');
  send('bet',{betAmount:1000});await until(()=>state.state!=='BETTING','deal');
  const before=structuredClone(state);
  const durableBefore=composeMode?storedSnapshot():null;
  const money=(await api('/api/members/me')).money;
  await kill();await start();console.log("Restored rooms",await api("/api/rooms"));await connect();
  assert.equal(state.paused,true);
  if(composeMode)assert.deepEqual(storedSnapshot(),durableBefore,'MariaDB retains full ordered deck and snapshot');
  for(const key of ['state','roundId','revision','players','dealer','remainingCards','bets','seats'])assert.deepEqual(state[key],before[key],`restored ${key}`);
  assert.equal((await api('/api/members/me')).money,money);
  console.log('PASS: forced process termination restores exact cards, phase, revision, bets, wallet and authenticated session');
  send('resume');await until(()=>!state.paused,'resume');
  const sent=new Set();
  await until(async()=>{
    if(state.state==='FINISHED')return true;
    if(sent.has(state.revision))return false;
    sent.add(state.revision);
    if(state.state==='INSURANCE')send('insurance',{accept:false});
    if(state.state==='PLAYER_TURN')send('action',{action:'STAND',handIndex:state.players[0].hands.findIndex(h=>h.status==='PLAYING'),revision:state.revision});
    return false;
  },'settlement');
  const balance=(await api('/api/members/me')).money;
  const history=await api('/api/members/history');
  assert.equal(history.length,1);
  await kill();if(composeMode)compose('restart','db');await start();console.log("Restored rooms",await api("/api/rooms"));await connect();
  assert.equal(state.state,'FINISHED');assert.equal(state.paused,true);
  assert.equal((await api('/api/members/me')).money,balance);
  assert.equal((await api('/api/members/history')).length,1);
  await api(`/api/rooms/${roomId}/leave`,{});
  await api('/api/members/logout',{});
  await api('/api/members/me',undefined,401);
  console.log('PASS: settlement survives second crash with one receipt, no double payout; logout revokes access');
}finally{if(composeMode){if(client)await client.deactivate();}else await kill();}

