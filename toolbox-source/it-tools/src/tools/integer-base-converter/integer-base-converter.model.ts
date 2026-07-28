/** 保留非法字符的原始值，避免用分隔符序列化错误参数时丢失 `|` 等字符。 */
export class InvalidDigitError extends Error {
  readonly code = 'INVALID_DIGIT';

  constructor(
    readonly digit: string,
    readonly base: number,
  ) {
    super('INVALID_DIGIT');
    this.name = 'InvalidDigitError';
  }
}

export function convertBase({ value, fromBase, toBase }: { value: string; fromBase: number; toBase: number }) {
  const range = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ+/'.split('');
  const fromRange = range.slice(0, fromBase);
  const toRange = range.slice(0, toBase);
  let decValue = value
    .split('')
    .reverse()
    .reduce((carry: bigint, digit: string, index: number) => {
      if (!fromRange.includes(digit)) {
        throw new InvalidDigitError(digit, fromBase);
      }
      return (carry += BigInt(fromRange.indexOf(digit)) * BigInt(fromBase) ** BigInt(index));
    }, 0n);
  let newValue = '';
  while (decValue > 0) {
    newValue = toRange[Number(decValue % BigInt(toBase))] + newValue;
    decValue = (decValue - (decValue % BigInt(toBase))) / BigInt(toBase);
  }
  return newValue || '0';
}
