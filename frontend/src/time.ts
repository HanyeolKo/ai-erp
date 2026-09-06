type Parts = {
    year: number;
    month: number;
    day: number;
    hour: number;
    minute: number;
};
function formatter(zone: string) { return new Intl.DateTimeFormat("en-CA", { timeZone: zone, year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hourCycle: "h23" }); }
function parts(date: Date, zone: string): Parts { const values = formatter(zone).formatToParts(date).reduce<Record<string, string>>((out, item) => ({ ...out, [item.type]: item.value }), {}); return { year: Number(values.year), month: Number(values.month), day: Number(values.day), hour: Number(values.hour), minute: Number(values.minute) }; }
function text(value: Parts) { return `${value.year.toString().padStart(4, "0")}-${value.month.toString().padStart(2, "0")}-${value.day.toString().padStart(2, "0")}T${value.hour.toString().padStart(2, "0")}:${value.minute.toString().padStart(2, "0")}`; }
export function utcToLocalDateTime(instant: string, zone: string) { return text(parts(new Date(instant), zone)); }
export function localDateTimeToUtc(local: string, zone: string) {
    const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})$/.exec(local);
    if (!match)
        throw new Error("날짜와 시간을 입력하세요.");
    if (!validZone(zone))
        throw new Error("지원하지 않는 IANA 시간대입니다.");
    const assumed = Date.UTC(Number(match[1]), Number(match[2]) - 1, Number(match[3]), Number(match[4]), Number(match[5]));
    const offsets = new Set<number>();
    // Observe both sides of a transition; choosing only one iterative solution silently accepts overlaps.
    for (let hours = -36; hours <= 36; hours += 6) {
        const sample = new Date(assumed + hours * 3600000);
        const p = parts(sample, zone);
        offsets.add(Date.UTC(p.year, p.month - 1, p.day, p.hour, p.minute) - sample.getTime());
    }
    const matches = [...offsets].map(offset => new Date(assumed - offset)).filter(candidate => text(parts(candidate, zone)) === local);
    if (!matches.length)
        throw new Error("존재하지 않는 현지 시간입니다.");
    if (matches.length > 1)
        throw new Error("모호한 현지 시간입니다. 중복되지 않는 현지 시간으로 바꾸세요.");
    return matches[0].toISOString();
}
export function validZone(zone: string) { try {
    formatter(zone);
    return zone.length > 0;
}
catch {
    return false;
} }
export function dateInZone(instant: string, zone: string) { return utcToLocalDateTime(instant, zone).slice(0, 10); }
export function civilDateBoundary(date: string, zone: string) {
    const center = Date.parse(date + "T00:00:00Z");
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !Number.isFinite(center) || new Date(center).toISOString().slice(0, 10) !== date)
        throw new Error("올바른 날짜를 입력하세요.");
    if (!validZone(zone))
        throw new Error("지원하지 않는 IANA 시간대입니다.");
    const localDate = (instant: number) => dateInZone(new Date(instant).toISOString(), zone);
    let lower = center - 48 * 3600000;
    let upper = center + 48 * 3600000;
    // Civil dates remain ordered through a midnight clock gap or overlap. Find
    // the first instant belonging to this date, rather than resolving 00:00.
    while (upper - lower > 1) {
        const middle = Math.floor((lower + upper) / 2);
        if (localDate(middle) < date)
            lower = middle;
        else
            upper = middle;
    }
    if (localDate(upper) !== date)
        throw new Error("이 시간대에 존재하지 않는 날짜입니다.");
    return new Date(upper).toISOString();
}
export function displayTime(instant: string, zone = "Asia/Seoul") { return utcToLocalDateTime(instant, zone).replace("T", " "); }
export function shiftDate(date: string, days: number) { const value = new Date(date + "T12:00:00Z"); value.setUTCDate(value.getUTCDate() + days); return value.toISOString().slice(0, 10); }
export function navigateDate(date: string, view: "month" | "week", direction: number) {
    if (view === "week")
        return shiftDate(date, direction * 7);
    const value = new Date(date + "T12:00:00Z");
    value.setUTCDate(1);
    value.setUTCMonth(value.getUTCMonth() + direction);
    return value.toISOString().slice(0, 10);
}
export function viewWindow(anchor: string | Date, view: "month" | "week", zone = "Asia/Seoul") {
    const date = typeof anchor === "string" ? anchor : dateInZone(anchor.toISOString(), zone);
    const initial = view === "month" ? date.slice(0, 7) + "-01" : date;
    const weekday = new Date(initial + "T12:00:00Z").getUTCDay();
    const start = shiftDate(initial, -((weekday + 6) % 7));
    const count = view === "month" ? 42 : 7;
    const days = Array.from({ length: count }, (_, i) => shiftDate(start, i));
    return { from: civilDateBoundary(start, zone), to: civilDateBoundary(shiftDate(start, count), zone), days };
}
