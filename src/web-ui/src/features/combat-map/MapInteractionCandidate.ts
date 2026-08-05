export type GridCell = { x: number; y: number }

export type MapInteractionCandidate = {
  mapId: string
  mapVersion: number
  tokenId: string
  from: GridCell
  to: GridCell
  action: 'MOVE'
}

export function moveCandidate(mapId: string, mapVersion: number, tokenId: string, from: GridCell, to: GridCell): MapInteractionCandidate {
  return { mapId, mapVersion, tokenId, from, to, action: 'MOVE' }
}
