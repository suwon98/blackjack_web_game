import test from 'node:test';
import assert from 'node:assert/strict';
import { maximumBet, addBet } from '../src/utils/betting.ts';

test('MAX preserves the 50-chip remainder after a 1,100-chip surrender', () => {
  const balance = 97_400 - 1_100 + 550;
  const bet = maximumBet(balance);
  assert.equal(bet, 96_800);
  assert.equal(balance - bet, 50);
});

test('MAX preserves an already valid chip amount', () => {
  assert.equal(maximumBet(100_000), 100_000);
});

test('adding chips cannot select a remainder that the server rejects', () => {
  assert.equal(addBet(96_000, 1_000, 96_850), 96_800);
  assert.equal(addBet(96_800, 100, 96_850), 96_800);
});

test('adding a normal chip increments the selected amount', () => {
  assert.equal(addBet(1_000, 100, 96_850), 1_100);
});

test('MAX respects the server per-bet ceiling', () => {
  assert.equal(maximumBet(1_000_000_150), 1_000_000_000);
  assert.equal(addBet(999_999_900, 100_000, 1_000_000_150), 1_000_000_000);
});

test('balances below one betting unit cannot be selected', () => {
  for (const balance of [0, 50, 99, -100, NaN, Infinity]) {
    assert.equal(maximumBet(balance), 0);
  }
});

test('selecting MAX does not round the wallet balance itself', () => {
  const wallet = { balance: 96_850 };
  maximumBet(wallet.balance);
  assert.equal(wallet.balance, 96_850);
});
