const http = require('http');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 8080;
const MAX_MSG_LEN = 500;
const MAX_CONN_PER_IP = 5;
const REPORTS_TO_BAN = 3;
const REPORT_WINDOW_MS = 24 * 60 * 60 * 1000;
const BAN_MS = 24 * 60 * 60 * 1000;

const bans = new Map();    // ip -> ban expiry (ms)
const reports = new Map(); // ip -> [timestamps of reports against it]
const queue = [];          // sockets waiting for a partner

function iceServers() {
  const list = [{ urls: 'stun:stun.l.google.com:19302' }];
  if (process.env.TURN_URL) {
    list.push({
      urls: process.env.TURN_URL,
      username: process.env.TURN_USER || '',
      credential: process.env.TURN_PASS || '',
    });
  }
  return list;
}

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end(req.url === '/health' ? 'ok' : 'random-chat signaling server');
});

const wss = new WebSocketServer({ server, maxPayload: 64 * 1024 });

function send(ws, obj) {
  if (ws && ws.readyState === 1) ws.send(JSON.stringify(obj));
}

function isBanned(ip) {
  const until = bans.get(ip);
  if (!until) return false;
  if (Date.now() > until) {
    bans.delete(ip);
    return false;
  }
  return true;
}

function removeFromQueue(ws) {
  const i = queue.indexOf(ws);
  if (i >= 0) queue.splice(i, 1);
}

function unpair(ws) {
  const p = ws.partner;
  if (!p) return;
  ws.lastPartnerIp = p.ip;
  p.lastPartnerIp = ws.ip;
  ws.partner = null;
  p.partner = null;
  send(p, { type: 'partner_left' });
}

function enqueue(ws) {
  removeFromQueue(ws);
  unpair(ws);
  queue.push(ws);
  send(ws, { type: 'waiting' });
  tryMatch();
}

function tryMatch() {
  while (queue.length >= 2) {
    const a = queue.shift();
    const b = queue.shift();
    if (a.readyState !== 1) {
      if (b.readyState === 1) queue.unshift(b);
      continue;
    }
    if (b.readyState !== 1) {
      queue.unshift(a);
      continue;
    }
    a.partner = b;
    b.partner = a;
    const servers = iceServers();
    send(a, { type: 'matched', initiator: true, iceServers: servers });
    send(b, { type: 'matched', initiator: false, iceServers: servers });
  }
}

function report(ws) {
  const target = ws.partner ? ws.partner.ip : ws.lastPartnerIp;
  if (!target || target === ws.ip) return;
  ws.reported = ws.reported || new Set();
  if (ws.reported.has(target)) return; // one report per reporter per target
  ws.reported.add(target);

  const now = Date.now();
  const list = (reports.get(target) || []).filter((t) => now - t < REPORT_WINDOW_MS);
  list.push(now);
  reports.set(target, list);

  if (list.length >= REPORTS_TO_BAN) {
    bans.set(target, now + BAN_MS);
    for (const client of wss.clients) {
      if (client.ip === target) {
        send(client, { type: 'banned' });
        client.close();
      }
    }
  }
}

function handle(ws, m) {
  switch (m.type) {
    case 'join':
      if (m.age18 !== true) {
        return send(ws, { type: 'error', message: 'You must confirm you are 18 or older.' });
      }
      ws.joined = true;
      enqueue(ws);
      break;

    case 'next':
      if (ws.joined) enqueue(ws);
      break;

    case 'chat': {
      if (!ws.partner || typeof m.text !== 'string') return;
      const text = m.text.trim().slice(0, MAX_MSG_LEN);
      if (!text) return;
      const now = Date.now();
      ws.msgTimes = ws.msgTimes.filter((t) => now - t < 3000);
      if (ws.msgTimes.length >= 5) {
        return send(ws, { type: 'error', message: 'Slow down a little.' });
      }
      ws.msgTimes.push(now);
      send(ws.partner, { type: 'chat', text });
      break;
    }

    case 'signal':
      if (ws.partner && m.data && typeof m.data === 'object') {
        send(ws.partner, { type: 'signal', data: m.data });
      }
      break;

    case 'report':
      report(ws);
      break;

    case 'leave':
      removeFromQueue(ws);
      unpair(ws);
      ws.joined = false;
      break;
  }
}

wss.on('connection', (ws, req) => {
  const fwd = (req.headers['x-forwarded-for'] || '').split(',')[0].trim();
  ws.ip = fwd || req.socket.remoteAddress || 'unknown';
  ws.alive = true;
  ws.partner = null;
  ws.joined = false;
  ws.msgTimes = [];
  ws.lastPartnerIp = null;

  if (isBanned(ws.ip)) {
    send(ws, { type: 'banned' });
    return ws.close();
  }

  let sameIp = 0;
  for (const c of wss.clients) if (c.ip === ws.ip) sameIp++;
  if (sameIp > MAX_CONN_PER_IP) {
    send(ws, { type: 'error', message: 'Too many connections.' });
    return ws.close();
  }

  ws.on('pong', () => { ws.alive = true; });

  ws.on('message', (raw) => {
    let m;
    try {
      m = JSON.parse(raw.toString());
    } catch {
      return;
    }
    if (m && typeof m === 'object') handle(ws, m);
  });

  ws.on('close', () => {
    removeFromQueue(ws);
    unpair(ws);
  });

  ws.on('error', () => {});
});

// Heartbeat: drop dead connections.
setInterval(() => {
  for (const ws of wss.clients) {
    if (!ws.alive) {
      ws.terminate();
      continue;
    }
    ws.alive = false;
    ws.ping();
  }
}, 30000);

// Online counter.
setInterval(() => {
  const count = wss.clients.size;
  for (const ws of wss.clients) send(ws, { type: 'online', count });
}, 5000);

server.listen(PORT, () => console.log(`Signaling server listening on :${PORT}`));

module.exports = { server, wss };
