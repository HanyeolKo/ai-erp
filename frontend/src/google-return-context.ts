const STORAGE_KEY = "ai-erp.google-return";
const MAX_AGE_MS = 10 * 60 * 1000;

export type GoogleReturnContext = {
  userId: string;
  returnTo: string;
  createdAt: number;
};

export const isGoogleProjectFilesPath = (value: unknown): value is string =>
  typeof value === "string" && /^\/projects\/[A-Za-z0-9_-]+\/files$/.test(value);

function readStoredContext(): GoogleReturnContext | undefined {
  if (typeof window === "undefined") return undefined;
  try {
    const raw = window.sessionStorage.getItem(STORAGE_KEY);
    if (!raw) return undefined;
    const value: unknown = JSON.parse(raw);
    if (
      !value ||
      typeof value !== "object" ||
      typeof (value as Partial<GoogleReturnContext>).userId !== "string" ||
      !(value as Partial<GoogleReturnContext>).userId ||
      !isGoogleProjectFilesPath((value as Partial<GoogleReturnContext>).returnTo) ||
      typeof (value as Partial<GoogleReturnContext>).createdAt !== "number" ||
      !Number.isFinite((value as Partial<GoogleReturnContext>).createdAt)
    )
      return undefined;
    const context = value as GoogleReturnContext;
    const age = Date.now() - context.createdAt;
    return age >= 0 && age <= MAX_AGE_MS ? context : undefined;
  } catch {
    return undefined;
  }
}

export function saveGoogleReturnContext(userId: string, returnTo: string, createdAt = Date.now()) {
  if (!userId || !isGoogleProjectFilesPath(returnTo) || !Number.isFinite(createdAt) || Date.now() - createdAt < 0 || Date.now() - createdAt > MAX_AGE_MS) {
    clearGoogleReturnContext();
    return false;
  }
  if (typeof window === "undefined") return false;
  try {
    window.sessionStorage.setItem(
      STORAGE_KEY,
      JSON.stringify({ userId, returnTo, createdAt }),
    );
    return true;
  } catch {
    // An unavailable session store must not block navigation to settings.
    return false;
  }
}

export function consumeGoogleReturnContext(userId: string) {
  const context = readStoredContext();
  clearGoogleReturnContext();
  return context?.userId === userId ? context : undefined;
}

export function getGoogleReturnContext(userId: string) {
  const context = readStoredContext();
  return context?.userId === userId ? context : undefined;
}

export function clearGoogleReturnContext() {
  if (typeof window === "undefined") return;
  try {
    window.sessionStorage.removeItem(STORAGE_KEY);
  } catch {
    // An unavailable session store is treated as an empty store.
  }
}
