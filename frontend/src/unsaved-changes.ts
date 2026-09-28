import { useCallback, useEffect, useRef } from "react";

const discardMessage = "저장하지 않은 변경사항이 있습니다. 이동하면 입력 내용이 사라질 수 있습니다. 계속하시겠습니까?";

/** Protect an editor draft while it is dirty. Filters and view changes should not use this hook. */
export function useUnsavedChanges(dirty: boolean): { confirmDiscard: () => boolean; markSaved: () => void } {
  const dirtyRef = useRef(dirty);
  const routeRef = useRef(`${window.location.pathname}${window.location.search}${window.location.hash}`);
  const restoringRef = useRef(false);
  const markSaved = useCallback(() => { dirtyRef.current = false; }, []);
  useEffect(() => { dirtyRef.current = dirty; }, [dirty]);
  const confirmDiscard = useCallback(() => {
    if (!dirtyRef.current) return true;
    const accepted = window.confirm(discardMessage);
    if (accepted) dirtyRef.current = false;
    return accepted;
  }, []);
  useEffect(() => {
    const currentRoute = () => `${window.location.pathname}${window.location.search}${window.location.hash}`;
    const routeBase = (route: string) => {
      const hash = route.indexOf("#");
      const prefix = hash < 0 ? route : route.slice(0, hash);
      const fragment = hash < 0 ? "" : route.slice(hash + 1);
      return `${prefix}${fragment.split("?", 1)[0]}`;
    };
    const onRouteChange = () => {
      const next = currentRoute();
      if (restoringRef.current) { restoringRef.current = false; routeRef.current = next; return; }
      if (next === routeRef.current) return;
      // Filter and view query changes do not leave an editor; remember them so a
      // later real departure restores the correct in-editor URL.
      if (routeBase(next) === routeBase(routeRef.current)) { routeRef.current = next; return; }
      if (!dirtyRef.current || window.confirm(discardMessage)) { dirtyRef.current = false; routeRef.current = next; return; }
      restoringRef.current = true;
      window.history.replaceState(null, "", routeRef.current);
      window.dispatchEvent(new HashChangeEvent("hashchange"));
    };
    const onBeforeUnload = (event: BeforeUnloadEvent) => {
      if (!dirtyRef.current) return;
      event.preventDefault();
      event.returnValue = discardMessage;
    };
    window.addEventListener("hashchange", onRouteChange);
    window.addEventListener("popstate", onRouteChange);
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => {
      window.removeEventListener("hashchange", onRouteChange);
      window.removeEventListener("popstate", onRouteChange);
      window.removeEventListener("beforeunload", onBeforeUnload);
    };
  }, []);
  return { confirmDiscard, markSaved };
}

