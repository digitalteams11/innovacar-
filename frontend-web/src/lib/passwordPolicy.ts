// Single source of truth for the password policy on the frontend — must stay
// in sync with the backend's PasswordPolicyService.validate(): 8+ characters,
// at least one uppercase, one lowercase, one digit, one special character.
export const PASSWORD_MIN_LENGTH = 8;

export const PASSWORD_PATTERN =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/;

export interface PasswordStrength {
  len: boolean;
  upper: boolean;
  lower: boolean;
  digit: boolean;
  sym: boolean;
}

export function checkPasswordStrength(password: string): PasswordStrength {
  return {
    len: password.length >= PASSWORD_MIN_LENGTH,
    upper: /[A-Z]/.test(password),
    lower: /[a-z]/.test(password),
    digit: /\d/.test(password),
    sym: /[^A-Za-z0-9]/.test(password),
  };
}

export function isPasswordStrong(password: string): boolean {
  return Object.values(checkPasswordStrength(password)).every(Boolean);
}
