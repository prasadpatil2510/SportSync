import { execFileSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const tracked = execFileSync("git", ["ls-files", "-z"], { cwd: root }).toString().split("\0").filter(Boolean);
const forbiddenNames = [/google-services\.json$/i, /\.jks$/i, /\.keystore$/i, /\.apk$/i, /\.aab$/i, /local\.properties$/i, /testers\.txt$/i, /\.dev\.vars/i];
const secretPatterns = [/-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/, /"private_key"\s*:/, /AIza[0-9A-Za-z_-]{20,}/, /CLOUDFLARE_API_TOKEN\s*=/, /ADMIN_TOKEN\s*=/];
const failures = [];

for (const file of tracked) {
  if (forbiddenNames.some(pattern => pattern.test(file))) { failures.push(`Prohibited tracked file: ${file}`); continue; }
  const full = path.join(root, file);
  let text;
  try { text = fs.readFileSync(full, "utf8"); } catch { continue; }
  if (secretPatterns.some(pattern => pattern.test(text))) failures.push(`Possible secret in: ${file}`);
}

if (failures.length) {
  console.error(`Secret scan failed:\n- ${failures.join("\n- ")}`);
  process.exit(1);
}
console.log(`Secret scan passed: ${tracked.length} tracked files checked`);
