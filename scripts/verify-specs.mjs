import fs from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const registry = JSON.parse(fs.readFileSync(path.join(root, "specs/registry.json"), "utf8"));
const allowed = new Set(["draft", "approved", "implemented", "verified", "superseded"]);
const ids = new Set();
const failures = [];

for (const spec of registry.specs || []) {
  if (!/^[A-Z]+-\d{3}$/.test(spec.id || "")) failures.push(`Invalid specification ID: ${spec.id}`);
  if (ids.has(spec.id)) failures.push(`Duplicate specification ID: ${spec.id}`);
  ids.add(spec.id);
  if (!allowed.has(spec.status)) failures.push(`${spec.id}: invalid status ${spec.status}`);
  const specPath = path.join(root, spec.path || "");
  if (!fs.existsSync(specPath)) { failures.push(`${spec.id}: missing ${spec.path}`); continue; }
  const text = fs.readFileSync(specPath, "utf8");
  if (!text.includes(`# ${spec.id} —`)) failures.push(`${spec.id}: heading does not contain its ID`);
  if (!/Status:\s*(draft|approved|implemented|verified|superseded)/.test(text)) failures.push(`${spec.id}: missing valid Status`);
  if (!text.includes("## Acceptance criteria") || !/- \[[ x]\]/.test(text)) failures.push(`${spec.id}: missing checklist acceptance criteria`);
  for (const implementation of spec.implementation || []) {
    if (!fs.existsSync(path.join(root, implementation))) failures.push(`${spec.id}: missing implementation path ${implementation}`);
  }
}

if (failures.length) {
  console.error(`Specification verification failed:\n- ${failures.join("\n- ")}`);
  process.exit(1);
}
console.log(`Specifications verified: ${ids.size}`);
