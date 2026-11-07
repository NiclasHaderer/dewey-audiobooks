import { cn } from "cn"
import { ComponentProps, FC, KeyboardEvent, memo, ReactNode, RefObject, useEffect, useRef, useState } from "react"
import { InputError } from "@dewey/components/input/input-error"
import { InputGroup, InputGroupAddon, InputGroupInput } from "@dewey/components/ui/input-group"

export type LabeledInputProps = Omit<Omit<ComponentProps<"input">, "defaultValue">, "value"> & {
  label?: string | undefined
  leftIcon?: ReactNode | undefined
  rightIcon?: ReactNode | undefined
  labelClassName?: string | undefined
  defaultValue?: string | number | ReadonlyArray<string> | undefined | null
  value?: string | ReadonlyArray<string> | number | undefined | null
  wrapperClassName?: string | undefined
  groupClassName?: string | undefined
  touched?: boolean | undefined
  errors?: string[] | undefined
  hideError?: boolean | undefined
  inputRef?: RefObject<HTMLInputElement | null> | undefined
  preventSubmit?: boolean
  onValue?: (value: string) => void
  onEnter?: (event: KeyboardEvent<HTMLInputElement>) => void
}

export const LabeledInput: FC<LabeledInputProps> = memo(
  ({
    leftIcon,
    rightIcon,
    label,
    wrapperClassName,
    groupClassName,
    placeholder = label,
    labelClassName,
    defaultValue,
    className,
    preventSubmit = false,
    value,
    onValue,
    onEnter,
    onKeyDown,
    onChange,
    touched,
    errors,
    hideError = false,
    inputRef,
    ...props
  }) => {
    const ref = useRef<HTMLInputElement | null>(null)
    const [cursor, setCursor] = useState<number | null>(null)

    useEffect(() => {
      const input = ref.current
      if (input && cursor) input.setSelectionRange(cursor, cursor)
    }, [ref, cursor, value])

    return (
      <div className={hideError ? undefined : "pb-4"}>
        <label className={`flex items-center ${wrapperClassName ?? ""}`}>
          {label ? <div className={cn("shrink-0 px-2 whitespace-nowrap", labelClassName)}>{label}</div> : null}
          <div className="grow">
            <InputGroup className={groupClassName}>
              {leftIcon ? <InputGroupAddon>{leftIcon}</InputGroupAddon> : null}
              <InputGroupInput
                onKeyDown={event => {
                  if (event.key === "Enter") {
                    if (preventSubmit) event.preventDefault()
                    if (onEnter) onEnter(event)
                  }
                  if (onKeyDown) onKeyDown(event)
                }}
                onChange={event => {
                  if (onValue) onValue(event.target.value)
                  if (onChange) onChange(event)
                  setCursor(event.target.selectionStart)
                }}
                placeholder={placeholder}
                defaultValue={defaultValue ?? undefined}
                value={value ?? undefined}
                {...props}
                ref={instance => {
                  if (inputRef) inputRef.current = instance
                  ref.current = instance
                }}
                className={cn("placeholder:text-[0.9em]", className)}
              />
              {rightIcon ? <InputGroupAddon align="inline-end">{rightIcon}</InputGroupAddon> : null}
            </InputGroup>
          </div>
        </label>
        {hideError ? null : <InputError errors={errors} show={touched} />}
      </div>
    )
  }
)
LabeledInput.displayName = "LabeledInput"
