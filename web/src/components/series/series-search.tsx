import { SearchIcon } from "lucide-react"
import { FC, useState } from "react"
import { MetadataSeries, UUID } from "@dewey/client"
import { MetadataResults } from "@dewey/components/generic/metadata-results"
import { LabeledInput } from "@dewey/components/input/labeled-input"
import { LoadingCards } from "@dewey/components/loading-card"
import { Button } from "@dewey/components/ui/button"
import { useSeriesMetadataSearch } from "@dewey/queries/metadata-queries"

export const SeriesSearch: FC<{
  series?: string | null | undefined
  authors?: string[] | null | undefined
  libraryId: UUID
  onSelect: (result: MetadataSeries) => void
}> = ({ series: _series, authors: _authors, libraryId, onSelect }) => {
  const [authors, setAuthors] = useState(_authors?.join(", "))
  const [series, setSeries] = useState(_series)
  const [submitted, setSubmitted] = useState<{ query: string; authorName: string | undefined } | null>(null)

  const { data: result, isFetching: loading } = useSeriesMetadataSearch(libraryId, submitted)

  const search = () => {
    if (!series) return
    setSubmitted({ query: series, authorName: authors })
  }

  return (
    <>
      <div className="mb-4 flex items-center gap-2">
        <div className="grow">
          <LabeledInput
            labelClassName="w-28"
            label="Series"
            onValue={setSeries}
            defaultValue={series}
            onEnter={search}
            preventSubmit
            hideError
          />
        </div>
        <div className="grow">
          <LabeledInput
            label="Author"
            onValue={setAuthors}
            defaultValue={authors}
            onEnter={search}
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
            title={series => series.title}
            description={series => series.description}
          />
        ) : null}
        {loading ? <LoadingCards amount={10} /> : null}
      </div>
    </>
  )
}
