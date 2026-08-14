import type { ComponentPropsWithoutRef } from 'react'
import { cn } from './utils'

type ProgressProps = ComponentPropsWithoutRef<'div'> & {
  value?: number
  max?: number
}

export function Progress({ className, value = 0, max = 100, ...props }: ProgressProps) {
  const percentage = max > 0 ? Math.min(100, Math.max(0, (value / max) * 100)) : 0

  return (
    <div
      {...props}
      role="progressbar"
      aria-valuemin={0}
      aria-valuemax={max}
      aria-valuenow={value}
      className={cn('ui-progress', className)}
    >
      <div className="ui-progress-indicator" style={{ transform: `translateX(-${100 - percentage}%)` }} />
    </div>
  )
}
