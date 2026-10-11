export function screenPointToGrid(clientX: number, clientY: number, rect: Pick<DOMRect, 'left' | 'top' | 'width' | 'height'>,
  width: number, height: number) {
  return {
    x: Math.max(0, Math.min(width, ((clientX - rect.left) / rect.width) * width)),
    y: Math.max(0, Math.min(height, ((clientY - rect.top) / rect.height) * height)),
  }
}
