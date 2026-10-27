import { DetailList } from "@/components/detail/detail-list.tsx"
import { parseDate, pluralize } from "@/utils/utils.ts"
import { cn } from "cn"
import {
  BarcodeIcon,
  BuildingIcon,
  CalendarIcon,
  CheckIcon,
  CircleCheckIcon,
  CirclePlayIcon,
  ClockIcon,
  ImageOffIcon,
  LanguagesIcon,
  LayersIcon,
  LucideIcon,
  MicIcon,
  StarIcon,
  TagsIcon,
  UserIcon,
} from "lucide-react"
import { FC, ReactNode } from "react"
import { Book, UUID } from "@dewey/client"
import { BookActionsMenu, useBookActions } from "@dewey/components/book/book-actions"
import { DownloadBadge } from "@dewey/components/book/book-download"
import { MobileDetailHeader } from "@dewey/components/detail/detail-layout"
import { DetailSkeleton } from "@dewey/components/detail/detail-skeleton"
import { PartialSection } from "@dewey/components/detail/partial-section"
import { HtmlViewer } from "@dewey/components/html-editor"
import { AudibleIcon } from "@dewey/components/icons/audible"
import { Link } from "@dewey/components/link.tsx"
import { QueryError } from "@dewey/components/status-page.tsx"
import { Button } from "@dewey/components/ui/button"
import { useCanPlay, useCoverSrc } from "@dewey/downloads"
import { useBreakpoint } from "@dewey/hooks/use-media-query"
import { isDetailedBook } from "@dewey/models/model-typeguards"
import { playback, useBookProgress, usePlayback } from "@dewey/playback"
import { locateTrack } from "@dewey/playback/track-position"
import { useBook } from "@dewey/queries/resource-queries"
import { TrackList, TrackListSkeleton } from "../track/track-list"
import { toReadableTime, toRuntime } from "../track/track-time-format"

const RATING_MAX = 5

const Rating: FC<{ value: number }> = ({ value }) => {
  const stars = Array.from({ length: RATING_MAX }, (_, index) => index)
  const filled = (Math.min(Math.max(value, 0), RATING_MAX) / RATING_MAX) * 100

  return (
    <div className="flex items-center gap-2">
      <div aria-hidden className="relative">
        <div className="text-muted-foreground/35 flex">
          {stars.map(index => (
            <StarIcon key={index} className="size-4" />
          ))}
        </div>
        <div className="absolute inset-y-0 left-0 flex overflow-hidden text-amber-400" style={{ width: `${filled}%` }}>
          {stars.map(index => (
            <StarIcon key={index} className="size-4 shrink-0 fill-current" />
          ))}
        </div>
      </div>
      <span className="text-sm tabular-nums">{value.toFixed(1)}</span>
    </div>
  )
}

const BookProgress: FC<{ book: Book; className?: string }> = ({ book, className }) => {
  const { inProgress, remainingMs } = useBookProgress(book)

  // Height is reserved even without progress so starting playback does not shift the layout.
  return (
    <div className={cn("text-muted-foreground min-h-[1lh] text-xs whitespace-nowrap", className)}>
      {inProgress ? `${toReadableTime(remainingMs / 1000)} left` : null}
    </div>
  )
}

const MetaRow: FC<{ icon: LucideIcon; children: ReactNode }> = ({ icon: Icon, children }) => (
  <div className="text-muted-foreground flex min-w-0 items-baseline gap-2 text-sm">
    <Icon aria-hidden className="size-4 shrink-0 translate-y-0.5" />
    <div className="min-w-0 grow">{children}</div>
  </div>
)

const MetaItem: FC<{ icon: LucideIcon; children: ReactNode }> = ({ icon: Icon, children }) => (
  <span className="text-muted-foreground flex items-center gap-1.5 text-sm whitespace-nowrap">
    <Icon aria-hidden className="size-4 shrink-0" />
    {children}
  </span>
)

interface CoverProps {
  book: Book
  className: string
}

// Isolated so live position updates only re-render the overlay, not the whole page.
const CoverIndicators: FC<{ book: Book }> = ({ book }) => {
  const { finished, inProgress, fraction } = useBookProgress(book)

  return (
    <>
      <DownloadBadge bookId={book.id} />
      {finished ? (
        <div
          aria-label="Played"
          className="absolute top-0 right-0 flex size-11 items-center justify-center rounded-bl-lg bg-black/55 text-white/90 backdrop-blur-sm"
        >
          <CheckIcon className="size-5" strokeWidth={2.5} />
        </div>
      ) : null}
      {inProgress ? (
        <div className="pointer-events-none absolute inset-x-0 bottom-0 h-1 bg-black/60 backdrop-blur-sm">
          <div
            className="bg-primary h-full transition-[width] duration-300 ease-linear"
            style={{ width: `${fraction * 100}%` }}
          />
        </div>
      ) : null}
    </>
  )
}

const Cover: FC<CoverProps> = ({ book, className }) => {
  const cover = useCoverSrc()
  return (
    <div className={cn("relative overflow-hidden rounded-lg", className)}>
      {book.coverID ? (
        <img
          className="border-border w-full rounded-lg border object-cover shadow-lg shadow-black/25"
          alt={book.title}
          src={cover(book.coverID)}
        />
      ) : (
        <div className="border-border text-muted-foreground/60 flex aspect-square w-full items-center justify-center rounded-lg border">
          <ImageOffIcon className="size-2/5" />
        </div>
      )}
      <CoverIndicators book={book} />
    </div>
  )
}

interface MetaProps {
  book: Book
  libraryId: UUID
  runtime: number
}

const Meta: FC<MetaProps> = ({ book, libraryId, runtime }) => (
  <>
    {book.authors.length > 0 ? (
      <MetaRow icon={UserIcon}>
        <DetailList
          items={book.authors.map(author => ({
            key: author.id,
            label: author.name,
            href: `/libraries/${libraryId}/authors/${author.id}`,
          }))}
        />
      </MetaRow>
    ) : null}

    {book.series.length > 0 ? (
      <MetaRow icon={LayersIcon}>
        <DetailList
          items={book.series.map(series => ({
            key: series.id,
            label: series.title,
            href: `/libraries/${libraryId}/series/${series.id}`,
          }))}
        />
      </MetaRow>
    ) : null}

    {book.narrators.length > 0 ? (
      <MetaRow icon={MicIcon}>
        <DetailList
          items={book.narrators.map(narrator => ({
            key: narrator,
            label: narrator,
            href: `/libraries/${libraryId}/narrators/${encodeURIComponent(narrator)}`,
          }))}
        />
      </MetaRow>
    ) : null}

    <div className="flex flex-wrap items-center gap-x-5 gap-y-1.5">
      {runtime ? <MetaItem icon={ClockIcon}>{toRuntime(runtime)}</MetaItem> : null}
      {book.releaseDate ? <MetaItem icon={CalendarIcon}>{parseDate(book.releaseDate).getFullYear()}</MetaItem> : null}
      {book.publisher ? <MetaItem icon={BuildingIcon}>{book.publisher}</MetaItem> : null}
      {book.language ? (
        <MetaItem icon={LanguagesIcon}>
          <span className="capitalize">{book.language}</span>
        </MetaItem>
      ) : null}
      {book.isbn ? (
        <MetaItem icon={BarcodeIcon}>
          <span className="tabular-nums">{book.isbn}</span>
        </MetaItem>
      ) : null}
      {book.asin ? (
        <MetaItem icon={AudibleIcon}>
          <span className="tabular-nums">{book.asin}</span>
        </MetaItem>
      ) : null}
    </div>
  </>
)

const GenreLinks: FC<{ book: Book; libraryId: UUID }> = ({ book, libraryId }) => (
  <>
    {book.genres.map(genre => (
      <Link
        key={genre}
        href={`/libraries/${libraryId}/genres/${encodeURIComponent(genre)}`}
        className="text-muted-foreground min-w-0 truncate text-sm hover:underline"
      >
        {genre}
      </Link>
    ))}
  </>
)

interface HeaderProps extends MetaProps {
  tracks: number
  actions: ReactNode
}

const DesktopHeader: FC<HeaderProps> = ({ book, libraryId, runtime, actions }) => (
  <div className="grid grid-cols-[auto_minmax(0,1fr)] items-start gap-x-8 gap-y-5">
    <div className="flex w-56 shrink-0 flex-col gap-2 self-center lg:w-64">
      <Cover book={book} className="w-full" />
      <BookProgress book={book} className="text-center" />
    </div>

    <div className="flex min-w-0 flex-col gap-2">
      <h1 className="min-w-0 text-4xl font-bold tracking-tight text-balance">{book.title}</h1>

      {book.providerRating || book.genres.length > 0 ? (
        <div className="flex min-w-0 items-center gap-x-5">
          {book.providerRating ? <Rating value={book.providerRating} /> : null}
          <GenreLinks book={book} libraryId={libraryId} />
        </div>
      ) : null}

      <div className="border-border/60 mt-1 flex flex-col gap-1.5 border-t pt-3">
        <Meta book={book} libraryId={libraryId} runtime={runtime} />
      </div>

      <div className="flex items-center gap-1.5 pt-4">{actions}</div>
    </div>

    {book.description ? (
      <div className="col-span-2">
        <HtmlViewer content={book.description} collapsedLines={3} />
      </div>
    ) : null}
  </div>
)

const MobileHeader: FC<HeaderProps> = ({ book, libraryId, runtime, tracks, actions }) => {
  const cover = useCoverSrc()
  const tracksLabel = tracks > 0 ? pluralize(tracks, "Track") : undefined

  return (
    <MobileDetailHeader
      title={book.title}
      backdrop={cover(book.coverID)}
      art={<Cover book={book} className="w-full" />}
      actions={actions}
      revealTop={
        book.publisher ? <div className="text-muted-foreground pb-1.5 text-center text-sm">{book.publisher}</div> : null
      }
      stats={
        <>
          {book.providerRating ? <Rating value={book.providerRating} /> : null}
          {runtime ? <span>{toRuntime(runtime)}</span> : null}
          {tracksLabel ? <span>{tracksLabel}</span> : null}
          <BookProgress book={book} />
        </>
      }
      revealBottom={
        <div className="flex flex-col gap-4 pt-4 text-left">
          {book.description ? <HtmlViewer content={book.description} collapsedLines={3} /> : null}

          <div className="border-border/60 flex flex-col gap-2 rounded-lg border p-4">
            <Meta book={book} libraryId={libraryId} runtime={0} />
            {book.genres.length > 0 ? (
              <MetaRow icon={TagsIcon}>
                <div className="flex min-w-0 gap-x-2">
                  <GenreLinks book={book} libraryId={libraryId} />
                </div>
              </MetaRow>
            ) : null}
          </div>
        </div>
      }
    />
  )
}

const BookPageActions: FC<{ book: Book }> = ({ book }) => {
  const { play, finished } = useBookActions(book)

  return (
    <>
      <Button onPress={play.run} isDisabled={play.isDisabled} className="h-11 grow px-5 md:h-10 md:grow-0">
        <CirclePlayIcon className="mr-2" /> {play.label}
      </Button>
      <Button
        variant="ghost"
        size="icon-lg"
        aria-label={finished.label}
        isDisabled={finished.isDisabled}
        onPress={finished.run}
        className={cn(
          "aspect-square h-11 w-auto! shrink-0 grow-0! rounded-full px-0! sm:h-10",
          finished.isFinished ? "text-primary" : "text-muted-foreground hover:text-foreground"
        )}
      >
        {finished.isFinished ? (
          <CircleCheckIcon className="[&>path]:stroke-background size-5 fill-current" />
        ) : (
          <CircleCheckIcon className="size-5" />
        )}
      </Button>
      <BookActionsMenu book={book} />
    </>
  )
}

export const BookDetails: FC<{ bookId: UUID; libraryId: UUID }> = ({ bookId, libraryId }) => {
  const isCurrentBook = usePlayback(s => s.book?.id === bookId)
  const currentTrack = usePlayback(s => s.track)
  const currentTrackIndex = usePlayback(s => s.trackIndex)
  const isPlaying = usePlayback(s => !s.paused)
  const isDesktop = useBreakpoint("md")
  const { data: book, error, refetch, isFetching, isLoadingError, isPending } = useBook(libraryId, bookId)
  const canPlay = useCanPlay(bookId)

  if (isLoadingError) return <QueryError error={error} onRetry={refetch} />
  if (isPending)
    return (
      <DetailSkeleton>
        <TrackListSkeleton />
      </DetailSkeleton>
    )

  const currentTrackId = isCurrentBook ? currentTrack?.id : undefined

  const tracks = isDetailedBook(book) ? book.tracks : []
  const totalDuration = tracks.reduce((sum, track) => sum + track.durationMs / 1000, 0)

  const startPlayback = (index: number) => {
    if (isDetailedBook(book)) void playback.play(book, { track: index })
  }

  const isFinished = book.status === "FINISHED"
  const playedCount = isCurrentBook
    ? currentTrackIndex
    : isFinished
      ? tracks.length
      : locateTrack(tracks, book.positionMs).index

  const actions = <BookPageActions book={book} />

  const Header = isDesktop ? DesktopHeader : MobileHeader

  return (
    <div className="mx-auto w-full max-w-5xl min-w-0">
      <Header book={book} libraryId={libraryId} runtime={totalDuration} tracks={tracks.length} actions={actions} />

      {tracks.length > 0 ? (
        <TrackList
          className="pt-8 md:pt-10"
          label={pluralize(tracks.length, "Track")}
          trailing={totalDuration ? toRuntime(totalDuration) : undefined}
          tracks={tracks}
          activeId={currentTrackId}
          playedCount={playedCount}
          playing={isPlaying}
          disabled={!canPlay}
          onStart={startPlayback}
          onToggle={playback.setPlaying}
        />
      ) : (
        <PartialSection
          className="pt-8 md:pt-10"
          title="Tracks"
          error={error}
          loading={isFetching}
          skeleton={<TrackListSkeleton />}
        />
      )}
    </div>
  )
}
export default BookDetails
