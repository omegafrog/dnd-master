import type { HTMLAttributes } from 'react'
import { cn } from './utils'

export function Progress({ value = 0, className, ...props }: HTMLAttributes<HTMLDivElement> & { value?: number }) {
  const percent = Math.max(0, Math.min(100, value))

  return (
    <div
      {...props}
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={percent}
      className={cn('ui-progress', className)}
    >
      <div className="ui-progress-indicator" style={{ transform: `translateX(-${100 - percent}%)` }} />
    </div>
  )
}
