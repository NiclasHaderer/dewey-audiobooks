import { CalendarClockIcon, CopyIcon, LibraryIcon, LockIcon, PlusIcon, TagIcon } from "lucide-react"
import { FC, useMemo, useState } from "react"
import { toast } from "sonner"
import { AccessToken, DeweyUserWithPermissions, UserPermissions } from "@dewey/client"
import { tokenColumns } from "@dewey/components/account/token-columns"
import { DataTable } from "@dewey/components/data-table/data-table"
import { DataTableToolbar } from "@dewey/components/data-table/data-table-toolbar"
import { CheckboxLine } from "@dewey/components/input/checkbox-line"
import { FieldLine, WIDE_LABEL } from "@dewey/components/input/field-line"
import { ManagedInput } from "@dewey/components/input/managed-input"
import { usePasswordToggle } from "@dewey/components/input/password-toggle"
import { SelectLine } from "@dewey/components/input/select-line"
import { LibraryAccess, LibraryGrant } from "@dewey/components/library/library-access"
import { TitledDialog } from "@dewey/components/titled-dialog"
import { Button } from "@dewey/components/ui/button"
import { DialogClose, DialogDescription, DialogFooter } from "@dewey/components/ui/dialog"
import { Form, useForm } from "@dewey/hooks/use-form"
import {
  useAccessTokens,
  useCreateAccessToken,
  useRevealAccessToken,
  useRevokeAccessToken,
  useUpdateAccessToken,
} from "@dewey/queries/access-token-queries"

type User = DeweyUserWithPermissions<UserPermissions>

const EXPIRIES = [
  { value: 0, label: "Never" },
  { value: 30, label: "In 30 days" },
  { value: 90, label: "In 90 days" },
  { value: 365, label: "In a year" },
]

export const AccessTokens: FC<{ user: User }> = ({ user }) => {
  const { data: tokens } = useAccessTokens()
  const [editing, setEditing] = useState<AccessToken | "new">()
  const [showing, setShowing] = useState<AccessToken>()
  const [revoking, setRevoking] = useState<AccessToken>()

  const columns = useMemo(
    () =>
      tokenColumns({
        libraries: user.permissions.libraries,
        onEdit: setEditing,
        onShow: setShowing,
        onRevoke: setRevoking,
      }),
    [user.permissions.libraries]
  )

  return (
    <>
      <div className="flex min-h-0 flex-1 flex-col">
        <DataTable
          columns={columns}
          data={tokens ?? []}
          onRowClick={setEditing}
          emptyState="No access tokens yet"
          toolbar={table => (
            <DataTableToolbar table={table} searchColumnId="name" searchPlaceholder="Filter tokens...">
              <Button size="sm" className="h-8" aria-label="Create token" onPress={() => setEditing("new")}>
                <PlusIcon />
                <span className="hidden sm:inline">Create token</span>
              </Button>
            </DataTableToolbar>
          )}
        />
      </div>
      {editing && (
        <TokenDialog
          user={user}
          token={editing === "new" ? undefined : editing}
          onClose={() => setEditing(undefined)}
        />
      )}
      {showing && <ShowTokenDialog token={showing} onClose={() => setShowing(undefined)} />}
      {revoking && <RevokeTokenDialog token={revoking} onClose={() => setRevoking(undefined)} />}
    </>
  )
}

const requirePassword = (password: string) => password.length > 0 || "Password is required"

const PasswordField: FC = () => {
  const password = usePasswordToggle()
  return (
    <ManagedInput
      name="password"
      label="Current password"
      labelClassName={WIDE_LABEL}
      type={password.type}
      autoComplete="current-password"
      placeholder="Password"
      leftIcon={<LockIcon />}
      rightIcon={password.toggle}
    />
  )
}

const TokenSecret: FC<{ secret: string }> = ({ secret }) => {
  const copy = () =>
    navigator.clipboard.writeText(secret).then(
      () => toast.success("Copied the token"),
      () => toast.error("Could not copy, select the token and copy it by hand")
    )

  return (
    <>
      <div className="bg-muted flex min-w-0 items-center gap-2 rounded-lg p-1 pl-3">
        <code className="min-w-0 grow truncate font-mono text-xs select-all">{secret}</code>
        <Button variant="ghost" size="icon" aria-label="Copy token" onPress={copy}>
          <CopyIcon />
        </Button>
      </div>
      <DialogFooter className="mt-4">
        <DialogClose>Done</DialogClose>
      </DialogFooter>
    </>
  )
}

const ShowTokenDialog: FC<{ token: AccessToken; onClose: () => void }> = ({ token, onClose }) => {
  const reveal = useRevealAccessToken()
  const form = useForm({ password: "" }, { validate: { password: requirePassword } })
  const [secret, setSecret] = useState<string>()

  const submit = ({ password }: { password: string }) =>
    reveal.mutate({ tokenId: token.id, password }, { onSuccess: revealed => setSecret(revealed.token) })

  return (
    <TitledDialog
      isOpen
      onOpenChange={open => !open && onClose()}
      title={`Show token "${token.name}"`}
      className="sm:max-w-lg"
    >
      {secret ? (
        <TokenSecret secret={secret} />
      ) : (
        <Form form={form} onSubmit={submit}>
          <DialogDescription className="mb-4">Enter your password to see this token again.</DialogDescription>
          <PasswordField />
          <DialogFooter>
            <DialogClose>Cancel</DialogClose>
            <Button type="submit" isDisabled={reveal.isPending}>
              Show token
            </Button>
          </DialogFooter>
        </Form>
      )}
    </TitledDialog>
  )
}

const RevokeTokenDialog: FC<{ token: AccessToken; onClose: () => void }> = ({ token, onClose }) => {
  const revoke = useRevokeAccessToken()
  const form = useForm({ password: "" }, { validate: { password: requirePassword } })

  const submit = ({ password }: { password: string }) =>
    revoke.mutate(
      { tokenId: token.id, password },
      {
        onSuccess: () => {
          toast.success(`Revoked ${token.name}`)
          onClose()
        },
      }
    )

  return (
    <TitledDialog
      isOpen
      onOpenChange={open => !open && onClose()}
      title={`Revoke ${token.name}?`}
      className="sm:max-w-lg"
    >
      <Form form={form} onSubmit={submit}>
        <DialogDescription className="mb-4">Scripts and clients using this token will stop working.</DialogDescription>
        <PasswordField />
        <DialogFooter>
          <DialogClose>Cancel</DialogClose>
          <Button type="submit" variant="destructive" isDisabled={revoke.isPending}>
            Revoke token
          </Button>
        </DialogFooter>
      </Form>
    </TitledDialog>
  )
}

// Creates a token, or edits one. The expiry is signed into the token, so editing leaves it out.
const TokenDialog: FC<{ user: User; token?: AccessToken; onClose: () => void }> = ({ user, token, onClose }) => {
  const create = useCreateAccessToken()
  const update = useUpdateAccessToken()
  const form = useForm(
    {
      name: token?.name ?? "",
      expiryDays: 0,
      isAdmin: token?.permissions.isAdmin ?? false,
      everyLibrary: !token || token.permissions.libraries === null,
      grants:
        token?.permissions.libraries ??
        user.permissions.libraries.map(({ id, permissions }): LibraryGrant => ({ id, permissions })),
      password: "",
    },
    {
      validate: {
        name: (name: string) => name.trim().length > 0 || "Name is required",
        password: requirePassword,
      },
    }
  )
  const { expiryDays, isAdmin, everyLibrary, grants } = form.fields
  const [secret, setSecret] = useState<string>()

  const submit = ({ name, password }: typeof form.fields) => {
    const permissions = { isAdmin, libraries: everyLibrary ? null : grants }
    if (token) {
      update.mutate(
        { tokenId: token.id, update: { name: name.trim(), permissions, password } },
        {
          onSuccess: () => {
            toast.success(`Updated ${name.trim()}`)
            onClose()
          },
        }
      )
      return
    }
    const expiresAt = expiryDays ? new Date(Date.now() + expiryDays * 86_400_000).toISOString() : null
    create.mutate(
      { name: name.trim(), expiresAt, permissions, password },
      { onSuccess: created => setSecret(created.token) }
    )
  }

  return (
    <TitledDialog
      isOpen
      onOpenChange={open => !open && onClose()}
      title={secret ? "Token created" : token ? `Edit token "${token.name}"` : "Create access token"}
      className="sm:max-w-lg"
    >
      {secret ? (
        <TokenSecret secret={secret} />
      ) : (
        <Form form={form} onSubmit={submit}>
          <ManagedInput
            name="name"
            label="Name"
            labelClassName={WIDE_LABEL}
            placeholder="dewey-cli on my laptop"
            leftIcon={<TagIcon />}
          />
          <PasswordField />
          {!token && (
            <SelectLine
              name="expiryDays"
              label="Expires"
              labelClassName={WIDE_LABEL}
              icon={<CalendarClockIcon />}
              title="Expires"
              options={EXPIRIES}
            />
          )}
          {user.permissions.isAdmin && <CheckboxLine name="isAdmin" label="Admin rights" labelClassName={WIDE_LABEL} />}
          <SelectLine
            name="everyLibrary"
            label="Libraries"
            labelClassName={WIDE_LABEL}
            icon={<LibraryIcon />}
            title="Libraries"
            options={[
              { value: true, label: "All of mine" },
              { value: false, label: "Selected" },
            ]}
          />
          {!everyLibrary && (
            <FieldLine label="" labelClassName={WIDE_LABEL}>
              <LibraryAccess
                // A level above the user's own would be cut back by the server anyway
                libraries={user.permissions.libraries.map(it => ({ ...it, max: it.permissions }))}
                value={grants}
                onChange={granted => form.setFields({ grants: granted })}
              />
            </FieldLine>
          )}
          <DialogFooter>
            <DialogClose>Cancel</DialogClose>
            <Button type="submit" isDisabled={create.isPending || update.isPending}>
              {token ? "Save" : "Create token"}
            </Button>
          </DialogFooter>
        </Form>
      )}
    </TitledDialog>
  )
}
