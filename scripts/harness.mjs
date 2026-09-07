import { spawnSync } from "node:child_process";
import path from "node:path";

const root = path.resolve(import.meta.dirname, "..");
const full = process.argv.includes("--full");
const commands = [
  ["node", ["scripts/verify-specs.mjs"], root],
  ["node", ["scripts/secret-scan.mjs"], root],
  ["pnpm", ["test"], path.join(root, "worker")],
  ["pnpm", ["run", "check"], path.join(root, "worker")]
];
if (full) commands.push(["gradle", ["assembleStagingDebug", "--stacktrace"], path.join(root, "android")]);

for (const [command, args, cwd] of commands) {
  console.log(`\n> ${command} ${args.join(" ")}`);
  const result = spawnSync(command, args, { cwd, stdio: "inherit", shell: process.platform === "win32" });
  if (result.status !== 0) process.exit(result.status || 1);
}
console.log(`\n${full ? "Release" : "Quick"} harness passed.`);
