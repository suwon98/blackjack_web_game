import assert from 'node:assert/strict';
import { Client } from '@stomp/stompjs';

const base = process.env.API_URL || 'http://localhost:18081';
const stamp = Date.now().toString().slice(-8);
const clients = [];
const users = [];
async function request(path, body, token) {
  return fetch(base + path, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
}
async function until(predicate, label) {
  const end = Date.now() + 8000;
  while (Date.now() < end) { if (predicate()) return; await new Promise(resolve => setTimeout(resolve, 40)); }
  throw new Error(`Timeout: ${label}`);
}
async function connect(user) {
  const state = { messages: [], errors: [], denied: false };
  const client = new Client({
    brokerURL: base.replace(/^http/, 'ws') + '/ws-blackjack',
    connectHeaders: { channel: 'lobby', Authorization: `Bearer ${user.accessToken}` },
    reconnectDelay: 0,
  });
  clients.push(client);
  client.onStompError = () => { state.denied = true; };
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error('CONNECT timeout')), 8000);
    client.onConnect = () => {
      client.subscribe('/user/queue/errors', message => state.errors.push(JSON.parse(message.body)));
      client.subscribe('/topic/chat/lobby', message => state.messages.push(JSON.parse(message.body)));
      setTimeout(() => { clearTimeout(timer); resolve(); }, 250);
    };
    client.activate();
  });
  state.client = client;
  state.send = (text, roomId = null, kind = 'TEXT') => client.publish({
    destination: '/app/chat/send', body: JSON.stringify({ roomId, kind, text, username: 'forged', nickname: 'forged' }),
  });
  return state;
}

try {
  for (let i = 0; i < 3; i++) {
    const user = { username: `chat${stamp}${i}`, nickname: `c${stamp}${i}`, password: 'ChatTesting!123' };
    assert.equal((await request('/api/members/join', user)).status, 200);
    Object.assign(user, await (await request('/api/members/login', user)).json());
    users.push(user);
  }
  const room = await (await request('/api/rooms', { title: 'Chat verification', maxPlayers: 3, minBet: 1000, isPrivate: true, inviteCode: 'chat-only' }, users[0].accessToken)).json();
  users[0].room = room.roomId;
  assert.equal((await request(`/api/rooms/${room.roomId}/enter`, { inviteCode: 'chat-only' }, users[1].accessToken)).status, 200);
  users[1].room = room.roomId;
  const [a, b, outsider] = await Promise.all(users.map(connect));
  a.send('<img src=x onerror=alert(1)>');
  await until(() => b.messages.some(message => message.text.startsWith('<img')), 'lobby message');
  const received = b.messages.find(message => message.text.startsWith('<img'));
  assert.equal(received.username, users[0].username);
  assert.equal(received.nickname, users[0].nickname);
  const roomMessages = [];
  b.client.subscribe(`/topic/chat/room/${room.roomId}`, message => roomMessages.push(JSON.parse(message.body)));
  await new Promise(resolve => setTimeout(resolve, 150));
  a.send('👋', room.roomId, 'EMOJI');
  await until(() => roomMessages.length === 1, 'room emoji');
  assert.equal(roomMessages[0].kind, 'EMOJI');
  assert.equal(outsider.messages.some(message => message.kind === 'EMOJI'), false);
  outsider.send('intrusion', room.roomId);
  await until(() => outsider.errors.some(error => error.message.includes('입장')), 'outsider SEND rejection');
  a.send('x'.repeat(301));
  await until(() => a.errors.some(error => error.message.includes('300')), 'length rejection');
  a.send('invalid', null, 'EMOJI');
  await until(() => a.errors.some(error => error.message.includes('이모지')), 'emoji validation');
  for (let i = 0; i < 6; i++) a.send(`rate-${i}`);
  await until(() => a.errors.some(error => error.message.includes('10초')), 'per-user rate limit');
  assert.equal((await request('/api/members/recharge', {}, users[0].accessToken)).status, 400);
  await request(`/api/rooms/${room.roomId}/leave`, {}, users[1].accessToken);
  const before = roomMessages.length;
  outsider.client.subscribe(`/topic/chat/room/${room.roomId}`, () => assert.fail('Unauthorized subscription delivered data'));
  await until(() => outsider.denied, 'outsider SUBSCRIBE rejection');
  await new Promise(resolve => setTimeout(resolve, 10100));
  a.send('after-leave', room.roomId);
  await new Promise(resolve => setTimeout(resolve, 500));
  assert.equal(roomMessages.length, before, 'departed member received room chat');
  await request('/api/members/logout', {}, users[1].accessToken);
  const lobbyBefore = b.messages.length;
  a.send('after-logout');
  await new Promise(resolve => setTimeout(resolve, 500));
  assert.equal(b.messages.length, lobbyBefore, 'revoked session received lobby chat');
  console.log('PASS: lobby delivery, server identity, room emoji, channel isolation, membership enforcement, length/emoji/rate limits, recharge eligibility and logout revocation');
} finally {
  await Promise.all(clients.map(client => client.deactivate()));
  for (const user of users) {
    if (user.room) await request(`/api/rooms/${user.room}/leave`, {}, user.accessToken);
  }
}
