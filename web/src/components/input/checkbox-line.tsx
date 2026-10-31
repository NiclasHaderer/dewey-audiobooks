import { FieldLine } from "@dewey/components/input/field-line"
import { Checkbox } from "@dewey/components/ui/checkbox"
import { useField } from "@dewey/hooks/use-form"

export function CheckboxLine({
  name,
  label,
  labelClassName,
}: {
  name: string
  label: string
  labelClassName?: string | undefined
}) {
  const { value, setValue } = useField<Record<string, boolean>, string>(name)

  return (
    <FieldLine label={label} labelClassName={labelClassName}>
      <div className="flex justify-end pt-2 pr-1">
        <Checkbox aria-label={label} isSelected={value} onChange={setValue} />
      </div>
    </FieldLine>
  )
}
