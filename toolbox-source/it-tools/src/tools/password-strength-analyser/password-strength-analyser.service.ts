import _ from 'lodash';

export { getPasswordCrackTimeEstimation, getCharsetLength };

export type CrackDuration =
  | { kind: 'INSTANT' }
  | { kind: 'LESS_THAN_SECOND' }
  | { kind: 'PARTS'; parts: Array<{ code: string; quantity: number; formattedQuantity: string }> };

function prettifyExponentialNotation(exponentialNotation: number) {
  const [base, exponent] = exponentialNotation.toString().split('e');
  const baseAsNumber = Number.parseFloat(base);
  const prettyBase = baseAsNumber % 1 === 0 ? baseAsNumber.toLocaleString() : baseAsNumber.toFixed(2);
  return exponent ? `${prettyBase}e${exponent}` : prettyBase;
}

function getHumanFriendlyDuration({ seconds }: { seconds: number }) {
  if (seconds <= 0.001) {
    return { kind: 'INSTANT' } as const;
  }

  if (seconds <= 1) {
    return { kind: 'LESS_THAN_SECOND' } as const;
  }

  const timeUnits = [
    { code: 'MILLENNIUM', secondsInUnit: 31536000000, format: prettifyExponentialNotation },
    { code: 'CENTURY', secondsInUnit: 3153600000 },
    { code: 'DECADE', secondsInUnit: 315360000 },
    { code: 'YEAR', secondsInUnit: 31536000 },
    { code: 'MONTH', secondsInUnit: 2592000 },
    { code: 'WEEK', secondsInUnit: 604800 },
    { code: 'DAY', secondsInUnit: 86400 },
    { code: 'HOUR', secondsInUnit: 3600 },
    { code: 'MINUTE', secondsInUnit: 60 },
    { code: 'SECOND', secondsInUnit: 1 },
  ];

  const parts = _.chain(timeUnits)
    .map(({ code, secondsInUnit, format = _.identity }) => {
      const quantity = Math.floor(seconds / secondsInUnit);
      seconds %= secondsInUnit;

      if (quantity <= 0) {
        return undefined;
      }

      const formattedQuantity = format(quantity);
      return { code, quantity, formattedQuantity: String(formattedQuantity) };
    })
    .compact()
    .take(2)
    .value();

  return { kind: 'PARTS', parts } as const;
}

function getPasswordCrackTimeEstimation({ password, guessesPerSecond = 1e9 }: { password: string; guessesPerSecond?: number }) {
  const charsetLength = getCharsetLength({ password });
  const passwordLength = password.length;

  const entropy = password === '' ? 0 : Math.log2(charsetLength) * passwordLength;

  const secondsToCrack = 2 ** entropy / guessesPerSecond;

  const crackDuration = getHumanFriendlyDuration({ seconds: secondsToCrack });

  const score = Math.min(entropy / 128, 1);

  return {
    entropy,
    charsetLength,
    passwordLength,
    crackDuration,
    secondsToCrack,
    score,
  };
}

function getCharsetLength({ password }: { password: string }) {
  const hasLowercase = /[a-z]/.test(password);
  const hasUppercase = /[A-Z]/.test(password);
  const hasDigits = /\d/.test(password);
  const hasSpecialChars = /\W|_/.test(password);

  let charsetLength = 0;

  if (hasLowercase) {
    charsetLength += 26;
  }
  if (hasUppercase) {
    charsetLength += 26;
  }
  if (hasDigits) {
    charsetLength += 10;
  }
  if (hasSpecialChars) {
    charsetLength += 32;
  }

  return charsetLength;
}
