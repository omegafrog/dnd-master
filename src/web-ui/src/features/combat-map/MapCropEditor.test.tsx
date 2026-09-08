import '@testing-library/jest-dom/vitest'
import { fireEvent, render, screen } from '@testing-library/react'
import { expect, it, vi } from 'vitest'
import { MapCropEditor } from './MapCropEditor'

it('sets the crop from a drag on the map image', () => {
  const onChange = vi.fn()
  render(<MapCropEditor image="/map.png" crop={{ x: 0, y: 0, width: 200, height: 100 }} onChange={onChange} />)

  const image = screen.getByAltText('자르기 대상 지도')
  Object.defineProperty(image, 'naturalWidth', { value: 200 })
  Object.defineProperty(image, 'naturalHeight', { value: 100 })
  fireEvent.load(image)
  const canvas = screen.getByLabelText('맵 자르기')
  vi.spyOn(canvas, 'getBoundingClientRect').mockReturnValue({ left: 0, top: 0, width: 400, height: 200 } as DOMRect)
  Object.assign(canvas, { setPointerCapture: vi.fn(), releasePointerCapture: vi.fn() })

  fireEvent(canvas, new MouseEvent('pointerdown', { bubbles: true, clientX: 40, clientY: 20 }))
  expect(screen.getByLabelText('확대경')).toBeInTheDocument()
  fireEvent(canvas, new MouseEvent('pointermove', { bubbles: true, clientX: 240, clientY: 160 }))
  expect(screen.getByLabelText('확대경')).toHaveStyle({ left: '220px', top: '40px' })
  fireEvent(canvas, new MouseEvent('pointerup', { bubbles: true, clientX: 240, clientY: 160 }))

  expect(onChange).toHaveBeenLastCalledWith({ x: 20, y: 10, width: 100, height: 70 })
  expect(screen.queryByLabelText('확대경')).not.toBeInTheDocument()
})
