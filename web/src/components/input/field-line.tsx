import { cn } from "cn"
import { FC, ReactNode } from "react"

/** Label column wide enough for "Current password", for forms that ask for it. */
export const WIDE_LABEL = "w-32 text-sm"

/** A labelled row laid out like ManagedInput and SelectLine, for content that is not a single input. */
export const FieldLine: FC<{ label: string; labelClassName?: string | undefined; children: ReactNode }> = ({
  label,
  labelClassName,
  children,
}) => (
  <div className="flex items-start pb-4">
    <div className={cn("shrink-0 px-2 leading-8 whitespace-nowrap", labelClassName)}>{label}</div>
    <div className="min-w-0 grow">{children}</div>
  </div>
)
