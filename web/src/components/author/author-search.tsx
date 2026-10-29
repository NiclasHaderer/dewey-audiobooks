import { SearchIcon } from "lucide-react"
import { FC, useState } from "react"
import { MetadataAuthor, UUID } from "@dewey/client"
import { MetadataResults } from "@dewey/components/generic/metadata-results"
import { LabeledInput } from "@dewey/components/input/labeled-input"
import { LoadingCards } from "@dewey/components/loading-card"
import { ResponsiveImage } from "@dewey/components/responsive-image"
import { Button } from "@dewey/components/ui/button"
import { useAuthorMetadataSearch } from "@dewey/queries/metadata-queries"

export const AuthorSearch: FC<{
  authorSearch?: string | null | undefined
  libraryId: UUID
  onSelect: (result: MetadataAuthor) => void
}> = ({ onSelect, authorSearch, libraryId }) => {
  const [author, setAuthor] = useState(authorSearch)
  const [submitted, setSubmitted] = useState<{ query: string } | null>(null)

  const { data: result, isFetching: loading } = useAuthorMetadataSearch(libraryId, submitted)

  const search = () => {
    if (!author) return
    setSubmitted({ query: author })
  }

  return (
    <>
      <div className="mb-4 flex items-center gap-2">
        <div className="grow">
          <LabeledInput
            label="Author"
            onEnter={search}
            onValue={setAuthor}
            defaultValue={author}
            preventSubmit
            hideError
          />
        </div>
        <Button variant="secondary" size="icon" aria-label="Search" onPress={search}>
          <SearchIcon />
        </Button>
      </div>
      <div className="h-0 min-h-48 grow overflow-y-auto">
        {result ? (
          <MetadataResults
            results={result}
            onSelect={onSelect}
            title={author => author.name}
            description={author => author.biography}
            image={author =>
              author.imageURL ? (
                <ResponsiveImage
                  className="h-28 w-28 min-w-28 rounded-full bg-cover"
                  alt={author.name ?? "Cover"}
                  src={author.imageURL}
                />
              ) : null
            }
          />
        ) : null}
        {loading ? <LoadingCards amount={10} /> : null}
      </div>
    </>
  )
}
