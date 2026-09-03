import SwaggerParser from "@apidevtools/swagger-parser";
import { access } from "node:fs/promises";
import { fileURLToPath } from "node:url";

const specPath = fileURLToPath(new URL("../backend/build/api-spec/openapi3.yaml", import.meta.url));
await access(specPath);
const specification = await SwaggerParser.validate(specPath);
const systemInfo = specification.paths?.["/api/v1/system/info"]?.get?.responses?.["200"]?.content?.["application/json"]?.schema;

if (specification.openapi !== "3.0.1" || !systemInfo?.properties?.name || !systemInfo?.properties?.phase) {
  throw new Error("OpenAPI 명세에 /api/v1/system/info의 200 JSON 계약이 없습니다.");
}

console.log("OpenAPI 3.0.1 시스템 정보 계약을 확인했습니다.");
