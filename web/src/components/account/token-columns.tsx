import { ColumnDef } from "@tanstack/react-table"
import { EyeIcon, PencilIcon, Trash2Icon } from "lucide-react"
import { AccessToken, LibraryPermissions } from "@dewey/client"
import { DataTableColumnHeader } from "@dewey/components/data-table/data-table-column-header"
import { DataTableRowActions } from "@dewey/components/data-table/data-table-row-actions"
import { LibraryBadges } from "@dewey/components/library/library-badges"
import { DropdownMenuItem } from "@dewey/components/ui/dropdown-menu"

interface TokenColumnsOptions {
  libraries: LibraryPermissions[]
  onEdit: (token: AccessToken) => void
  onShow: (token: AccessToken) => void
  onRevoke: (token: AccessToken) => void
}

const day = (iso: string | null) => (iso ? new Date(iso).toLocaleDateString() : "")

// What the server lets a token reach: all of the user's libraries without a list, never above the user's level
const reachable = (token: AccessToken, libraries: LibraryPermissions[]): LibraryPermissions[] => {
  const granted = token.permissions.libraries
  if (granted === null) return libraries
  return libraries.flatMap(library => {
    const grant = granted.find(it => it.id === library.id)
    if (!grant) return []
    const permissions = grant.permissions === "READ_WRITE" ? library.permissions : "READONLY"
    return [{ ...library, permissions }]
  })
}

export const tokenColumns = ({
  libraries,
  onEdit,
  onShow,
  onRevoke,
}: TokenColumnsOptions): ColumnDef<AccessToken>[] => [
  {
    accessorKey: "name",
    meta: { label: "Name" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Name" />,
    cell: ({ row }) => <span className="font-medium">{row.original.name}</span>,
  },
  {
    id: "role",
    accessorFn: row => (row.permissions.isAdmin ? "Admin" : "User"),
    meta: { label: "Role" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Role" />,
  },
  {
    id: "libraries",
    accessorFn: row =>
      reachable(row, libraries)
        .map(library => library.name)
        .join(", "),
    meta: { label: "Libraries" },
    header: "Libraries",
    enableSorting: false,
    cell: ({ row }) => <LibraryBadges libraries={reachable(row.original, libraries)} />,
  },
  {
    accessorKey: "createdAt",
    meta: { label: "Created" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Created" />,
    cell: ({ row }) => day(row.original.createdAt),
  },
  {
    accessorKey: "lastUsedAt",
    meta: { label: "Last used" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Last used" />,
    cell: ({ row }) => day(row.original.lastUsedAt) || <span className="text-muted-foreground">Never</span>,
  },
  {
    accessorKey: "expiresAt",
    meta: { label: "Expires" },
    header: ({ column }) => <DataTableColumnHeader column={column} title="Expires" />,
    cell: ({ row }) => day(row.original.expiresAt) || <span className="text-muted-foreground">Never</span>,
  },
  {
    id: "actions",
    cell: ({ row }) => (
      <DataTableRowActions className="justify-end">
        <DropdownMenuItem onAction={() => onEdit(row.original)}>
          <PencilIcon className="text-muted-foreground" />
          Edit
        </DropdownMenuItem>
        <DropdownMenuItem onAction={() => onShow(row.original)}>
          <EyeIcon className="text-muted-foreground" />
          Show token
        </DropdownMenuItem>
        <DropdownMenuItem variant="destructive" onAction={() => onRevoke(row.original)}>
          <Trash2Icon />
          Revoke
        </DropdownMenuItem>
      </DataTableRowActions>
    ),
    enableSorting: false,
    enableHiding: false,
  },
]
