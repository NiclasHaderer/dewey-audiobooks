import { cn } from "cn"
import { EyeIcon, IdCardIcon, LibraryIcon, LockIcon, PencilIcon, ShieldCheckIcon, Trash2Icon } from "lucide-react"
import { FC, ReactNode, useEffect, useMemo, useRef } from "react"
import { toast } from "sonner"
import { DeweyUserWithPermissions, UserPermissions } from "@dewey/client"
import { WIDE_LABEL } from "@dewey/components/input/field-line"
import { ManagedInput } from "@dewey/components/input/managed-input.tsx"
import { usePasswordToggle } from "@dewey/components/input/password-toggle"
import { Link } from "@dewey/components/link.tsx"
import { Avatar, AvatarFallback } from "@dewey/components/ui/avatar"
import { Badge } from "@dewey/components/ui/badge"
import { Button } from "@dewey/components/ui/button"
import {
  Dialog,
  DialogClose,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from "@dewey/components/ui/dialog"
import { Form, useForm } from "@dewey/hooks/use-form.tsx"
import { useSetUsername } from "@dewey/queries/current-user-queries"
import { useDeleteUser, useUpdatePassword, useUpdateUsername } from "@dewey/queries/user-queries"
import { logout } from "@dewey/state/user-data"

export const User: FC<{ user: DeweyUserWithPermissions<UserPermissions> }> = ({ user }) => {
  const setUsername = useSetUsername()
  const updateUsername = useUpdateUsername()
  const updatePassword = useUpdatePassword()
  const deleteUser = useDeleteUser()

  const deleteAccount = () =>
    deleteUser.mutate(user.id, {
      onSuccess: async () => {
        await logout()
        window.location.replace("/login")
      },
    })

  const usernameInitial = useMemo(() => ({ username: user.username }), [user.username])
  const usernameForm = useForm(usernameInitial, {
    validate: {
      username: value => value.length >= 3 || "Username must be at least 3 characters",
    },
    reloadOnInitialChange: true,
  })

  const newPasswordRef = useRef("")
  const passwordForm = useForm(
    {
      currentPassword: "",
      newPassword: "",
      confirmPassword: "",
    },
    {
      validate: {
        currentPassword: value => value.length > 0 || "Current password is required",
        newPassword: value => value.length >= 6 || "Password must be at least 6 characters",
        confirmPassword: value => {
          if (value.length === 0) return "Please confirm your password"
          return value === newPasswordRef.current || "Passwords do not match"
        },
      },
    }
  )
  useEffect(() => {
    newPasswordRef.current = passwordForm.fields.newPassword
  }, [passwordForm.fields.newPassword])

  const password = usePasswordToggle()

  return (
    <div className="mx-auto flex w-full max-w-3xl flex-col gap-4">
      <div className="bg-card flex flex-col gap-4 rounded-xl p-4">
        <div className="flex items-center gap-4">
          <Avatar size="lg" className="size-14">
            <AvatarFallback className="text-lg font-medium">{user.username.charAt(0).toUpperCase()}</AvatarFallback>
          </Avatar>
          <div className="flex min-w-0 items-center gap-2">
            <span className="truncate text-lg font-semibold">{user.username}</span>
            {user.permissions.isAdmin && (
              <Badge variant="secondary">
                <ShieldCheckIcon />
                Admin
              </Badge>
            )}
          </div>
        </div>
        <div className="flex flex-col gap-0.5">
          <h3 className="text-sm font-medium">Library access</h3>
          {user.permissions.isAdmin && (
            <p className="text-muted-foreground text-sm">As an admin you can read and write every library.</p>
          )}
        </div>
        {user.permissions.libraries.length === 0 ? (
          <p className="text-muted-foreground text-sm">
            {user.permissions.isAdmin ? (
              <>
                There are no libraries yet.{" "}
                <Link href="/settings/libraries" className="text-foreground underline">
                  Create one under Libraries
                </Link>
                .
              </>
            ) : (
              "You don't have access to any library yet. Ask an admin."
            )}
          </p>
        ) : (
          <div className="flex flex-col">
            {user.permissions.libraries.map(library => (
              <div key={library.id} className="flex items-center justify-between gap-3 py-2 first:pt-0 last:pb-0">
                <span className="flex min-w-0 items-center gap-2 text-sm">
                  <LibraryIcon className="text-muted-foreground size-4 shrink-0" />
                  <span className="truncate">{library.name}</span>
                </span>
                <span className="text-muted-foreground flex shrink-0 items-center gap-1.5 text-sm">
                  {library.permissions === "READ_WRITE" ? (
                    <PencilIcon className="size-3.5" />
                  ) : (
                    <EyeIcon className="size-3.5" />
                  )}
                  {library.permissions === "READ_WRITE" ? "Read & write" : "Read only"}
                </span>
              </div>
            ))}
          </div>
        )}
      </div>

      <Form
        form={usernameForm}
        onSubmit={values =>
          updateUsername.mutate(
            { id: user.id, username: values.username },
            {
              onSuccess: () => {
                setUsername(values.username)
                toast.success("Username updated")
              },
            }
          )
        }
      >
        <Section
          title="Username"
          description="The name you log in with."
          actions={
            <>
              <Button onPress={() => usernameForm.restoreInitial()} variant="secondary" className="w-full sm:w-auto">
                Cancel
              </Button>
              <Button type="submit" className="w-full sm:w-auto">
                Save
              </Button>
            </>
          }
        >
          <ManagedInput name="username" label="Username" labelClassName={WIDE_LABEL} leftIcon={<IdCardIcon />} />
        </Section>
      </Form>

      <Form
        form={passwordForm}
        onSubmit={values =>
          updatePassword.mutate(
            { id: user.id, currentPassword: values.currentPassword, newPassword: values.newPassword },
            {
              onSuccess: () => {
                passwordForm.restoreInitial()
                toast.success("Password updated")
              },
            }
          )
        }
      >
        <Section
          title="Password"
          description="Changing it signs you out everywhere and revokes all of your access tokens."
          actions={
            <>
              <Button onPress={() => passwordForm.restoreInitial()} variant="secondary" className="w-full sm:w-auto">
                Cancel
              </Button>
              <Button type="submit" className="w-full sm:w-auto">
                Update password
              </Button>
            </>
          }
        >
          <ManagedInput
            name="currentPassword"
            label="Current password"
            labelClassName={WIDE_LABEL}
            type={password.type}
            leftIcon={<LockIcon />}
            autoComplete="current-password"
            rightIcon={password.toggle}
          />
          <ManagedInput
            name="newPassword"
            label="New password"
            labelClassName={WIDE_LABEL}
            type={password.type}
            leftIcon={<LockIcon />}
            autoComplete="new-password"
            rightIcon={password.toggle}
          />
          <ManagedInput
            name="confirmPassword"
            label="Confirm password"
            labelClassName={WIDE_LABEL}
            type={password.type}
            leftIcon={<LockIcon />}
            autoComplete="new-password"
            rightIcon={password.toggle}
          />
        </Section>
      </Form>

      <Section
        title="Delete account"
        description="Permanently delete your account and all associated data. This cannot be undone."
        destructive
        actions={
          <DialogTrigger>
            <Button variant="destructive" className="w-full sm:w-auto">
              <Trash2Icon />
              Delete account
            </Button>
            <Dialog showCloseButton={false}>
              <DialogHeader>
                <DialogTitle>Delete your account?</DialogTitle>
                <DialogDescription>This permanently deletes your account and cannot be undone.</DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <DialogClose>Cancel</DialogClose>
                <Button variant="destructive" onPress={deleteAccount}>
                  Delete account
                </Button>
              </DialogFooter>
            </Dialog>
          </DialogTrigger>
        }
      />
    </div>
  )
}

const Section: FC<{
  title: string
  description?: string
  destructive?: boolean
  actions?: ReactNode
  children?: ReactNode
}> = ({ title, description, destructive = false, actions, children }) => (
  <section
    className={cn("flex flex-col gap-4 rounded-xl p-4", destructive ? "border-destructive/30 border" : "bg-card")}
  >
    <div className="flex flex-col gap-0.5">
      <h3 className={cn("text-sm font-medium", destructive && "text-destructive")}>{title}</h3>
      {description && <p className="text-muted-foreground text-sm">{description}</p>}
    </div>
    {children && <div>{children}</div>}
    {actions && <div className="flex flex-col-reverse gap-2 sm:flex-row sm:justify-end">{actions}</div>}
  </section>
)
