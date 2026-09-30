import "@testing-library/jest-dom/vitest";
import { configure } from "@testing-library/react";
import { vi } from "vitest";

configure({ asyncUtilTimeout: 5000 });
vi.setConfig({ testTimeout: 15000 });
import { cleanup } from "@testing-library/react";
import { afterEach } from "vitest";

afterEach(cleanup);
