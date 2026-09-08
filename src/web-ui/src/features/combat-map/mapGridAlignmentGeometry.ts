export type ImagePoint = { x: number; y: number }
export type MapGridAlignmentDraft = { originX: number; originY: number; cellSize: number }
export type MapView = { zoom: number; panX: number; panY: number }

export function screenPointFromImage(point: ImagePoint, view: MapView): ImagePoint {
  return { x: point.x * view.zoom + view.panX, y: point.y * view.zoom + view.panY }
}

export function imagePointFromScreen(point: ImagePoint, view: MapView): ImagePoint {
  return { x: (point.x - view.panX) / view.zoom, y: (point.y - view.panY) / view.zoom }
}

export function refineCellSize(_draft: MapGridAlignmentDraft, referenceGrid: ImagePoint, referenceImage: ImagePoint, targetGrid: ImagePoint, targetImage: ImagePoint): MapGridAlignmentDraft {
  const gridDelta = { x: targetGrid.x - referenceGrid.x, y: targetGrid.y - referenceGrid.y }
  const divisor = gridDelta.x * gridDelta.x + gridDelta.y * gridDelta.y
  if (!Number.isFinite(divisor) || divisor === 0) throw new Error('서로 다른 격자 교차점을 선택해야 합니다.')
  const imageDelta = { x: targetImage.x - referenceImage.x, y: targetImage.y - referenceImage.y }
  const cellSize = (imageDelta.x * gridDelta.x + imageDelta.y * gridDelta.y) / divisor
  if (!Number.isFinite(cellSize) || cellSize <= 0) throw new Error('한 칸 크기는 0보다 커야 합니다.')
  return {
    originX: referenceImage.x - referenceGrid.x * cellSize,
    originY: referenceImage.y - referenceGrid.y * cellSize,
    cellSize,
  }
}
