export const BET_STEP = 100;
export const MAX_BET = 1_000_000_000;

export function maximumBet(balance: number): number {
  if (!Number.isFinite(balance) || balance <= 0) return 0;
  return Math.floor(Math.min(balance, MAX_BET) / BET_STEP) * BET_STEP;
}

export function addBet(current: number, increment: number, balance: number): number {
  return Math.min(current + increment, maximumBet(balance));
}
