let generation = 0;
let terminated = false;
export const SESSION_EXPIRED_EVENT = "ai-erp:session-expired";

export class SessionTerminatedError extends Error {
    constructor() {
        super("SESSION_EXPIRED");
        this.name = "SessionTerminatedError";
    }
}

export function beginSessionBoundary() {
    generation += 1;
    terminated = false;
}

export function currentSessionGeneration() {
    return generation;
}

export function terminateSession(expectedGeneration?: number) {
    if (expectedGeneration !== undefined && expectedGeneration !== generation)
        return false;
    if (terminated)
        return false;
    terminated = true;
    generation += 1;
    if (typeof window !== "undefined") window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    return true;
}

export function isSessionActive(expectedGeneration: number) {
    return !terminated && expectedGeneration === generation;
}

export type SessionMutationContext = { sessionGeneration: number };
export const captureSession = (): SessionMutationContext => ({ sessionGeneration: currentSessionGeneration() });
export const isSessionContextActive = (context: unknown) => !!context && typeof context === "object" && "sessionGeneration" in context
    && isSessionActive((context as SessionMutationContext).sessionGeneration);
