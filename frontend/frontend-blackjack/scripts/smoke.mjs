import assert from 'node:assert/strict';
import { Client } from '@stomp/stompjs';
const base = process.env.API_URL || 'http://localhost:8080';
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const password = 'LocalTest!123';
const stamp = Date.now().toString().slice(-8);
const users = [0, 1].map(i => ({ username: `qa${stamp}${i}`, nickname: `qa${stamp.slice(-5)}${i}`, password }));
async function api(path, body, user, method = 'POST', expected = 200) {
  const response = await fetch(base + path, { method, headers: { 'Content-Type': 'application/json', ...(user ? { Authorization: `Bearer ${user.accessToken}` } : {}) }, ...(method !== 'GET' ? { body: JSON.stringify(body ?? {}) } : {}) });
  const text = await response.text(); assert.equal(response.status, expected, `${path}: ${text}`);
  try { return JSON.parse(text); } catch { return text; }
}
async function until(predicate, message, timeout = 12000) {
  const deadline = Date.now() + timeout;
  while (Date.now() < deadline) { if (predicate()) return; await pause(50); }
  throw new Error(`Timeout: ${message}`);
}
let roomId;
const clients = [];
try {
  for (const u of users) { await api('/api/members/join', u); Object.assign(u, await api('/api/members/login', u)); }
  await api('/api/rooms', null, null, 'GET', 401);
  const room = await api('/api/rooms', { title: '자동 검증 비공개 방', maxPlayers: 2, isPrivate: true, inviteCode: 'local-check', minBet: 1000 }, users[0]);
  roomId = room.roomId; assert.equal(room.isPrivate, true);
  await api(`/api/rooms/${roomId}/enter`, { inviteCode: 'wrong' }, users[1], 'POST', 400);
  await api(`/api/rooms/${roomId}/enter`, { inviteCode: 'local-check' }, users[1]);
  await api('/api/members/me', null, { accessToken: users[0].refreshToken }, 'GET', 401);
  for (const u of users) {
    u.errors = []; u.snapshots = []; u.state = null;
    const client = new Client({ brokerURL: base.replace('http', 'ws') + '/ws-blackjack', connectHeaders: { roomId, Authorization: `Bearer ${u.accessToken}` }, reconnectDelay: 0 });
    u.client = client; clients.push(client);
    client.onConnect = () => {
      client.subscribe(`/topic/room/${roomId}`, m => { u.state = JSON.parse(m.body); u.snapshots.push(u.state); });
      client.subscribe('/user/queue/errors', m => u.errors.push(JSON.parse(m.body)));
      client.publish({ destination: '/app/game/sync', body: JSON.stringify({ roomId }) });
    };
    client.onStompError = frame => { u.errors.push({ message: frame.headers.message }); };
    client.activate(); await until(() => u.state, 'STOMP connection and sync');
  }
  function send(u, route, body = {}) { u.client.publish({ destination: `/app/game/${route}`, body: JSON.stringify({ roomId, ...body }) }); }
  send(users[0], 'seat', { seatIndex: 1, username: users[1].username });
  await until(() => users[0].state.seats['1'] === users[0].username, 'server ignores forged username');
  send(users[1], 'seat', { seatIndex: 2 });
  await until(() => users[0].state.seats['2'] === users[1].username, 'second seat');
  send(users[1], 'ready', { ready: true });
  await until(() => users[0].state.readyPlayers.includes(users[1].username), 'guest ready');
  send(users[0], 'start'); await until(() => users[0].state.state === 'BETTING', 'game start');
  send(users[0], 'bet', { betAmount: -1000 }); await until(() => users[0].errors.length === 1, 'negative bet rejected');
  assert.equal((await api('/api/members/me', null, users[0], 'GET')).money, 100000);
  const completed = new Map(); const sent = new Set(); const deadline = Date.now() + 95000;
  while (Date.now() < deadline && completed.size < 3) {
    const state = users[0].state;
    if (state.state === 'FINISHED') {
      if (!completed.has(state.roundId)) {
        for (const p of state.players) {
          assert.ok(p.hands.every(h => h.result));
          const u = users.find(u => u.username === p.name);
          assert.equal((await api('/api/members/me', null, u, 'GET')).money, p.money);
        }
        completed.set(state.roundId, state.players.map(p => ({ name: p.name, money: p.money, results: p.hands.map(h => h.result) })));
        console.log(`Round ${state.roundId}: settlement and account balances verified`);
      }
    }
    for (const u of users) {
      const s = u.state; if (!s) continue;
      const key = `${u.username}:${s.roundId}:${s.state}:${s.revision}`;
      if (sent.has(key)) continue;
      if (s.state === 'BETTING' && s.bets[u.username] === undefined) { sent.add(key); send(u, 'bet', { betAmount: 1000 }); await pause(100); }
      if (s.state === 'INSURANCE' && s.insuranceDecisions[u.username] === undefined) { sent.add(key); send(u, 'insurance', { accept: false }); await pause(100); }
      if (s.state === 'PLAYER_TURN' && s.players[s.currentPlayerIndex]?.name === u.username) {
        const p = s.players[s.currentPlayerIndex]; const handIndex = p.hands.findIndex(h => h.status === 'PLAYING');
        sent.add(key); send(u, 'action', { action: p.hands[handIndex].score < 16 ? 'HIT' : 'STAND', handIndex, revision: s.revision }); await pause(100);
      }
    }
    await pause(100);
  }
  assert.equal(completed.size, 3, 'three full rounds');
  for(const u of users) await api(`/api/rooms/${roomId}/leave`, {}, u);
  roomId=null;
  const oldAccess=users[0].accessToken;
  const old = users[0].refreshToken;
  Object.assign(users[0], await api('/api/members/reissue', { refreshToken: old }));
  await api('/api/members/reissue', { refreshToken: old }, null, 'POST', 400);
  await api('/api/members/me', null, {accessToken:oldAccess}, 'GET', 401);
  await api('/api/members/logout', {}, users[0]);
  await api('/api/members/me', null, users[0], 'GET', 401);
  await api('/api/members/reissue', { refreshToken: users[0].refreshToken }, null, 'POST', 400);
  console.log('PASS: register/login, private room, authenticated STOMP, identity spoof protection, negative bet rejection, three two-player rounds, DB settlement, refresh rotation and logout');
} finally {
  if (roomId) for (const u of users) { try { await api(`/api/rooms/${roomId}/leave`, {}, u); } catch {} }
  await Promise.all(clients.map(c => c.deactivate()));
}
