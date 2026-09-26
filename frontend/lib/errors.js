/** Friendly copy for API error codes (Docs/Design.md §4 "Errors", §7). Never blames the user. */
const MESSAGES = {
  NETWORK_ERROR: "We can't reach the server right now. Check your connection and try again.",
  INVALID_CREDENTIALS: "That email and password don't match. Try again or reset your password.",
  ACCOUNT_DISABLED: 'This account has been deactivated. Please contact the clinic administrator.',
  EMAIL_TAKEN: 'An account with this email already exists. Try signing in instead.',
  UNAUTHENTICATED: 'Your session has ended. Please sign in again.',
  FORBIDDEN: "You don't have access to this area.",
  NO_CARE_RELATIONSHIP:
    "You don't have an appointment with this patient. Ask the front desk to book them in.",
  CSRF_HEADER_MISSING: 'Your request was blocked for security reasons. Refresh the page and try again.',
  VALIDATION_FAILED: 'Some details need fixing — see the highlighted fields.',
  INTERNAL_ERROR: 'Something went wrong on our side. Please try again in a moment.',
};

export function friendlyMessage(error) {
  if (!error) return null;
  return MESSAGES[error.code] ?? error.message ?? MESSAGES.INTERNAL_ERROR;
}
