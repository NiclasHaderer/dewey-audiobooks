import { LibraryIcon } from "lucide-react"
import { FC } from "react"
import { LibraryPermissionLevel, UUID } from "@dewey/client"
import { OptionSelect } from "@dewey/components/input/option-select"

export type LibraryGrant = { id: UUID; permissions: LibraryPermissionLevel }

type Level = LibraryPermissionLevel | "NONE"

const LEVEL_LABELS: Record<Level, string> = { NONE: "No access", READONLY: "Read only", READ_WRITE: "Read & write" }

export const LibraryAccess: FC<{
  libraries: { id: UUID; name: string; max?: LibraryPermissionLevel }[]
  value: LibraryGrant[]
  onChange: (grants: LibraryGrant[]) => void
}> = ({ libraries, value, onChange }) => {
  const set = (id: UUID, level: Level) => {
    const others = value.filter(grant => grant.id !== id)
    onChange(level === "NONE" ? others : [...others, { id, permissions: level }])
  }

  return (
    <div className="flex flex-col rounded-lg border px-3 py-1">
      {libraries.length === 0 && <p className="text-muted-foreground py-3 text-sm">No libraries to choose from.</p>}
      {libraries.map(library => {
        const allowed: Level[] = library.max === "READONLY" ? ["NONE", "READONLY"] : ["NONE", "READONLY", "READ_WRITE"]
        const current: Level = value.find(grant => grant.id === library.id)?.permissions ?? "NONE"
        return (
          <div key={library.id} className="flex items-center justify-between gap-3 py-2">
            <span className="flex min-w-0 items-center gap-2 text-sm">
              <LibraryIcon className="text-muted-foreground size-4 shrink-0" />
              <span className="truncate">{library.name}</span>
            </span>
            <OptionSelect
              title={`Access to ${library.name}`}
              options={allowed.map(level => ({ value: level, label: LEVEL_LABELS[level] }))}
              value={current}
              onChange={level => set(library.id, level)}
              placeholderButtonClassName="w-36"
              hDir="right"
            />
          </div>
        )
      })}
    </div>
  )
}
