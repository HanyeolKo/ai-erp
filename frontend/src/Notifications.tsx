import { useState } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { api } from "./api/client";
import { keys } from "./state";
import { captureSession, isSessionContextActive } from "./session";
import { internalLink, Link, Notice, QueryState } from "./ui";

const notificationLabels: Record<string, string> = {
  SCHEDULE_CREATED: "일정 생성",
  SCHEDULE_UPDATED: "일정 변경",
  SCHEDULE_CONFIRMED: "일정 확정",
  SCHEDULE_CANCELLED: "일정 취소",
  SCHEDULE_ACKNOWLEDGED: "참여자 확인",
  CALENDAR_SYNCED: "Calendar 동기화 완료",
  CALENDAR_SYNC_FAILED: "Calendar 동기화 실패",
  INVITATION_CREATED: "초대 생성",
  INVITATION_ACCEPTED: "초대 수락",
  INVITATION_REJECTED: "초대 거절",
};

function dateLabel(value: string | null | undefined) {
  return value
    ? new Intl.DateTimeFormat("ko-KR", {
        dateStyle: "medium",
        timeStyle: "short",
      }).format(new Date(value))
    : "-";
}

export function NotificationContent({
  onNavigate,
  compact = false,
}: {
  onNavigate?: () => void;
  compact?: boolean;
}) {
  const [page, setPage] = useState(0);
  const queryClient = useQueryClient();
  const list = useQuery({
    queryKey: [...keys.notifications, page],
    queryFn: () => api.notifications(page),
  });
  const read = useMutation({
    mutationFn: api.readNotification,
    onMutate: captureSession,
    onSuccess: (_, __, context) => {
      if (isSessionContextActive(context)) {
        return queryClient.invalidateQueries({ queryKey: keys.notifications });
      }
      return undefined;
    },
  });
  return (
    <div className={compact ? "notification-content notification-content--compact" : "notification-content"}>
      {!compact && <p className="eyebrow">모든 프로젝트</p>}
      {!compact && <h1>알림</h1>}
      <QueryState query={list} />
      {list.isSuccess &&
        (list.data.length ? (
          <ul className="notification-list">
            {list.data.map((notification) => {
              const href = internalLink(notification.link);
              return (
                <li key={notification.id}>
                  <div className="notification-main">
                    <strong>{notificationLabels[notification.type] ?? "프로젝트 알림"}</strong>
                    <time dateTime={notification.createdAt}>{dateLabel(notification.createdAt)}</time>
                  </div>
                  <div className="notification-actions">
                    {href && (
                      <Link to={href} onClick={onNavigate}>
                        {notification.link?.includes("/schedules/")
                          ? "관련 일정 보기"
                          : "관련 프로젝트 보기"}
                      </Link>
                    )}
                    {notification.readAt ? (
                      <span className="notification-read">읽음</span>
                    ) : (
                      <button
                        type="button"
                        disabled={read.isPending}
                        onClick={() => !read.isPending && read.mutate(notification.id)}
                      >
                        읽음 처리
                      </button>
                    )}
                  </div>
                </li>
              );
            })}
          </ul>
        ) : (
          <div className="empty-state">
            <h2>새 알림이 없습니다</h2>
            <p>새로운 프로젝트 활동이 생기면 여기에 표시됩니다.</p>
          </div>
        ))}
      {read.isError && <Notice error={read.error} />}
      <div className="pagination">
        <button type="button" disabled={!page || list.isFetching} onClick={() => setPage(page - 1)}>
          이전
        </button>
        <span>{page + 1} 페이지</span>
        <button
          type="button"
          disabled={!list.isSuccess || list.data.length < 100 || list.isFetching}
          onClick={() => setPage(page + 1)}
        >
          다음
        </button>
      </div>
    </div>
  );
}
