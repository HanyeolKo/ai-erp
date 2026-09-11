import { useRef, useState } from "react";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { api, ApiError, type Project } from "../api/client";
import {
  captureSession,
  currentSessionGeneration,
  isSessionContextActive,
} from "../session";
import {
  clearCreateAttempt,
  getCreateAttempt,
  keys,
  setCreateAttempt,
  useCreateAttempt,
} from "../state";
import { Dialog, Notice } from "../ui";

function isProjectResponse(value: unknown): value is Project {
  return (
    !!value &&
    typeof value === "object" &&
    typeof (value as Project).id === "string"
  );
}
export function ProjectStart({
  accountId: _accountId,
}: {
  accountId?: string;
}) {
  const [open, setOpen] = useState(false);
  const session = String(currentSessionGeneration());
  const attempt = useCreateAttempt(session);
  const [name, setName] = useState(attempt?.name ?? "");
  const [error, setError] = useState("");
  const input = useRef<HTMLInputElement>(null);
  const qc = useQueryClient();
  const create = useMutation({
    mutationFn: async (body: { name: string; requestId: string }) => {
      const value = await api.createProject(body);
      if (
        !isProjectResponse(value) ||
        typeof value.groupId !== "string" ||
        typeof value.name !== "string" ||
        typeof value.role !== "string"
      )
        throw new Error("PROJECT_CREATE_RESULT_UNKNOWN");
      return value;
    },
    onMutate: (vars) => {
      const ctx = captureSession();
      setCreateAttempt(session, {
        name: vars.name,
        requestId: vars.requestId,
        uncertain: false,
        pending: true,
      });
      return ctx;
    },
    onError: (err, vars, ctx) => {
      if (!isSessionContextActive(ctx)) return;
      setCreateAttempt(session, {
        name: vars.name,
        requestId: vars.requestId,
        uncertain: !(
          err instanceof ApiError && [400, 409].includes(err.status)
        ),
        pending: false,
      });
    },
    onSuccess: async (value, vars, ctx) => {
      if (!isSessionContextActive(ctx)) return;
      setCreateAttempt(session, {
        name: vars.name,
        requestId: vars.requestId,
        uncertain: false,
        pending: true,
      });
      await qc.invalidateQueries({ queryKey: keys.projects });
      if (!isSessionContextActive(ctx)) return;
      clearCreateAttempt(session);
      window.location.hash = `#/projects/${value.id}`;
    },
  });
  const submit = (e: React.FormEvent) => {
    e.preventDefault();
    const current = getCreateAttempt(session);
    if (create.isPending || current?.pending || current?.uncertain) return;
    const trimmed = name.trim();
    if (!trimmed || trimmed.length > 200) {
      setError("프로젝트 이름을 1~200자로 입력하세요.");
      input.current?.focus();
      return;
    }
    setError("");
    const requestId =
      current?.name === trimmed ? current.requestId : crypto.randomUUID();
    create.mutate({ name: trimmed, requestId });
  };
  const close = () => setOpen(false);
  return (
    <>
      <div className="action-row">
        <button
          className="button button-primary"
          type="button"
          onClick={() => setOpen(true)}
        >
          새 프로젝트
        </button>
      </div>
      <Dialog
        open={open}
        title="새 프로젝트"
        labelledBy="create-project-title"
        onClose={close}
      >
        <p className="help">프로젝트를 만든 뒤 구성원을 초대할 수 있습니다.</p>
        <form noValidate onSubmit={submit}>
          <label htmlFor="project-name">프로젝트 이름</label>
          <input
            id="project-name"
            ref={input}
            maxLength={200}
            value={name}
            disabled={!!attempt?.pending}
            onChange={(e) => {
              if (!attempt?.uncertain) {
                setName(e.target.value);
                setError("");
              }
            }}
            aria-invalid={!!error}
            aria-describedby={error ? "project-name-error" : undefined}
          />
          {error && (
            <p id="project-name-error" role="alert">
              {error}
            </p>
          )}
          {create.isError && <Notice error={create.error} />}{" "}
          {attempt?.pending && <p role="status">만드는 중…</p>}
          <div className="modal-actions">
            <button
              className="button button-secondary"
              type="button"
              onClick={close}
            >
              취소
            </button>
            <button
              className="button button-primary"
              type="submit"
              disabled={!!attempt?.pending || !!attempt?.uncertain}
            >
              {attempt?.uncertain ? "결과 확인 필요" : "프로젝트 만들기"}
            </button>
          </div>
        </form>
        {attempt?.uncertain && (
          <div className="uncertain-panel">
            <p>
              결과를 확인하지 못했습니다. 같은 요청을 확인한 뒤 새 이름을 입력할
              수 있습니다.
            </p>
            <button
              type="button"
              disabled={!!attempt.pending}
              onClick={() => {
                const a = getCreateAttempt(session);
                if (a) create.mutate({ name: a.name, requestId: a.requestId });
              }}
            >
              같은 요청 결과 확인
            </button>
          </div>
        )}
      </Dialog>
    </>
  );
}
