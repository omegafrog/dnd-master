import { expect, it } from 'vitest'
import { screenPointToGrid } from './mapScreenToGrid'

it('converts client coordinates through the displayed map rectangle at any zoom', () => {
  expect(screenPointToGrid(250, 180, { left: 50, top: 30, width: 400, height: 300 }, 20, 15))
    .toEqual({ x: 10, y: 7.5 })
})
