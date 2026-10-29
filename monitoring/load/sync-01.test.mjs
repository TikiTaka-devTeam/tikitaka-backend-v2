import test from 'node:test';
import assert from 'node:assert/strict';
import { summary, canonical, applyCommitted } from './sync-01.mjs';

test('tail latency uses nearest-rank percentiles, empty data does not become zero', () => {
  assert.equal(summary([]).p95_ms, null);
  const result = summary(Array.from({ length: 100 }, (_, i) => 100 - i));
  assert.equal(result.p50_ms, 50);
  assert.equal(result.p95_ms, 95);
  assert.equal(result.p99_ms, 99);
});

test('duplicate versions cannot duplicate strokes; gaps require a snapshot', () => {
  const model = { version: 0, strokes: new Map() };
  const event = { version: 1, created_strokes: [{ stroke_id: 'a' }], deleted_stroke_ids: [] };
  assert.equal(applyCommitted(model, event), 'applied');
  assert.equal(applyCommitted(model, event), 'duplicate');
  assert.equal(model.strokes.size, 1);
  assert.equal(applyCommitted(model, { ...event, version: 3 }), 'gap');
  assert.equal(model.version, 1);
  assert.equal(applyCommitted(model, { version: 2, created_strokes: [], deleted_stroke_ids: ['a'] }), 'applied');
  assert.equal(model.strokes.size, 0);
});

test('state equality ignores field order but detects geometry differences', () => {
  const stroke = { stroke_id: 'a', points: [{ x_ratio: .1, y_ratio: .2 }] };
  assert.equal(canonical([stroke]), canonical([{ ...stroke, client_stroke_id: 'temporary-id' }]));
  assert.notEqual(canonical([stroke]), canonical([{ ...stroke, points: [{ x_ratio: .2, y_ratio: .2 }] }]));
});
