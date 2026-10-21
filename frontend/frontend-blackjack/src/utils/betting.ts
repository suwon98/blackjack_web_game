export const BET_STEP = 100;
export const MAX_BET = 1_000_000_000;

export function maximumBet(balance: number): number {
  if (!Number.isFinite(balance) || balance <= 0) return 0;
  return Math.floor(Math.min(balance, MAX_BET) / BET_STEP) * BET_STEP;
}

export function addBet(current: number, increment: number, balance: number): number {
  return normalizeBet(current + increment, balance);
}

export function normalizeBet(amount: number, balance: number): number {
  if (!Number.isFinite(amount)) return 0;
  return Math.max(0, Math.min(Math.floor(amount / BET_STEP) * BET_STEP, maximumBet(balance)));
}
