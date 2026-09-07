import { useRef, useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api, ApiError, type CreationOptions, type Project } from "../api/client";
import { accessKey, clearAccessDenial, clearCreationUncertainty, isAccessError, keys, recordAccessDenial, recordCreationUncertainty, useAccessDenied, useCreationUncertainty } from "../state";
import { captureSession, isSessionContextActive } from "../session";
import { go, Notice, QueryState, RetryButton } from "../ui";

function AdministratorRequest({ accountId, group }: { accountId?: string; group?: CreationOptions[number] }) {
    const [copied, setCopied] = useState<"idle" | "done" | "failed">("idle");
    const request = `AI ERP 프로젝트 시작을 요청합니다.\n계정: ${accountId ?? "로그인 계정"}\n${group ? `그룹: ${group.name} (${group.id})\n프로젝트 생성 또는 그룹 역할 확인을 부탁드립니다. 그룹 소유자/관리자는 프로젝트를 생성할 수 있습니다.` : "참여할 그룹과 소유자 설정을 확인하거나 프로젝트 초대 링크를 보내 주세요."}`;
    return <div><label>관리자 요청문<textarea readOnly value={request} onFocus={event => event.currentTarget.select()}/></label>
        <button type="button" onClick={async () => {
            try { await navigator.clipboard.writeText(request); setCopied("done"); }
            catch { setCopied("failed"); }
        }}>요청문 복사</button>
        <p className="help">요청문을 복사해 관리자에게 전달하세요. 이 화면에서 자동으로 전송하지 않습니다.</p>
        {copied === "done" && <p role="status">요청문을 복사했습니다.</p>}
        {copied === "failed" && <p role="status">자동 복사를 사용할 수 없습니다. 위 요청문을 선택해 직접 복사하세요.</p>}
    </div>;
}

class UnknownCreationResult extends Error {
    constructor() {
        super("PROJECT_CREATE_RESULT_UNKNOWN");
    }
}

function isProjectResponse(value: unknown): value is Project {
    return !!value && typeof value === "object" && typeof (value as Project).id === "string" && typeof (value as Project).groupId === "string"
        && typeof (value as Project).name === "string" && typeof (value as Project).role === "string";
}

export function ProjectStart({ accountId }: { accountId?: string }) {
    const qc = useQueryClient();
    const [page, setPage] = useState(0);
    const options = useQuery({ queryKey: ["project-creation-options", page], queryFn: () => api.creationOptions(page) });
    const [groupId, setGroupId] = useState("");
    const [name, setName] = useState("");
    const [nameError, setNameError] = useState("");
    const [refreshError, setRefreshError] = useState<unknown>();
    const input = useRef<HTMLInputElement>(null);
    const create = useMutation({
        mutationFn: async (body: Parameters<typeof api.createProject>[0]) => {
            const value = await api.createProject(body);
            if (!isProjectResponse(value)) throw new UnknownCreationResult();
            return value;
        },
        onMutate: captureSession,
        onError: (error, variables, context) => {
            if (!isSessionContextActive(context)) return;
            if (isAccessError(error)) recordAccessDenial(accessKey("creation", variables.groupId));
            else if (error instanceof UnknownCreationResult || !(error instanceof ApiError) || error.status >= 500)
                recordCreationUncertainty(accessKey("creation", variables.groupId));
        },
        onSuccess: async (value, _variables, context) => {
        if (!isSessionContextActive(context)) return;
        await Promise.all([
            qc.invalidateQueries({ queryKey: keys.projects }),
            qc.invalidateQueries({ queryKey: [...keys.projects, "lookup", value.id] }),
            qc.invalidateQueries({ queryKey: keys.dashboard(value.id) })
        ]);
        if (!isSessionContextActive(context)) return;
        go(`/projects/${value.id}`);
    } });
    const rows = (options.data ?? []) as CreationOptions;
    const group = rows.find(row => row.id === groupId) ?? rows.find(row => row.canCreate) ?? rows[0];
    const creationStateKey = accessKey("creation", group?.id ?? "");
    const creationDenied = useAccessDenied(creationStateKey);
    const creationUnknown = useCreationUncertainty(creationStateKey);
    const selectedCreateError = create.variables?.groupId === group?.id ? create.error : undefined;
    const denied = creationDenied || isAccessError(selectedCreateError);
    const uncertainFailure = creationUnknown || (selectedCreateError instanceof ApiError ? selectedCreateError.status >= 500 : !!selectedCreateError && !isAccessError(selectedCreateError));
    const shouldDisableCreate = create.isPending || denied || uncertainFailure;
    const serverNameErrors = selectedCreateError instanceof ApiError ? selectedCreateError.problem.fieldErrors?.filter(error => error.field === "name") : undefined;
    const serverGroupErrors = selectedCreateError instanceof ApiError ? selectedCreateError.problem.fieldErrors?.filter(error => error.field === "groupId") : undefined;
    const refresh = async () => {
        setRefreshError(undefined);
        try {
            const result = await options.refetch();
            if (result.isSuccess && group) {
                clearAccessDenial(accessKey("creation", group.id));
                create.reset();
            }
        }
        catch (error) { setRefreshError(error); }
    };
    const refreshProjects = async () => {
        const context = captureSession();
        setRefreshError(undefined);
        try {
            await qc.cancelQueries({ queryKey: keys.projects });
            if (!isSessionContextActive(context)) return;
            const latest = await api.projects(0);
            if (!isSessionContextActive(context)) return;
            qc.setQueryData(keys.projects, latest);
            if (!isSessionContextActive(context)) return;
            if (group) clearCreationUncertainty(creationStateKey);
            create.reset();
        }
        catch (error) { if (isSessionContextActive(context)) setRefreshError(error); }
    };
    const setGroup = (value: string) => {
        setGroupId(value);
        create.reset();
        setNameError("");
    };
    return <div className="recovery-panel"><h2>새 프로젝트 만들기</h2>
        <p>새 프로젝트를 만드시겠어요? 그룹 소유자 또는 관리자는 프로젝트를 만들 수 있습니다.</p>
        <QueryState query={options} label="생성 권한 다시 확인" loadingMessage="프로젝트 생성 권한을 확인하고 있습니다." errorMessage="생성 권한을 확인하지 못했습니다."/>
        {options.isSuccess && <>
            {!rows.length ? page ? <p>이 페이지에 그룹이 없습니다. 이전 그룹 페이지를 확인하세요.</p> : <><p>참여 중인 그룹이 없습니다. 관리자에게 그룹 설정 또는 초대를 요청하세요.</p><AdministratorRequest accountId={accountId}/></> : <>
                <label>그룹<select value={group?.id ?? ""} disabled={create.isPending} onChange={event => setGroup(event.target.value)} aria-invalid={!!serverGroupErrors?.length} aria-describedby={serverGroupErrors?.length ? "project-group-error" : undefined}>{rows.map(row => <option key={row.id} value={row.id}>{row.name}</option>)}</select></label>
                {serverGroupErrors?.length ? <p id="project-group-error">{serverGroupErrors.map(error => error.message).join(" ")}</p> : null}
                {group?.canCreate ? <form noValidate onSubmit={event => {
                    event.preventDefault();
                    if (shouldDisableCreate || options.isFetching) return;
                    const trimmed = name.trim();
                    if (!trimmed || trimmed.length > 200) { setNameError("프로젝트 이름을 1~200자로 입력하세요."); input.current?.focus(); return; }
                    setNameError(""); create.mutate({ groupId: group.id, name: trimmed });
                }}>
                    <p id="project-create-help">프로젝트를 만들면 이 프로젝트의 관리자가 됩니다.</p>
                    <label>프로젝트 이름<input ref={input} required maxLength={200} value={name} disabled={create.isPending} onChange={event => { setName(event.target.value); setNameError(""); }} aria-invalid={!!nameError || !!serverNameErrors?.length} aria-describedby={`project-create-help${nameError || serverNameErrors?.length ? " project-name-error" : ""}`}/></label>
                    {(nameError || !!serverNameErrors?.length) && <p id="project-name-error" role={nameError ? "alert" : undefined}>{nameError || serverNameErrors?.map(error => error.message).join(" ")}</p>}
                    <button disabled={shouldDisableCreate || options.isFetching}>프로젝트 만들기</button>
                </form> : <><p>{group?.reason === "GROUP_ROLE_NOT_CONFIGURED" ? "그룹 역할이 아직 설정되지 않아 생성 권한을 확인할 수 없습니다." : "그룹 소유자 또는 관리자만 프로젝트를 만들 수 있습니다."}</p><AdministratorRequest key={group?.id} accountId={accountId} group={group}/></>}
            </>}
        </>}
        {(page > 0 || rows.length >= 100) && <div className="toolbar"><button disabled={!page || options.isFetching || create.isPending} onClick={() => { setPage(page - 1); setGroupId(""); create.reset(); setNameError(""); }}>이전 그룹</button><button disabled={!options.isSuccess || rows.length < 100 || page >= 10000 || options.isFetching || create.isPending} onClick={() => { setPage(page + 1); setGroupId(""); create.reset(); setNameError(""); }}>다음 그룹</button></div>}
        {create.isPending && <p role="status">프로젝트를 만드는 중입니다.</p>}
        {refreshError !== undefined && <Notice error={refreshError} message="최신 상태를 확인하지 못했습니다. 다시 시도하세요."/>}
        {(denied && !create.isError) && <><p>생성 권한을 다시 확인해야 합니다.</p><RetryButton onRetry={refresh} isFetching={options.isFetching}>생성 권한 다시 확인</RetryButton><AdministratorRequest accountId={accountId} group={group}/></>}
        {(uncertainFailure && !selectedCreateError) && <><p>생성 결과를 확인하지 못했습니다. 현재 목록과 다른 프로젝트 목록 페이지도 확인한 뒤 다시 만들기 전에 프로젝트 목록을 새로고침해 생성 여부를 확인하세요.</p><RetryButton onRetry={refreshProjects} isFetching={options.isFetching}>프로젝트 목록 새로고침</RetryButton></>}
        {selectedCreateError && <><Notice error={selectedCreateError}/>{denied ? <><RetryButton onRetry={refresh} isFetching={options.isFetching}>생성 권한 다시 확인</RetryButton><AdministratorRequest accountId={accountId} group={group}/></> : uncertainFailure ? <><p>생성 결과를 확인하지 못했습니다. 현재 목록과 다른 프로젝트 목록 페이지도 확인한 뒤 다시 만들기 전에 프로젝트 목록을 새로고침해 생성 여부를 확인하세요.</p><RetryButton onRetry={refreshProjects} isFetching={options.isFetching}>프로젝트 목록 새로고침</RetryButton></> : null}</>}
    </div>;
}
