import { execSync } from "node:child_process";
import { existsSync, mkdirSync } from "node:fs";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const scriptDir = dirname(fileURLToPath(import.meta.url));
const rootDir = dirname(scriptDir);
const outputFile = `${rootDir}/src/generated/api.ts`;
const openApiUrl =
  process.env.OPENAPI_URL ?? "http://localhost:8080/v3/api-docs";

if (!existsSync(dirname(outputFile))) {
  mkdirSync(dirname(outputFile), { recursive: true });
}

try {
  execSync(`npx openapi-typescript ${openApiUrl} -o ${outputFile}`, {
    cwd: rootDir,
    stdio: "pipe",
  });
  console.log(`Generated API types from ${openApiUrl}`);
} catch (error) {
  if (existsSync(outputFile)) {
    console.warn(
      `OpenAPI source unavailable at ${openApiUrl}. Using existing generated types at ${outputFile}.`,
    );
    process.exit(0);
  }
  console.error(
    `Failed to generate API types and no existing generated file found at ${outputFile}.`,
  );
  throw error;
}
