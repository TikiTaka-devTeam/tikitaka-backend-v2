// Node.js 24+, no npm dependencies. Uses Spring SockJS's raw WebSocket endpoint.
import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { performance } from 'node:perf_hooks';
import { setTimeout as sleep } from 'node:timers/promises';
import { pathToFileURL } from 'node:url';

export function summary(values) {
  const sorted = [...values].sort((a, b) => a - b);
  const percentile = p => sorted.length ? sorted[Math.ceil(sorted.length * p) - 1] : null;
  return { count: sorted.length, p50_ms: percentile(.5), p95_ms: percentile(.95),
    p99_ms: percentile(.99), max_ms: sorted.at(-1) ?? null };
}

export function canonical(strokes) {
  return JSON.stringify(strokes.filter(s => !s.is_deleted).map(s => ({
    stroke_id: s.stroke_id, tool: s.tool, color: s.color, thickness: s.thickness,
    opacity: s.opacity, stroke_order: s.stroke_order,
    points: s.points.map(p => [p.x_ratio, p.y_ratio]),
  })).sort((a, b) => a.stroke_id.localeCompare(b.stroke_id)));
}

export function applyCommitted(model, event) {
  if (event.version <= model.version) return 'duplicate';
  if (event.version !== model.version + 1) return 'gap';
  for (const id of event.deleted_stroke_ids) model.strokes.delete(id);
  for (const stroke of event.created_strokes) model.strokes.set(stroke.stroke_id, stroke);
  model.version = event.version;
  return 'applied';
}

function eventKey(event) {
  return `${event.client_stroke_id}:${event.type}:${event.chunk_seq ?? ''}`;
}

class StompClient {
  constructor(id, token, context) {
    Object.assign(this, { id, token, context, ready: false, planned: false,
      model: { version: 0, strokes: new Map() }, buffer: [], initializing: true });
  }
  async connect() {
    this.planned = false;
    let incoming = '';
    await new Promise((resolve, reject) => {
      const socket = new WebSocket(this.context.wsUrl, 'v12.stomp');
      this.ws = socket;
      const timeout = setTimeout(() => {
        reject(new Error(`${this.id}: CONNECT timeout`)); socket.close();
      }, 10000);
      socket.onopen = () => this.frame('CONNECT', {
        'accept-version': '1.2', host: new URL(this.context.base).host,
        Authorization: `Bearer ${this.token}`, 'heart-beat': '0,0',
      });
      socket.onmessage = ({ data }) => {
        incoming += data;
        let end;
        while ((end = incoming.indexOf('\0')) !== -1) {
          const frame = incoming.slice(0, end).replace(/^\s+/, '').replaceAll('\r\n', '\n');
          incoming = incoming.slice(end + 1);
          const separator = frame.indexOf('\n\n');
          const command = frame.split('\n')[0];
          if (command === 'CONNECTED') {
            clearTimeout(timeout);
            this.frame('SUBSCRIBE', { id: this.id, destination: this.context.topic, ack: 'auto' });
            resolve();
          } else if (command === 'MESSAGE') {
            try { this.receive(JSON.parse(frame.slice(separator + 2))); }
            catch (error) { this.context.errors.push(`${this.id}: ${error.message}`); }
          } else if (command === 'ERROR') {
            clearTimeout(timeout);
            const error = new Error(`${this.id}: STOMP ERROR (check server authentication/access logs)`);
            reject(error); this.context.errors.push(error.message);
          }
        }
      };
      socket.onerror = () => { clearTimeout(timeout); reject(new Error(`${this.id}: socket error`)); };
      socket.onclose = () => {
        clearTimeout(timeout);
        if (this.ws === socket && !this.planned) {
          this.ready = false;
          const error = new Error(`${this.id}: unexpected disconnect`);
          this.context.errors.push(error.message); reject(error);
        }
      };
    });
  }
  frame(command, headers = {}, body = '') {
    if (this.ws?.readyState !== WebSocket.OPEN) throw new Error(`${this.id}: socket not open`);
    this.ws.send(`${command}\n${Object.entries(headers).map(([k, v]) => `${k}:${v}`).join('\n')}\n\n${body}\0`);
  }
  send(event) {
    this.frame('SEND', { destination: this.context.destination, 'content-type': 'application/json' }, JSON.stringify(event));
  }
  receive(event) {
    if (this.context.probes.has(event.client_stroke_id)) {
      if (event.type === 'STROKE_START') this.context.probes.get(event.client_stroke_id).add(this.id);
      return;
    }
    if (this.id === 'writer') return;
    const sent = this.context.events.get(eventKey(event));
    if (sent && sent.expected.has(this.id)) {
      if (sent.received.has(this.id)) this.context.duplicates++;
      else {
        sent.received.add(this.id);
        this.context.latencies.push(performance.now() - sent.at);
      }
    }
    if (event.type !== 'SHARED_STROKES_SYNCED') return;
    if (this.initializing) this.buffer.push(event);
    else if (applyCommitted(this.model, event) === 'gap') {
      this.initializing = true;
      this.buffer.push(event);
      this.restore().catch(error => this.context.errors.push(`${this.id}: ${error.message}`));
    }
  }
  async restore() {
    this.initializing = true;
    for (let attempt = 0; attempt < 5; attempt++) {
      const snapshot = await this.context.request(this.token);
      this.model = { version: snapshot.version,
        strokes: new Map(snapshot.strokes.map(stroke => [stroke.stroke_id, stroke])) };
      const buffered = this.buffer.splice(0).sort((a, b) => a.version - b.version);
      if (buffered.some(event => applyCommitted(this.model, event) === 'gap')) continue;
      this.initializing = false;
      return;
    }
    throw new Error('snapshot recovery did not converge');
  }
  close() {
    this.planned = true; this.ready = false;
    this.ws?.close();
  }
}

async function main() {
  if (!process.argv.includes('--confirm-write')) {
    throw new Error('This test creates permanent strokes. Use a dedicated test slide and pass --confirm-write.');
  }
  const env = process.env;
  for (const name of ['SYNC_BASE_URL', 'SYNC_SPACE_ID', 'SYNC_SLIDE_ID', 'SYNC_WRITER_TOKEN', 'SYNC_READER_TOKENS']) {
    if (!env[name]) throw new Error(`Missing ${name}`);
  }
  const number = (name, fallback, min, max) => {
    const value = Number(env[name] ?? fallback);
    if (!Number.isInteger(value) || value < min || value > max) throw new Error(`Invalid ${name}: ${min}..${max}`);
    return value;
  };
  const count = number('SYNC_READERS', 5, 1, 50);
  const duration = number('SYNC_DURATION_SECONDS', 60, 30, 600);
  const rate = number('SYNC_STROKES_PER_SECOND', 1, 1, 5);
  const outage = number('SYNC_OUTAGE_SECONDS', 10, 1, 20);
  const context = {
    base: env.SYNC_BASE_URL.replace(/\/$/, ''), events: new Map(), probes: new Map(),
    errors: [], latencies: [], duplicates: 0,
  };
  const wsUrl = new URL(`${context.base}/ws/websocket`);
  wsUrl.protocol = wsUrl.protocol === 'https:' ? 'wss:' : 'ws:';
  context.wsUrl = wsUrl.href;
  context.topic = `/topic/spaces/${env.SYNC_SPACE_ID}/slides/${env.SYNC_SLIDE_ID}/shared-strokes`;
  context.destination = `/app/spaces/${env.SYNC_SPACE_ID}/slides/${env.SYNC_SLIDE_ID}/shared-strokes/live`;
  context.request = async (token, body) => {
    const response = await fetch(`${context.base}/api/v1/slides/${env.SYNC_SLIDE_ID}/shared-strokes${body ? '/sync' : ''}`, {
      method: body ? 'POST' : 'GET', headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      ...(body ? { body: JSON.stringify(body) } : {}), signal: AbortSignal.timeout(10000),
    });
    if (!response.ok) throw new Error(`Shared strokes HTTP ${response.status}; test stopped`);
    return response.json();
  };
  const tokens = env.SYNC_READER_TOKENS.split(',').map(token => token.trim()).filter(Boolean);
  if (tokens.length !== 1 && tokens.length !== count) throw new Error('Provide either one reader token or one per reader');
  for (const token of [env.SYNC_WRITER_TOKEN, ...tokens]) {
    const claims = JSON.parse(Buffer.from(token.split('.')[1] ?? '', 'base64url').toString());
    if (!claims.exp || claims.exp * 1000 < Date.now() + (duration + outage + count * 2 + 90) * 1000) {
      throw new Error('Access token expires too soon; obtain fresh tokens before the test');
    }
  }
  const writer = new StompClient('writer', env.SYNC_WRITER_TOKEN, context);
  const readers = Array.from({ length: count }, (_, i) => new StompClient(`reader-${i}`, tokens[i % tokens.length], context));
  const saveTimes = [], recoveries = [];
  let created = 0, excluded = 0, recoveryTask;
  const startedAt = new Date().toISOString();
  const style = { tool: 'PEN', color: '#3366FF', thickness: 2, opacity: 1, stroke_order: 0 };
  const point = i => ({ x_ratio: (i % 100) / 100, y_ratio: ((i * 3) % 100) / 100 });
  async function barrier(clients) {
    const deadline = performance.now() + 10000;
    while (performance.now() < deadline) {
      const id = randomUUID(), received = new Set();
      context.probes.set(id, received);
      writer.send({ type: 'STROKE_START', client_stroke_id: id, ...style, point: point(0) });
      writer.send({ type: 'STROKE_CANCEL', client_stroke_id: id });
      for (let i = 0; i < 10; i++) {
        if (clients.every(client => received.has(client.id))) return;
        await sleep(50);
      }
    }
    throw new Error('Subscription barrier timeout');
  }
  async function recover(reader) {
    reader.close();
    // Exclude still-in-flight deliveries at the intentional disconnect boundary.
    for (const event of context.events.values()) {
      if (event.expected.has(reader.id) && !event.received.has(reader.id)) {
        event.expected.delete(reader.id); excluded++;
      }
    }
    await sleep(outage * 1000);
    const start = performance.now();
    reader.initializing = true; reader.buffer = [];
    await reader.connect();
    await barrier([reader]);
    await reader.restore();
    reader.ready = true;
    recoveries.push(performance.now() - start);
  }
  try {
    await writer.connect();
    // Spread handshake/authentication requests instead of spiking a 1GB server.
    for (const reader of readers) { await reader.connect(); await sleep(50); }
    await barrier(readers);
    for (const reader of readers) { await reader.restore(); reader.ready = true; }
    let { version, strokes } = await context.request(writer.token);
    let order = Math.max(-1, ...strokes.map(stroke => stroke.stroke_order)) + 1;
    const start = performance.now();
    console.log(`Started: ${count} reader connections, ${duration}s, ${rate} strokes/s`);
    while (performance.now() - start < duration * 1000) {
      if (context.errors.length) throw new Error(context.errors[0]);
      const tick = performance.now();
      if (!recoveryTask && tick - start >= duration * 500) {
        recoveryTask = recover(readers[0]);
        // Observe errors immediately to avoid unhandled rejections while sending.
        recoveryTask.catch(error => context.errors.push(error.message));
      }
      const id = randomUUID();
      const styleWithOrder = { ...style, stroke_order: order++ };
      const points = Array.from({ length: 81 }, (_, i) => point(i));
      const send = event => {
        context.events.set(eventKey(event), { at: performance.now(),
          expected: new Set(readers.filter(reader => reader.ready).map(reader => reader.id)), received: new Set() });
        writer.send(event);
      };
      send({ type: 'STROKE_START', client_stroke_id: id, ...styleWithOrder, point: points[0] });
      for (let chunk = 1; chunk <= 4; chunk++) {
        await sleep(10);
        send({ type: 'STROKE_POINTS', client_stroke_id: id, chunk_seq: chunk,
          points: points.slice(1 + (chunk - 1) * 20, 1 + chunk * 20) });
      }
      send({ type: 'STROKE_END', client_stroke_id: id, last_chunk_seq: 4 });
      const saveStart = performance.now();
      const response = await context.request(writer.token, {
        base_version: version, operations: [{ client_operation_id: randomUUID(), type: 'CREATE',
          stroke: { client_stroke_id: id, ...styleWithOrder, points } }],
      });
      saveTimes.push(performance.now() - saveStart);
      version = response.version; created++;
      await sleep(Math.max(0, 1000 / rate - (performance.now() - tick)));
    }
    if (recoveryTask) await recoveryTask;
    await sleep(3000); // Fixed drain window; missing messages remain failures after this deadline.
    if (context.errors.length) throw new Error(context.errors[0]);
    const server = await context.request(writer.token);
    const reference = canonical(server.strokes);
    const matched = readers.filter(reader => !reader.initializing && reader.model.version === server.version
      && canonical([...reader.model.strokes.values()]) === reference).length;
    let expected = 0, received = 0;
    for (const event of context.events.values()) { expected += event.expected.size; received += event.received.size; }
    const report = {
      status: expected === received && matched === count ? 'passed' : 'failed',
      started_at: startedAt, ended_at: new Date().toISOString(),
      reader_connections: count, distinct_reader_accounts: new Set(tokens).size,
      configured_duration_seconds: duration, configured_strokes_per_second: rate,
      created_strokes: created, expected_live_deliveries: expected, received_live_deliveries: received,
      excluded_at_disconnect_boundary: excluded,
      missing_live_deliveries: expected - received, missing_ratio: expected ? (expected - received) / expected : null,
      duplicate_live_deliveries: context.duplicates, delivery: summary(context.latencies),
      rest_save: summary(saveTimes), reconnect_and_restore: summary(recoveries),
      final_version: server.version, matching_readers: matched, errors: context.errors,
      note: 'Protocol receive latency, not browser render time. Recovery sample count is one. No automatic cleanup.',
    };
    await mkdir('results', { recursive: true });
    const filename = `results/sync-01-${Date.now()}.json`;
    await writeFile(filename, JSON.stringify(report, null, 2));
    console.log(JSON.stringify(report, null, 2)); console.log(`Saved ${filename}`);
    if (report.status !== 'passed') process.exitCode = 1;
  } catch (error) {
    await mkdir('results', { recursive: true });
    await writeFile(`results/sync-01-failed-${Date.now()}.json`, JSON.stringify({
      status: 'aborted', started_at: startedAt, ended_at: new Date().toISOString(),
      created_strokes: created, error: error.message, errors: context.errors,
    }, null, 2));
    throw error;
  } finally {
    // If an abort happened during the injected outage, settle recovery before closing its new socket.
    if (recoveryTask) await recoveryTask.catch(() => {});
    for (const client of [writer, ...readers]) client.close();
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch(error => { console.error(error.message); process.exitCode = 1; });
}
