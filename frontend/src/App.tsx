import { useEffect, useState } from "react";
import { fetchSystemInfo } from "./api/systemInfo";
import "./app.css";

type Connection = "loading" | "connected" | "failed";

export default function App() {
  const [connection, setConnection] = useState<Connection>("loading");

  async function checkConnection() {
    setConnection("loading");
    try {
      const info = await fetchSystemInfo();
      setConnection(info.name === "AI ERP" && info.phase === "foundation" ? "connected" : "failed");
    } catch {
      setConnection("failed");
    }
  }

  useEffect(() => {
    void checkConnection();
  }, []);

  return (
    <main>
      <section aria-labelledby="foundation-title">
        <p className="eyebrow">Foundation</p>
        <h1 id="foundation-title">AI ERP 기반 환경</h1>
        {connection === "loading" && <p role="status">AI ERP 연결을 확인하고 있습니다.</p>}
        {connection === "connected" && <p role="status">AI ERP 기반 환경에 연결되었습니다.</p>}
        {connection === "failed" && (
          <div role="alert">
            <p>연결을 확인하지 못했습니다.</p>
            <button type="button" onClick={() => void checkConnection()}>다시 시도</button>
          </div>
        )}
      </section>
    </main>
  );
}
