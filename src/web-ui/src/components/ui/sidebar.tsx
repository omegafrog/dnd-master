import type { ButtonHTMLAttributes, PropsWithChildren } from 'react'
import { cn } from './utils'

export function Sidebar({ className, children }: PropsWithChildren<{ className?: string }>) {
  return <aside className={cn('ui-sidebar', className)}>{children}</aside>
}

export function SidebarHeader({ children }: PropsWithChildren) {
  return <div className="ui-sidebar-header">{children}</div>
}

export function SidebarContent({ children }: PropsWithChildren) {
  return <div className="ui-sidebar-content">{children}</div>
}

export function SidebarFooter({ children }: PropsWithChildren) {
  return <div className="ui-sidebar-footer">{children}</div>
}

export function SidebarGroup({ children }: PropsWithChildren) {
  return <div className="ui-sidebar-group">{children}</div>
}

export function SidebarGroupLabel({ children }: PropsWithChildren) {
  return <div className="ui-sidebar-group-label">{children}</div>
}

export function SidebarMenu({ children }: PropsWithChildren) {
  return <nav className="ui-sidebar-menu">{children}</nav>
}

export function SidebarMenuItem({ children }: PropsWithChildren) {
  return <div className="ui-sidebar-menu-item">{children}</div>
}

export function SidebarMenuButton({ className, isActive, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & { isActive?: boolean }) {
  return <button className={cn('ui-sidebar-menu-button', isActive && 'is-active', className)} {...props} />
}
