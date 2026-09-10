import { describe, expect, it } from 'vitest'
import { imagePointFromScreen, refineCellSize, screenPointFromImage } from './mapGridAlignmentGeometry'

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
})
