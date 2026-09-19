import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const webRoot = resolve(scriptDirectory, '..')
const manifestPath = resolve(webRoot, 'public/assets/token-visuals.manifest.json')
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
const requiredIds = ['token-frame', 'player', 'friendly-npc', 'neutral-npc', 'enemy', 'boss', 'trap', 'object']

if (manifest.schemaVersion !== 1 || !Array.isArray(manifest.assets)) throw new Error('토큰 자산 manifest 형식이 올바르지 않습니다.')
const ids = new Set()
for (const asset of manifest.assets) {
  if (!asset || ids.has(asset.id)) throw new Error('토큰 자산 id가 없거나 중복되었습니다.')
  ids.add(asset.id)
  for (const field of ['id', 'path', 'source', 'author', 'license', 'originalUrl']) {
    if (typeof asset[field] !== 'string' || asset[field].trim() === '') throw new Error(`토큰 자산 ${asset.id ?? '(없음)'}에 ${field}가 없습니다.`)
  }
  if (asset.license !== 'CC0-1.0' || asset.redistributable !== true) throw new Error(`토큰 자산 ${asset.id}는 재배포 가능한 CC0 기록이 필요합니다.`)
  if (!asset.path.startsWith('/assets/') || /^https?:\/\//.test(asset.path)) throw new Error(`토큰 자산 ${asset.id}는 저장소 경로여야 합니다.`)
  if (!/^https?:\/\//.test(asset.originalUrl)) throw new Error(`토큰 자산 ${asset.id}의 원본 주소가 올바르지 않습니다.`)
  if (!existsSync(resolve(webRoot, 'public', asset.path.slice(1)))) throw new Error(`토큰 자산 파일을 찾을 수 없습니다: ${asset.path}`)
}
for (const id of requiredIds) if (!ids.has(id)) throw new Error(`필수 토큰 자산이 manifest에 없습니다: ${id}`)

console.log(`토큰 자산 ${manifest.assets.length}개와 라이선스 기록을 확인했습니다.`)
