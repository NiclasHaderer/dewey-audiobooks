import { ColumnDef } from "@tanstack/react-table"
import { PencilIcon, Trash2Icon } from "lucide-react"
import { DeweyUserWithPermissions, UserPermissions, UUID } from "@dewey/client"
import { DataTableColumnHeader } from "@dewey/components/data-table/data-table-column-header"
import { DataTableRowActions } from "@dewey/components/data-table/data-table-row-actions"
import { LibraryBadges } from "@dewey/components/library/library-badges"
import { DropdownMenuItem } from "@dewey/components/ui/dropdown-menu"

export type UserRow = DeweyUserWithPermissions<UserPermissions>

interface UserColumnsOptions {
  currentUserId: UUID | undefined
  onEdit: (user: UserRow) => void
  onDelete: (user: UserRow) => void
}

export const userColumns = ({ currentUserId, onEdit, onDelete }: UserColumnsOptions): ColumnDef<UserRow>[] => [
  {
    accessorKey: "username",
    meta: { label: "Username" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Username" />,
    cell: ({ row }) => <span className="font-medium">{row.original.username}</span>,
  },
  {
    id: "role",
    accessorFn: row => (row.permissions.isAdmin ? "Admin" : "User"),
    meta: { label: "Role" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Role" />,
  },
  {
    id: "libraries",
    accessorFn: row => row.permissions.libraries.map(l => `${l.name} (${l.permissions})`).join(", "),
    meta: { label: "Libraries" },
    header: "Libraries",
    enableSorting: false,
    cell: ({ row }) => <LibraryBadges libraries={row.original.permissions.libraries} />,
  },
  {
    id: "actions",
    cell: ({ row }) => (
      <DataTableRowActions className="justify-end">
        <DropdownMenuItem onAction={() => onEdit(row.original)}>
          <PencilIcon className="text-muted-foreground" />
          Edit
        </DropdownMenuItem>
        {currentUserId !== row.original.id && (
          <DropdownMenuItem variant="destructive" onAction={() => onDelete(row.original)}>
            <Trash2Icon />
            Delete
          </DropdownMenuItem>
        )}
      </DataTableRowActions>
    ),
    enableSorting: false,
    enableHiding: false,
  },
]
