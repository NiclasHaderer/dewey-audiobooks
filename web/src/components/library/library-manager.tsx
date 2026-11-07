import { PlusIcon } from "lucide-react"
import { useMemo, useState } from "react"
import { FileScanner, Library, MetadataLanguage, MetadataRegion, NamedMetadataAgent } from "@dewey/client"
import { DataTable } from "@dewey/components/data-table/data-table"
import { DataTableToolbar } from "@dewey/components/data-table/data-table-toolbar"
import { libraryColumns } from "@dewey/components/library/library-columns"
import { LibraryDialog, LibraryFormValues } from "@dewey/components/library/library-dialog"
import { TitledDialog } from "@dewey/components/titled-dialog"
import { Button } from "@dewey/components/ui/button"
import { DialogClose, DialogDescription, DialogFooter } from "@dewey/components/ui/dialog"
import { FormContext, useForm } from "@dewey/hooks/use-form"
import { useCreateLibrary, useDeleteLibrary, useLibraries, useUpdateLibrary } from "@dewey/queries/library-queries"
import { changedFields } from "@dewey/utils/changed-fields"

export const LibraryManager = () => {
  const { data: libraries } = useLibraries()
  const [isOpen, setIsOpen] = useState(false)
  // The library as it was when its dialog opened. Changes are diffed against this copy and not the live list,
  // where a refetch during the edit would make fields the user never touched look modified.
  const [editing, setEditing] = useState<Library | undefined>(undefined)
  const [libraryToDelete, setLibraryToDelete] = useState<Library | undefined>(undefined)
  const createLibrary = useCreateLibrary()
  const updateLibrary = useUpdateLibrary()
  const deleteLibrary = useDeleteLibrary()

  const form: FormContext<LibraryFormValues> = useForm(
    {
      name: "",
      language: "" as MetadataLanguage | "",
      region: "" as MetadataRegion | "",
      preferEmbeddedMetadata: false as boolean,
      folders: [] as string[],
      metadataAgents: [] as NamedMetadataAgent[],
      fileScanners: [] as FileScanner[],
      // TODO add these to the library creation dialog
      combineMetadataAgentFields: false as boolean,
      combineFileScannerFields: false as boolean,
      icon: null as string | null,
    } satisfies LibraryFormValues,
    {
      validate: {
        name: (name: string) => name.length > 0 || "Name is required",
        language: (language: string) => language.length > 0 || "Language is required",
        region: (region: string) => region.length > 0 || "Region is required",
        folders: (folders: string[]) => folders.length > 0 || "At least one folder is required",
        fileScanners: (fileScanners: FileScanner[]) => {
          return fileScanners.length > 0 || "At least one file scanner is required"
        },
      },
    }
  )

  const onSubmit = ({ language, region, ...values }: LibraryFormValues) => {
    if (!language || !region) return
    const handlers = { onSuccess: () => setIsOpen(false) }
    const library = { ...values, language, region }
    if (editing) updateLibrary.mutate({ id: editing.id, library: changedFields(editing, library) }, handlers)
    else createLibrary.mutate(library, handlers)
  }

  const openEdit = (library: Library) => {
    setEditing(library)
    form.setAllFields(library)
    setIsOpen(true)
  }

  const onDelete = () => {
    if (!libraryToDelete) return
    deleteLibrary.mutate(libraryToDelete.id)
    setLibraryToDelete(undefined)
  }

  // eslint-disable-next-line react-hooks/exhaustive-deps
  const columns = useMemo(() => libraryColumns({ onEdit: openEdit, onDelete: setLibraryToDelete }), [])

  return (
    <>
      <div className="flex min-h-0 flex-1 flex-col">
        <DataTable
          columns={columns}
          data={libraries ?? []}
          onRowClick={openEdit}
          emptyState="No library yet"
          toolbar={table => (
            <DataTableToolbar table={table} searchColumnId="name" searchPlaceholder="Filter libraries...">
              <Button
                size="sm"
                className="h-8"
                aria-label="Create new Library"
                onPress={() => {
                  setEditing(undefined)
                  form.restoreInitial()
                  setIsOpen(true)
                }}
              >
                <PlusIcon />
                <span className="hidden sm:inline">Create new Library</span>
              </Button>
            </DataTableToolbar>
          )}
        />
      </div>
      <LibraryDialog
        mode={editing ? "edit" : "create"}
        onSubmit={onSubmit}
        isOpen={isOpen}
        setIsOpen={setIsOpen}
        form={form}
      />
      <TitledDialog
        isOpen={libraryToDelete !== undefined}
        onOpenChange={open => !open && setLibraryToDelete(undefined)}
        title={`Delete ${libraryToDelete?.name}?`}
        className="sm:max-w-sm"
      >
        <DialogDescription>
          This permanently deletes the library and every book, series and author in it. This cannot be undone.
        </DialogDescription>
        <DialogFooter className="mt-4">
          <DialogClose>Cancel</DialogClose>
          <Button variant="destructive" onPress={onDelete}>
            Delete library
          </Button>
        </DialogFooter>
      </TitledDialog>
    </>
  )
}
