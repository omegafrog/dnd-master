import type { ButtonHTMLAttributes } from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from './utils'

const buttonVariants = cva('ui-button', {
  variants: {
    variant: {
      default: 'ui-button-default',
      outline: 'ui-button-outline',
      ghost: 'ui-button-ghost',
      destructive: 'ui-button-destructive',
    },
    size: { default: 'ui-button-default-size', icon: 'ui-button-icon-size' },
  },
  defaultVariants: { variant: 'default', size: 'default' },
})

export function Button({ className, variant, size, ...props }: ButtonHTMLAttributes<HTMLButtonElement> & VariantProps<typeof buttonVariants>) {
  return <button className={cn(buttonVariants({ variant, size }), className)} {...props} />
}
