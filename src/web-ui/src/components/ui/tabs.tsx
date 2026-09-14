import type { ButtonHTMLAttributes, ReactNode } from 'react'
import { cn } from './utils'

export function Tabs({ value, onValueChange, children, className }: { value: string; onValueChange?: (value: string) => void; children: ReactNode; className?: string }) {
  return <div className={cn('ui-tabs', className)} data-value={value} onChange={event => onValueChange?.((event.target as HTMLInputElement).value)}>{children}</div>
}

export function TabsList({ className, children }: { className?: string; children: ReactNode }) {
  return <div role="tablist" className={cn('ui-tabs-list', className)}>{children}</div>
}

export function TabsTrigger({ value, active, className, children, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { value: string; active?: boolean }) {
  return <button type="button" role="tab" aria-selected={active} data-value={value} className={cn('ui-tabs-trigger', active && 'ui-tabs-trigger-active', className)} {...props}>{children}</button>
}

export function TabsContent({ value, active, className, children }: { value: string; active?: boolean; className?: string; children: ReactNode }) {
  if (!active) return null
  return <div role="tabpanel" data-tab={value} className={cn('ui-tabs-content', className)}>{children}</div>
}
