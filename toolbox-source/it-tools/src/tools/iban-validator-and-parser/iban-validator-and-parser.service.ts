import { ValidationErrorsIBAN } from 'ibantools';

export { getKnownErrorCodes };

const knownErrorCodes = new Set(Object.values(ValidationErrorsIBAN));

function getKnownErrorCodes(errorCodes: ValidationErrorsIBAN[]) {
  return errorCodes.filter(errorCode => knownErrorCodes.has(errorCode));
}
