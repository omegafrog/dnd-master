import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const scriptDirectory = dirname(fileURLToPath(import.meta.url))
const webRoot = resolve(scriptDirectory, '..')
const manifestPath = resolve(webRoot, 'public/assets/token-visuals.manifest.json')
const tokensDirectory = resolve(webRoot, 'public/assets/tokens')
const licensesPath = resolve(tokensDirectory, 'LICENSES.md')
const catalogPath = resolve(webRoot, 'src/features/combat-map/TokenVisualCatalog.ts')
const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
const catalogSource = readFileSync(catalogPath, 'utf8')
if (!existsSync(licensesPath)) throw new Error('번들 토큰 자산 라이선스 고지 파일이 없습니다: public/assets/tokens/LICENSES.md')
const licensesNotice = readFileSync(licensesPath, 'utf8')
const requiredIds = ['token-frame', 'player', 'friendly-npc', 'neutral-npc', 'enemy', 'boss', 'trap', 'object']

if (manifest.schemaVersion !== 1 || !Array.isArray(manifest.assets)) throw new Error('토큰 자산 manifest 형식이 올바르지 않습니다.')
const ids = new Set()
const manifestPaths = new Set()
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
  manifestPaths.add(asset.path)
  const attribution = `| ${asset.id} | ${asset.path} | ${asset.source} | ${asset.author} | ${asset.license} | ${asset.originalUrl} |`
  if (!licensesNotice.includes(attribution)) throw new Error(`토큰 자산 ${asset.id}의 라이선스 고지가 manifest와 일치하지 않습니다.`)
}
for (const id of requiredIds) if (!ids.has(id)) throw new Error(`필수 토큰 자산이 manifest에 없습니다: ${id}`)

for (const fileName of readdirSync(tokensDirectory).filter(value => value.endsWith('.svg'))) {
  const assetPath = `/assets/tokens/${fileName}`
  if (!manifestPaths.has(assetPath)) throw new Error(`번들 토큰 자산이 manifest에 없습니다: ${assetPath}`)
}

const catalogAssetPaths = new Set([...catalogSource.matchAll(/['\"](\/assets\/tokens\/[^'\"]+)['\"]/g)].map(match => match[1]))
for (const path of catalogAssetPaths) {
  if (!manifest.assets.some(asset => asset.path === path)) throw new Error(`TokenVisualCatalog 자산이 manifest에 없습니다: ${path}`)
}

console.log(`토큰 자산 ${manifest.assets.length}개와 라이선스 기록을 확인했습니다.`)
