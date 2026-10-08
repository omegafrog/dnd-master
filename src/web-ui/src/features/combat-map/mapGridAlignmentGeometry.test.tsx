import { describe, expect, it } from 'vitest'
import { finiteGridOrigin, imagePointFromScreen, refineCellSize, screenPointFromImage } from './mapGridAlignmentGeometry'

describe('격자 정렬 계산', () => {
  it('converts through the screen without changing the original-image coordinate', () => {
    const image = { x: 148.25, y: 91.5 }
    const view = { zoom: 2.4, panX: 30, panY: -18 }

    expect(imagePointFromScreen(screenPointFromImage(image, view), view)).toEqual(image)
  })

  it('keeps the reference point while a distant intersection changes cell size', () => {
    const next = refineCellSize({ originX: 10, originY: 20, cellSize: 20 }, { x: 0, y: 0 }, { x: 10, y: 20 }, { x: 4, y: 0 }, { x: 110, y: 20 })

    expect(next).toEqual({ originX: 10, originY: 20, cellSize: 25 })
  })

  it('rejects an identical intersection and a non-positive size', () => {
    expect(() => refineCellSize({ originX: 0, originY: 0, cellSize: 10 }, { x: 0, y: 0 }, { x: 0, y: 0 }, { x: 0, y: 0 }, { x: 0, y: 0 })).toThrow()
  })

  it('keeps the measured grid phase while moving an interior sample to the nearest origin that fits the saved map', () => {
    const sample = { originX: 500.5, originY: 211.5, cellSize: 43.14355 }
    const origin = finiteGridOrigin(sample, { x: 459.53678, y: 170.26671 }, { width: 28, height: 19 }, { width: 1683, height: 1190 })

    expect(origin).not.toBeNull()
    expect(origin!.x + 28 * sample.cellSize).toBeLessThanOrEqual(1683)
    expect(origin!.y + 19 * sample.cellSize).toBeLessThanOrEqual(1190)
    expect((sample.originX - origin!.x) / sample.cellSize).toBeCloseTo(1, 8)
    expect((sample.originY - origin!.y) / sample.cellSize).toBeCloseTo(1, 8)
  })

  it('rejects a sample when no phase-equivalent finite grid can fit the source image', () => {
    expect(finiteGridOrigin({ originX: 10, originY: 10, cellSize: 120 }, { x: 0, y: 0 },
      { width: 10, height: 10 }, { width: 1000, height: 1000 })).toBeNull()
  })

  it('keeps the saved grid rectangle strictly inside an image at the floating-point edge', () => {
    const origin = finiteGridOrigin({ originX: 0.6, originY: 0.6, cellSize: 0.1 }, { x: 0.6, y: 0.6 },
      { width: 3, height: 3 }, { width: 1, height: 1 })

    expect(origin).not.toBeNull()
    expect(origin!.x + 3 * 0.1).toBeLessThanOrEqual(1)
    expect(origin!.y + 3 * 0.1).toBeLessThanOrEqual(1)
  })
})
