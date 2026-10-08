export type ImagePoint = { x: number; y: number }
export type MapGridAlignmentDraft = { originX: number; originY: number; cellSize: number }
export type MapView = { zoom: number; panX: number; panY: number }

/** Move an arbitrary sampled intersection to the nearest equivalent finite-grid origin. */
export function finiteGridOrigin(draft: MapGridAlignmentDraft, initialOrigin: ImagePoint,
  grid: { width: number; height: number }, image: { width: number; height: number }): ImagePoint | null {
  const { cellSize } = draft
  if (!Number.isFinite(cellSize) || cellSize <= 0 || grid.width < 1 || grid.height < 1
      || image.width <= 0 || image.height <= 0) return null
  const axis = (sample: number, initial: number, count: number, extent: number) => {
    const span = count * cellSize
    if (span > extent) return null
    const minIndex = Math.ceil((sample + span - extent) / cellSize)
    const maxIndex = Math.floor(sample / cellSize)
    if (minIndex > maxIndex) return null
    const nearest = Math.round((sample - initial) / cellSize)
    const index = Math.max(minIndex, Math.min(maxIndex, nearest))
    const maxOrigin = Math.max(0, extent - span)
    const candidate = sample - index * cellSize
    const origin = Math.max(0, Math.min(candidate, maxOrigin - Number.EPSILON * Math.max(1, extent) * 2))
    return origin + span <= extent ? origin : null
  }
  const x = axis(draft.originX, initialOrigin.x, grid.width, image.width)
  const y = axis(draft.originY, initialOrigin.y, grid.height, image.height)
  return x === null || y === null ? null : { x, y }
}

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
