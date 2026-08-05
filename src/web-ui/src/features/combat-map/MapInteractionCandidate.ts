export type GridCell = { x: number; y: number }

export type MapInteractionCandidate = {
  mapId: string
  mapVersion: number
  tokenId: string
  from?: GridCell
  to?: GridCell
  action: 'MOVE' | 'INTERACT' | 'TARGET' | 'LOCATION'
  targetId?: string
  location?: GridCell
}

export function actionCandidate(mapId: string, mapVersion: number, tokenId: string, action: MapInteractionCandidate['action'], location?: GridCell, targetId?: string): MapInteractionCandidate {
  return { mapId, mapVersion, tokenId, action, location, targetId }
}

export function moveCandidate(mapId: string, mapVersion: number, tokenId: string, from: GridCell, to: GridCell): MapInteractionCandidate {
  return { mapId, mapVersion, tokenId, from, to, action: 'MOVE' }
}
