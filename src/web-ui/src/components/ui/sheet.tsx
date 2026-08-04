import type { PropsWithChildren, ReactNode } from 'react'
import { cn } from './utils'

export function Sheet({ open, onOpenChange, children }: PropsWithChildren<{ open: boolean; onOpenChange: (open: boolean) => void }>) {
  if (!open) return null
  return <div className="ui-sheet-overlay" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onOpenChange(false) }}>{children}</div>
}

export function SheetContent({ className, children }: PropsWithChildren<{ className?: string }>) {
  return <section className={cn('ui-sheet-content', className)} role="dialog" aria-modal="true">{children}</section>
}

export function SheetHeader({ children }: { children: ReactNode }) { return <div className="ui-sheet-header">{children}</div> }
export function SheetTitle({ children }: { children: ReactNode }) { return <h2 className="ui-sheet-title">{children}</h2> }
