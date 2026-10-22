#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { access, chmod, copyFile, mkdtemp, mkdir, readFile, rename, rm } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const version = 'v1.154.0';
const archiveName = `vmutils-linux-amd64-${version}.tar.gz`;
const archiveSha256 = '0c6b01fba8ca3149a017ad7713ae65cd2222ba6a64f1e1f8787692cbb5d953c9';
const root = resolve(fileURLToPath(new URL('..', import.meta.url)));
const target = join(root, '.codex/tools/vmagent', version, 'vmagent');
const run = (command, args) => {
  const result = spawnSync(command, args, { encoding: 'utf8', stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} failed with exit code ${result.status}`);
};

if (process.platform !== 'linux' || process.arch !== 'x64') throw new Error('vmagent installer supports Linux x86_64 only');
await mkdir(join(root, '.codex/tools/vmagent', version), { recursive: true });
try {
  await access(target);
  process.exit(0);
} catch {}

const temp = await mkdtemp(join(tmpdir(), 'vmagent-install-'));
try {
  const archive = join(temp, archiveName);
  run('curl', ['-fsSL', `https://github.com/VictoriaMetrics/VictoriaMetrics/releases/download/${version}/${archiveName}`, '-o', archive]);
  const digest = createHash('sha256').update(await readFile(archive)).digest('hex');
  if (digest !== archiveSha256) throw new Error(`vmagent archive checksum mismatch: expected ${archiveSha256}, got ${digest}`);
  run('tar', ['-xzf', archive, '-C', temp, 'vmagent-prod']);
  const extracted = join(temp, 'vmagent-prod');
  const staged = `${target}.tmp-${process.pid}`;
  await copyFile(extracted, staged);
  await chmod(staged, 0o755);
  await rename(staged, target);
  console.log(`Installed vmagent ${version} at ${target}`);
} finally {
  await rm(temp, { recursive: true, force: true });
}
