import type { HTMLAttributes } from 'react'
import { cn } from './utils'

export function Progress({ value, className, ...props }: HTMLAttributes<HTMLDivElement> & { value?: number | null }) {
  const determinate = value != null
  const percent = determinate ? Math.max(0, Math.min(100, value)) : 0

  return (
    <div
      {...props}
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={determinate ? percent : undefined}
      data-state={determinate ? 'determinate' : 'indeterminate'}
      className={cn('ui-progress', !determinate && 'ui-progress-indeterminate', className)}
    >
      <div className="ui-progress-indicator" style={{ transform: `translateX(-${100 - percent}%)` }} />
    </div>
  )
}
