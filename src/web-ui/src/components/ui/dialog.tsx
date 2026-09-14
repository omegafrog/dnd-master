import { useEffect, type PropsWithChildren, type ReactNode } from 'react'
import { cn } from './utils'

export function Dialog({ open, onOpenChange, children }: PropsWithChildren<{ open: boolean; onOpenChange: (open: boolean) => void }>) {
  useEffect(() => {
    if (!open) return
    const onKeyDown = (event: KeyboardEvent) => { if (event.key === 'Escape') onOpenChange(false) }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onOpenChange, open])

  if (!open) return null
  return <div className="ui-dialog-overlay" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onOpenChange(false) }}>{children}</div>
}

export function DialogContent({ className, children }: PropsWithChildren<{ className?: string }>) {
  return <section className={cn('ui-dialog-content', className)} role="dialog" aria-modal="true">{children}</section>
}

export function DialogHeader({ children }: { children: ReactNode }) { return <header className="ui-dialog-header">{children}</header> }
export function DialogTitle({ children }: { children: ReactNode }) { return <h2 className="ui-dialog-title">{children}</h2> }
export function DialogDescription({ children }: { children: ReactNode }) { return <p className="ui-dialog-description">{children}</p> }
export function DialogFooter({ children }: { children: ReactNode }) { return <footer className="ui-dialog-footer">{children}</footer> }
