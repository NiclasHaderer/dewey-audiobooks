import { EyeIcon, PencilIcon } from "lucide-react"
import { FC } from "react"
import { LibraryPermissionLevel, UUID } from "@dewey/client"
import { Badge } from "@dewey/components/ui/badge"

const MAX = 3

const levelLabel = (level: LibraryPermissionLevel) => (level === "READ_WRITE" ? "Read & write" : "Read only")

export const LibraryBadges: FC<{ libraries: { id: UUID; name: string; permissions: LibraryPermissionLevel }[] }> = ({
  libraries,
}) => {
  if (libraries.length === 0) return <span className="text-muted-foreground">None</span>
  const shown = libraries.slice(0, MAX)
  const rest = libraries.slice(MAX)
  return (
    <div className="flex items-center gap-1">
      {shown.map(library => (
        <Badge
          key={library.id}
          variant="secondary"
          title={`${library.name} (${levelLabel(library.permissions)})`}
          className="max-w-40 truncate"
        >
          {library.permissions === "READ_WRITE" ? (
            <PencilIcon className="text-muted-foreground" />
          ) : (
            <EyeIcon className="text-muted-foreground" />
          )}
          {library.name}
        </Badge>
      ))}
      {rest.length > 0 && (
        <Badge
          variant="outline"
          title={rest.map(library => `${library.name} (${levelLabel(library.permissions)})`).join(", ")}
        >
          +{rest.length}
        </Badge>
      )}
    </div>
  )
}
