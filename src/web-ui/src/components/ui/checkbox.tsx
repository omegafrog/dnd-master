import type { ButtonHTMLAttributes } from 'react'
import { Check } from 'lucide-react'
import { cn } from './utils'

type CheckboxProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'onChange'> & {
  checked?: boolean
  onCheckedChange?: (checked: boolean) => void
}

export function Checkbox({ className, checked = false, onCheckedChange, ...props }: CheckboxProps) {
  return (
    <button
      type="button"
      role="checkbox"
      aria-checked={checked}
      className={cn('ui-checkbox', checked && 'ui-checkbox-checked', className)}
      onClick={() => onCheckedChange?.(!checked)}
      {...props}
    >
      {checked ? <Check aria-hidden="true" /> : null}
    </button>
  )
}
