import { fromFormDate, unique } from "@/utils/utils.ts"
import {
  BookMarkedIcon,
  CalendarIcon,
  LayersIcon,
  ListOrderedIcon,
  LanguagesIcon,
  TagsIcon,
  UserIcon,
  UsersIcon,
  SearchIcon,
} from "lucide-react"
import { FC, useMemo } from "react"
import { Book, BookUpdate, MetadataBook, UUID } from "@dewey/client"
import { GenericEdit } from "@dewey/components/generic/generic-edit.tsx"
import { AudibleIcon } from "@dewey/components/icons/audible"
import { CoverPicker } from "@dewey/components/input/cover-picker"
import { ManagedInput } from "@dewey/components/input/managed-input"
import { MultiCombobox } from "@dewey/components/input/multi-combobox"
import {
  useAllAuthors,
  useAllGenres,
  useAllSeries,
  useCreateAuthor,
  useCreateSeries,
  useUpdateBook,
} from "@dewey/queries/resource-queries"
import { FormContext } from "../../hooks/use-form"
import { HtmlEditor } from "../html-editor"
import { BookSearch } from "./book-search"

const mergeMetaIntoBook = ({ ...book }: BookUpdate, meta: MetadataBook): BookUpdate => {
  book.title = meta.title || book.title
  book.cover = meta.coverURL || book.cover
  book.description = meta.description || book.description
  book.narrators = meta.narrators.length > 0 ? meta.narrators : book.narrators
  book.language = meta.language || book.language
  book.releaseDate = meta.releaseDate || book.releaseDate
  book.isbn = meta.isbn || book.isbn
  book.asin = meta.asin || book.asin
  book.publisher = meta.publisher || book.publisher
  book.providerRating = meta.providerRating ?? book.providerRating
  book.provider = meta.id.provider
  book.providerID = meta.id.itemID
  return book
}

const bookToUpdate = (book: Book): BookUpdate => {
  return {
    authors: book.authors.map(a => a.id),
    cover: book.coverID,
    description: book.description,
    genres: book.genres,
    isbn: book.isbn,
    asin: book.asin,
    language: book.language,
    narrators: book.narrators,
    provider: book.provider,
    providerID: book.providerID,
    providerRating: book.providerRating,
    publisher: book.publisher,
    releaseDate: book.releaseDate,
    series: book.series.map(s => s.id),
    title: book.title,
  }
}

export const BookEdit: FC<{ book: Book; isOpen: boolean; onOpenChange: (open: boolean) => void }> = ({
  book,
  isOpen,
  onOpenChange,
}) => {
  const updateBook = useUpdateBook()

  return (
    <GenericEdit
      title="Edit Book"
      initial={bookToUpdate(book)}
      options={{
        toForm: {
          narrators: value => value?.join(", "),
        },
        fromForm: {
          releaseDate: fromFormDate,
          narrators: value =>
            value
              ?.split(",")
              .map(narrator => narrator.trim())
              .filter(Boolean) ?? [],
        },
        validate: { title: title => !!title || "Title is required" },
      }}
      isOpen={isOpen}
      onOpenChange={onOpenChange}
      onSubmit={async (changes, closeModal) => {
        await updateBook.mutateAsync({ libraryId: book.libraryId, id: book.id, data: changes })
        closeModal()
      }}
      information={form => <BookForm form={form} libraryId={book.libraryId} />}
      search={(form, onSelect) => (
        <BookSearch
          libraryId={book.libraryId}
          book={book.title}
          authors={book.authors.map(a => a.name)}
          onSelect={bookMeta => {
            form.setAllFields(mergeMetaIntoBook(form.fields, bookMeta))
            onSelect()
          }}
        />
      )}
    />
  )
}

const BookForm: FC<{ form: FormContext<BookUpdate>; libraryId: UUID }> = ({ form, libraryId }) => {
  const { data: authors } = useAllAuthors(libraryId)
  const { data: series } = useAllSeries(libraryId)
  const { data: genres } = useAllGenres(libraryId)
  const createAuthor = useCreateAuthor()
  const createSeries = useCreateSeries()

  const authorOptions = useMemo(() => (authors ?? []).map(author => ({ id: author.id, label: author.name })), [authors])
  const seriesOptions = useMemo(() => (series ?? []).map(entry => ({ id: entry.id, label: entry.title })), [series])
  const genreOptions = useMemo(
    () =>
      unique([...(genres ?? []).map(genre => genre.name), ...(form.fields.genres ?? [])]).map(genre => ({
        id: genre,
        label: genre,
      })),
    [genres, form.fields.genres]
  )

  return (
    <>
      <div className="flex flex-col md:flex-row">
        <CoverPicker alt="book" value={form.fields.cover} onChange={cover => form.setFields({ cover })} />
        <div className="min-w-0 grow">
          <ManagedInput name="title" labelClassName="w-28" label="Title" leftIcon={<SearchIcon />} />
          <MultiCombobox
            label="Authors"
            labelClassName="w-28"
            leftIcon={<UsersIcon />}
            options={authorOptions}
            value={form.fields.authors ?? []}
            onChange={authors => form.setFields({ authors })}
            onCreate={async name => (await createAuthor.mutateAsync({ libraryId, data: { name } })).id}
          />
          <MultiCombobox
            label="Series"
            labelClassName="w-28"
            leftIcon={<LayersIcon />}
            options={seriesOptions}
            value={form.fields.series ?? []}
            onChange={series => form.setFields({ series })}
            onCreate={async title => (await createSeries.mutateAsync({ libraryId, data: { title } })).id}
          />
          <ManagedInput name="narrators" labelClassName="w-28" label="Narrators" leftIcon={<UserIcon />} />
          <MultiCombobox
            label="Genres"
            labelClassName="w-28"
            leftIcon={<TagsIcon />}
            options={genreOptions}
            value={form.fields.genres ?? []}
            onChange={genres => form.setFields({ genres })}
            onCreate={genre => Promise.resolve(genre)}
          />
          <ManagedInput name="language" labelClassName="w-28" label="Language" leftIcon={<LanguagesIcon />} />
          <ManagedInput
            name="releaseDate"
            labelClassName="w-28"
            type="date"
            label="Release Date"
            leftIcon={<CalendarIcon />}
          />
          <ManagedInput name="isbn" labelClassName="w-28" label="ISBN" leftIcon={<ListOrderedIcon />} />
          <ManagedInput name="asin" labelClassName="w-28" label="ASIN" leftIcon={<AudibleIcon />} />
          <ManagedInput name="publisher" labelClassName="w-28" label="Publisher" leftIcon={<BookMarkedIcon />} />
        </div>
      </div>
      <HtmlEditor
        placeholder="Description"
        value={form.fields.description}
        onChange={description => form.setFields({ description })}
      />
    </>
  )
}
