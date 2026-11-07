import { useMemo, useState } from "react"
import { toast } from "sonner"
import { useSession } from "@dewey/client"
import { DataTable } from "@dewey/components/data-table/data-table"
import { DataTableToolbar } from "@dewey/components/data-table/data-table-toolbar"
import { UserRow, userColumns } from "@dewey/components/user-columns"
import { UserDialog, UserFormValues } from "@dewey/components/user-dialog"
import { useForm } from "@dewey/hooks/use-form.tsx"
import { useDeleteUser, useUpdateUser, useUsers } from "@dewey/queries/user-queries"

export const UserManager = () => {
  const loggedInUserId = useSession(s => s.accessToken?.payload.sub)
  const [isOpen, setIsOpen] = useState(false)
  const { data: users } = useUsers()
  const updateUserMutation = useUpdateUser()
  const deleteUserMutation = useDeleteUser()

  const updateUser = (user: UserFormValues) => {
    updateUserMutation.mutate(
      {
        id: user.id!,
        username: user.username,
        permissions: {
          isAdmin: user.admin,
          libraries: user.libraries,
        },
      },
      {
        onSuccess: () => {
          setIsOpen(false)
          toast.success("User updated")
        },
      }
    )
  }

  const form = useForm<UserFormValues>(
    {
      username: "",
      admin: false,
      libraries: [],
      id: undefined,
    },
    { validate: { username: (username: string) => username.length >= 3 || "Username must be at least 3 characters" } }
  )

  const openEdit = (user: UserRow) => {
    form.setAllFields({
      id: user.id,
      username: user.username,
      admin: user.permissions.isAdmin,
      libraries: user.permissions.libraries.map(({ id, permissions }) => ({ id, permissions })),
    })
    setIsOpen(true)
  }

  const deleteUser = (user: UserRow) => deleteUserMutation.mutate(user.id)

  const columns = useMemo(
    () => userColumns({ currentUserId: loggedInUserId, onEdit: openEdit, onDelete: deleteUser }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [loggedInUserId]
  )

  return (
    <>
      <div className="flex min-h-0 flex-1 flex-col">
        <DataTable
          columns={columns}
          data={users ?? []}
          onRowClick={openEdit}
          emptyState="No users yet"
          toolbar={table => (
            <DataTableToolbar table={table} searchColumnId="username" searchPlaceholder="Filter users..." />
          )}
        />
      </div>
      <UserDialog isOpen={isOpen} form={form} setIsOpen={setIsOpen} onSubmit={updateUser} />
    </>
  )
}
