import { test } from 'node:test';
import assert from 'node:assert/strict';
import { CallGuard, extractJson, xmlEscape, isE164 } from './server.mjs';

test('call guard blocks duplicates and enforces hourly limit', () => {
  let now = 0;
  const g = new CallGuard(2, () => now);
  assert.equal(g.check('1-0'), 'ok');
  assert.equal(g.check('1-0'), 'duplicate');
  assert.equal(g.check('2-0'), 'ok');
  assert.equal(g.check('3-0'), 'rate_limited');
  now = 3_600_001;
  assert.equal(g.check('3-0'), 'ok');
  now += 86_400_001;
  assert.equal(g.check('1-0'), 'ok'); // keys expire after a day
});

test('extracts JSON from model text', () => {
  assert.deepEqual(extractJson('Claro! {"action":"reply","text":"oi"} '), { action: 'reply', text: 'oi' });
  assert.throws(() => extractJson('sem json'));
});

test('escapes TwiML and validates numbers', () => {
  assert.equal(xmlEscape('<Hangup/>&'), '&lt;Hangup/&gt;&amp;');
  assert.ok(isE164('+5511999998888'));
  assert.ok(!isE164('11999998888'));
});
