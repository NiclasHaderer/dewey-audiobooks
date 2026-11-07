import { UUID } from "crypto"
import { UserIcon } from "lucide-react"
import { FC } from "react"
import { CheckboxLine } from "@dewey/components/input/checkbox-line"
import { FieldLine } from "@dewey/components/input/field-line"
import { ManagedInput } from "@dewey/components/input/managed-input"
import { LibraryAccess, LibraryGrant } from "@dewey/components/library/library-access"
import { TitledDialog } from "@dewey/components/titled-dialog"
import { Button } from "@dewey/components/ui/button"
import { DialogFooter } from "@dewey/components/ui/dialog"
import { Form, FormContext } from "@dewey/hooks/use-form"
import { useLibraries } from "@dewey/queries/library-queries"

export interface UserFormValues {
  id?: UUID
  username: string
  admin: boolean
  libraries: LibraryGrant[]
}

export const UserDialog: FC<{
  isOpen: boolean
  setIsOpen: (open: boolean) => void
  form: FormContext<UserFormValues>
  onSubmit: (user: UserFormValues) => void
}> = ({ isOpen, setIsOpen, onSubmit, form }) => {
  const { data: libraries } = useLibraries()

  return (
    <TitledDialog isOpen={isOpen} onOpenChange={setIsOpen} title="Edit user" className="sm:max-w-lg">
      <Form form={form} onSubmit={(user: UserFormValues) => onSubmit(user)}>
        <ManagedInput name="username" label="Name" labelClassName="w-28" placeholder="Name" leftIcon={<UserIcon />} />
        <CheckboxLine name="admin" label="Admin rights" labelClassName="w-28" />
        <FieldLine label="Libraries" labelClassName="w-28">
          {form.fields.admin ? (
            <p className="text-muted-foreground text-sm leading-8">Admins can read and write every library.</p>
          ) : (
            <LibraryAccess
              libraries={libraries ?? []}
              value={form.fields.libraries}
              onChange={grants => form.setFields({ libraries: grants })}
            />
          )}
        </FieldLine>
        <DialogFooter>
          <Button type="button" variant="secondary" onPress={() => setIsOpen(false)}>
            Cancel
          </Button>
          <Button type="submit">Save</Button>
        </DialogFooter>
      </Form>
    </TitledDialog>
  )
}
