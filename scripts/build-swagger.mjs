import { cp, mkdir, writeFile } from "node:fs/promises";

const output = new URL("../backend/build/api-docs/", import.meta.url);
const assets = new URL("../node_modules/swagger-ui-dist/", import.meta.url);
const spec = new URL("../backend/build/api-spec/openapi3.yaml", import.meta.url);

await mkdir(output, { recursive: true });
await cp(assets, new URL("swagger-ui-dist/", output), { recursive: true, dereference: true });
await cp(spec, new URL("openapi3.yaml", output));
const page = `<!doctype html>
<html lang="ko"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>AI ERP API 검토</title><link rel="stylesheet" href="./swagger-ui-dist/swagger-ui.css"></head>
<body><div id="swagger-ui"></div><script src="./swagger-ui-dist/swagger-ui-bundle.js"></script>
<script src="./swagger-ui-dist/swagger-ui-standalone-preset.js"></script><script>
SwaggerUIBundle({ url: "./openapi3.yaml", dom_id: "#swagger-ui", presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset], layout: "StandaloneLayout", supportedSubmitMethods: [] });
</script></body></html>`;
await writeFile(new URL("index.html", output), page, "utf8");
